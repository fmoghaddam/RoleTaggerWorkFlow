package ise.roletagger.evaluationtokenbased;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import ise.roletagger.datasetconvertor.TagParser;
import ise.roletagger.datasetconvertor.TaggerType;
import ise.roletagger.datasetgenerator.RoleListProviderFileBased;
import ise.roletagger.model.Category;
import ise.roletagger.model.DataSourceType;
import ise.roletagger.model.Global;
import ise.roletagger.model.Position;
import ise.roletagger.model.RoleListProvider;
import ise.roletagger.model.TagPosition;
import ise.roletagger.model.TagPositions;
import ise.roletagger.model.Tuple;
import ise.roletagger.util.Config;

/**
 * This class convert ground truth files to a structure which CRFSuite CRF can read
 * for testing
 * 
 * Ground truth should be in the format of
 * 
 * <RP Category=""><HR></HR></RP>
 * 
 * @author fbm
 *
 */
public class BaseLineAnchorTextEvaluation {

	private static final String GROUND_TRUTH_FOLDER = Config.getString("GROUND_TRUTH_FOLDER", "");
	/**
	 * How to convert ground truth to feature set?
	 * AND
	 * How to calculate accuracy, precision, ...
	 * Consider which part as TP,FP,TN,FN 
	 */
	private final TaggerType GroundTruth2FeatureType;
	
	private final TaggerType DictionayType;

	static float truePositive = 0;
	static float falsePositive = 0;
	static float falseNegative = 0;
	static float trueNegative = 0;

	/**
	 * Reads already calculated dictionary of roles from folder "dictionary/manually
	 * cleaned"
	 */
	private final RoleListProvider dictionaries = new RoleListProviderFileBased(false);
	
	/**
	 * contains all the role from dictionary.Every role goes to a new regex pattern
	 * to be able to select the longest match
	 */
	private List<Pattern> rolePatterns = new ArrayList<>();

	public BaseLineAnchorTextEvaluation(TaggerType howToTagGroundTruth,TaggerType whichModelUse) {
		GroundTruth2FeatureType = howToTagGroundTruth;
		DictionayType = whichModelUse;
	}
	
	
	public static void main(String[] args) throws IOException {
		final Thread t = new Thread(new BaseLineAnchorTextEvaluation(TaggerType.ONLY_HEAD_ROLE,TaggerType.ONLY_HEAD_ROLE).run());
		t.setDaemon(false);
		t.start();
	}

