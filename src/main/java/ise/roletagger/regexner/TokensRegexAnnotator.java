package ise.roletagger.regexner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.XMLOutputter;
import ise.roletagger.datasetgenerator.DatasetGeneratorWithCategoryTreesV2;
import ise.roletagger.model.Document;
import ise.roletagger.model.Global;
import ise.roletagger.util.Config;
import ise.roletagger.util.DictionaryRegexPatterns;
import ise.roletagger.util.FileUtil;
import ise.roletagger.util.MyStanfordCoreNLP;
import ise.roletagger.util.NER_TAG;
import ise.roletagger.util.NerTag;

public class TokensRegexAnnotator {
	private static final Logger LOG=Logger.getLogger(TokensRegexAnnotator.class.getName());
	private static StanfordCoreNLP pipeline;
	private static final String WIKI_FILES_FOLDER = Config.getString("WIKI_FILES_FOLDER", "");
	private static ExecutorService executor;
	private static final int NUMBER_OF_THREADS = Config.getInt("NUMBER_OF_THREADS", 0);
	private static final String WHERE_TO_WRITE_DATASET = Config.getString("WHERE_TO_WRITE_DATASET", "");
	private static final Set<String> datasetCandicates = new CopyOnWriteArraySet<String>();
	/**
	 * Counts how many sentence we check for generating data here in this class
	 */
	private static final AtomicLong NUMBER_OF_SENTENCES= new AtomicLong(0);
	private static final String rules = "edu/stanford/nlp/ling/tokensregex/demo/rules/roles.rules.txt";
	private static final Properties properties = new Properties();
	static {
		properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,tokensregexdemo");
		properties.setProperty("customAnnotatorClass.tokensregexdemo",
				"edu.stanford.nlp.pipeline.TokensRegexAnnotator");
		properties.setProperty("tokensregexdemo.rules", rules);
		pipeline = new StanfordCoreNLP(properties);
	}

	public static void main(String[] args) {
		final Thread t = new Thread(run());
		t.setDaemon(false);
		t.start();
	}

	public static Runnable run() {
		return () -> {
			try {
				executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
				final File[] listOfFolders = new File(WIKI_FILES_FOLDER).listFiles();
				Arrays.sort(listOfFolders);
				for (int i = 0; i < listOfFolders.length; i++) {
					final String subFolder = listOfFolders[i].getName();
					final File[] listOfFiles = new File(WIKI_FILES_FOLDER + File.separator + subFolder + File.separator)
							.listFiles();
					Arrays.sort(listOfFiles);
					for (int j = 0; j < listOfFiles.length; j++) {
						final String file = listOfFiles[j].getName();
						try{
							executor.submit(getTask(WIKI_FILES_FOLDER + File.separator + subFolder + File.separator+ File.separator + file));
						}catch(Exception e) {
							//ignore
						}
					}
				}
				executor.shutdown();
				executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);				
				FileUtil.writeDataToFile(new ArrayList<>(datasetCandicates), WHERE_TO_WRITE_DATASET+"regexDataset.txt", false);				
			} catch (final Exception exception) {
				exception.printStackTrace();
			}
		};
	}

	private static Runnable getTask(String filePath) {
		return () -> {
			final List<Document> documents = DatasetGeneratorWithCategoryTreesV2.getDocuments(filePath);
			for (final Document document : documents) {
				NUMBER_OF_SENTENCES.addAndGet(document.getSentences().size());
				for (String line : document.getSentences()) {					
					check(line);
				}
			}
			System.err.println("File " + filePath + " is done. Size of set="+datasetCandicates.size()+". So far "+(NUMBER_OF_SENTENCES)+" sentences checked");
		};
	}

	public static void check(String line) {
		try {
			Annotation annotation;
			final String sentenceWithoutHtmlTag = line.replaceAll("<[^>]*>", "");
			annotation = new Annotation(sentenceWithoutHtmlTag);
			pipeline.annotate(annotation);

			final nu.xom.Document doc = XMLOutputter.annotationToDoc(annotation, pipeline);
			final String result = MyStanfordCoreNLP.runRoleTaggerString(sentenceWithoutHtmlTag, doc.toXML());
			if (result.contains("ROLE")) {
				final Map<Integer, NerTag> nerXmlParser = MyStanfordCoreNLP.nerXmlParser(doc.toXML());
				final String normalForm = toNormalForm(result, nerXmlParser);
				LOG.info(normalForm);
				datasetCandicates.add(normalForm);
			}
		} catch (Exception e) {
			//ignore 
		}

	}

	/**
	 * This function convert the result to a sentence which contains <RP> and
	 * <HR>
	 * It is needed to be able to be parsed in the dataset
	 * 
	 * @param afterHavingRoleTage
	 *            a sentence which contains aggrigated <ROLE>
	 * @param nerXmlParser
	 *            xml of aggregated NRE result
	 * @return
	 */
	private static String toNormalForm(String afterHavingRoleTage, Map<Integer, NerTag> nerXmlParser) {

		final List<NerTag> values = new ArrayList<NerTag>(nerXmlParser.values().stream()
				.filter(p -> p.getNerTag().equals(NER_TAG.ROLE)).collect(Collectors.toList()));
		String result = new String(afterHavingRoleTage);
		for (NerTag nerTag : values) {
			String text = nerTag.getWord();
			StringBuilder rolePhrase = new StringBuilder(text);
			for (Pattern pp : DictionaryRegexPatterns.getHeadRolePatterns().get(nerTag.getNomalizedNER())) {
				Matcher matcher = pp.matcher(rolePhrase);
				if (matcher.find()) {
					String foundText2 = matcher.group();
					int end2 = matcher.end();
					int start2 = matcher.start();
					rolePhrase = rolePhrase.replace(start2, end2,
							Global.getHeadRoleStartTag() + foundText2 + Global.getHeadRoleEndTag());
					break;
				}
			}
			result = result.replaceFirst("<ROLE>", Global.getRolePhraseStartTag(nerTag.getNomalizedNER().name())
					+ rolePhrase + Global.getRolePhraseEndTag());
		}
		return result;
	}
}