	public Runnable run() {
		return () -> {
			
			getRoleRegex();
			
			try {
				final File[] listOfFiles = new File(GROUND_TRUTH_FOLDER).listFiles();				
				for (int i = 0; i < listOfFiles.length; i++) {
					final String fileName = listOfFiles[i].getName();
					final List<String> positiveLines = Files.readAllLines(Paths.get(GROUND_TRUTH_FOLDER+fileName),StandardCharsets.UTF_8);
					generateFullDataset(positiveLines);
				}
				final float precision = truePositive/(truePositive+falsePositive);
				System.err.println("----------------BaseLine-"+GroundTruth2FeatureType.name()+"-"+DictionayType.name()+"--------------------");
				System.err.println("Precision= " +precision);
				final float recall = truePositive/(truePositive+falseNegative);
				System.err.println("Recall= " +recall);
				System.err.println("F1= " +(2*precision*recall)/(precision+recall));
				System.err.println("Accuracy= " +(truePositive+trueNegative)/(falsePositive+falseNegative+trueNegative+truePositive));
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
	}


	public void getRoleRegex() {
		dictionaries.loadRoles(DataSourceType.WIKIPEDIA_LIST_OF_TILTES);
		dictionaries.loadRoles(DataSourceType.WIKIDATA_LIST_OF_PRESON);
		switch (DictionayType) {
		case ONLY_HEAD_ROLE:
			for (final Entry<Category, LinkedHashSet<String>> roleEntity : dictionaries.getHeadRoleMap().entrySet()) {
				for (String role : roleEntity.getValue()) {
					rolePatterns.add(Pattern.compile("(?im)" + "\\b" + Pattern.quote(role) + "\\b"));
				}
			}
			break;
//		case ONLY_ROLE_PHRASE:
//			for (final Entry<String, Set<Category>> roleEntity : dictionaries.getData().entrySet()) {
//				final String originalrole = roleEntity.getKey();
//				final String role = originalrole;
//				if (role.charAt(0) == '<' && role.charAt(role.length() - 1) == '>') {
//					continue;
//				}
//				rolePatterns.add(Pattern.compile("(?im)\\b" + Pattern.quote(role) + "\\b"));
//			}
//			break;
		default:
			break;
		}
	}

	/**
	 * Run in parallel Convert data to the features set which can be used by
	 * CRFSuite First chunk data and pass each chunk to each thread Can be used for
	 * converting train or test data by {@code isTrain}
	 * 
	 * @param postiveData
	 * @param negativeData
	 * @param isTrain
	 */
	private void generateFullDataset(List<String> data) {
		try {
			for(String line:data) {
				final String taggedLine = new String(line);
				if(taggedLine.isEmpty()) {
					continue;
				}
				final Map<String, Map<String, Category>> parseData = TagParser.parse(taggedLine);
				final String noTaggedLine = parseData.get("noTag").keySet().iterator().next();
				final Map<Integer, Map<String, String>> result = SentenceToFeature.convertTaggedSentenceToFeatures(taggedLine, GroundTruth2FeatureType, line.contains(Global.getHeadRoleStartTag()));
				evaluate(result,noTaggedLine);
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private void evaluate(Map<Integer, Map<String, String>> result, String noTaggedLine) {
		final List<Tuple> tuples = runBaseLineDictionary(noTaggedLine);
		if(tuples.size()!=result.size()) {
			throw new IllegalArgumentException("Size of the tuples and size of the result are not similar");
		}

		for(int i=0;i<tuples.size();i++) {
			final Tuple tuple = tuples.get(i);
			final Map<String, String> map = result.get(i);
			final String realTag = map.get("TAG");
			final String predictaedTag = tuple.b;

			if(realTag.equalsIgnoreCase(predictaedTag) && realTag.equalsIgnoreCase("O")) {
				trueNegative++;
			}else if(realTag.equalsIgnoreCase(predictaedTag) && !realTag.equalsIgnoreCase("O")) {
				truePositive++;
			}
			else {
				if(realTag.equalsIgnoreCase("O")) {
					falsePositive++;
				}else {
					falseNegative++;
				}
			}
		}
	}

	public List<Tuple> runBaseLineDictionary(String noTaggedLine) {
		final List<Tuple> result = new ArrayList<>();
		final TagPositions tagPositions = new TagPositions();
		for (Pattern p : rolePatterns) {
			final Matcher matcher = p.matcher(noTaggedLine);
			while (matcher.find()) {
				String foundText = matcher.group();
				int start = matcher.start();
				int end = matcher.end();
				final TagPosition tp = new TagPosition(foundText, start, end);
				if (tagPositions.alreadyExist(tp)) {
					continue;
				}
				tagPositions.add(tp);
			}
		}

		final TokenizerFactory<Word> tf = PTBTokenizer.factory();		
		List<Word> tokens_words = null;
		try {
			tokens_words = tf.getTokenizer(new StringReader(noTaggedLine)).tokenize();
			for(Word w:tokens_words) {
				if(isDetected(tagPositions,w)) {
					result.add(new Tuple(w.value(), "ROLE"));
				}else {
					result.add(new Tuple(w.value(), "O"));
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	private static boolean isDetected(TagPositions tagPositions, Word w) {
		int start = w.beginPosition();
		int end = w.endPosition();
		
		for(int i=0;i<tagPositions.getPositions().size();i++) {
			if(tagPositions.getPositions().get(i).contains(new Position(start, end))) {
				return true;
			}
		}
		return false;
	}
}
