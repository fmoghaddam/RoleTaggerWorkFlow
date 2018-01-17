package ise.roletagger.evaluationtokenbased;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import ise.roletagger.datasetconvertor.TagParser;
import ise.roletagger.datasetconvertor.TaggerType;
import ise.roletagger.model.Category;
import ise.roletagger.model.Global;
import ise.roletagger.model.Tuple;
import ise.roletagger.util.Config;
import ise.roletagger.util.CustomNERTagger;
import ise.roletagger.util.MyStanfordCoreNLP;

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
@Deprecated
public class EvaluationStanfordCrf {

	private static final String GROUND_TRUTH_FOLDER = Config.getString("GROUND_TRUTH_FOLDER", "");
	/**
	 * How to convert ground truth to feature set?
	 * AND
	 * How to calculate accuracy, precision, ...
	 * Consider which part as TP,FP,TN,FN 
	 * 
	 * Keep in your mid that Ground truth does not have anchortext
	 */
	private static final TaggerType TP = TaggerType.ONLY_HEAD_ROLE;
	
	static float truePositive = 0;
	static float falsePositive = 0;
	static float falseNegative = 0;
	static float trueNegative = 0;
	
	/**
	 * With seed
	 */
	public static void main(String[] args) throws IOException {

		final Thread t = new Thread(run());
		t.setDaemon(false);
		t.start();
	}

	public static Runnable run() {
		return () -> {
			try {
				final File[] listOfFiles = new File(GROUND_TRUTH_FOLDER).listFiles();				
				for (int i = 0; i < listOfFiles.length; i++) {
					final String fileName = listOfFiles[i].getName();
					final List<String> positiveLines = Files.readAllLines(Paths.get(GROUND_TRUTH_FOLDER+fileName),StandardCharsets.UTF_8);
					generateFullDataset(positiveLines);
				}
				final float precision = truePositive/(truePositive+falsePositive);
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

	/**
	 * Run in parallel Convert data to the features set which can be used by
	 * CRFSuite First chunk data and pass each chunk to each thread Can be used for
	 * converting train or test data by {@code isTrain}
	 * 
	 * @param postiveData
	 * @param negativeData
	 * @param isTrain
	 */
	private static void generateFullDataset(List<String> data) {
		try {
			for(String line:data) {
				final String taggedLine = new String(line);
				if(taggedLine.isEmpty()) {
					continue;
				}
				final Map<String, Map<String, Category>> parseData = TagParser.parse(taggedLine);
				final String noTaggedLine = parseData.get("noTag").keySet().iterator().next();

				final Map<Integer, Map<String, String>> result = nerXmlParser(MyStanfordCoreNLP.runNerTaggerXML(noTaggedLine));

				addContextFeatures(result, 2);

				try {
					if (line.contains(Global.getHeadRoleStartTag())) {
						switch (TP) {
//						case ONLY_ANCHOR_TEXT:
//							addPositivLabelsKeepAnchorTextOnly(result, taggedLine, noTaggedLine, parseData);
//							break;
						case ONLY_HEAD_ROLE:
							addPositivLabelsKeepHeadRoleOnly(result, taggedLine, noTaggedLine, parseData);
							break;
//						case ONLY_ROLE_PHRASE:
//							addPositivLabelsKeepRolePhraseOnly(result, taggedLine, noTaggedLine, parseData);
//							break;
						default:
							throw new IllegalArgumentException("TaggerType should be selected");
						}
					} else{
						addNegativeLabels(result);
					}
				} catch (Exception e) {
					continue;
				}

				evaluate(result,noTaggedLine);
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private static void evaluate(Map<Integer, Map<String, String>> result, String noTaggedLine) {
	final List<Tuple> tuples = runStanfordCRF(noTaggedLine);
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

	private static List<Tuple> runStanfordCRF(String line) {
		List<Tuple> runCRFTest = new ArrayList<>();
		try {
			final String noTaggedLine = new String(line);
			final Map<Integer, Map<String, String>> result = nerXmlParser(CustomNERTagger.runTaggerXML(noTaggedLine));

			addContextFeatures(result, 2);


			final List<String> localfinalResult = new ArrayList<>();
			for (Entry<Integer, Map<String, String>> entity2 : result.entrySet()) {
				final Map<String, String> value = entity2.getValue();
				final StringBuilder l2 = new StringBuilder();

				String tag = value.get("NER");
				if(!tag.equalsIgnoreCase("O") && !tag.equalsIgnoreCase("ROLE")) {
					tag = "O";
				}
				l2.append(tag + "\t");

				runCRFTest.add(new Tuple(value.get("word"), tag));
				
				for (Entry<String, String> e : value.entrySet()) {
					if (e.getKey().equals("N2Ner")) {
						l2.append(e.getKey() + "=" + e.getValue());
					} else if (!e.getKey().equals("TAG") && !e.getKey().equals("ID")) {
						l2.append(e.getKey() + "=" + e.getValue()).append("\t");
					}	
				}
				localfinalResult.add(l2.toString());
			}
			localfinalResult.add("\n");
			
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return runCRFTest;
	}
	/**
	 * Add Tag to negative data. Tag means label of the data which for negative case
	 * it is always "O"
	 * 
	 * @param result
	 */
	private static void addNegativeLabels(Map<Integer, Map<String, String>> result) {
		for (int i = 0; i < result.size(); i++) {
			result.get(i).put("TAG", "O");
		}
	}

	/**
	 * Add Tag to positive data. Tag means label of the data which for positive case
	 * it can be "ROLE" or "O" This function only consider Head Roles as "ROLE" and
	 * tag others as "O"
	 * 
	 * <a><RP Category=""><HR></HR></RP></a>
	 * 
	 * @param result
	 * @param parseData
	 */
	private static void addPositivLabelsKeepHeadRoleOnly(Map<Integer, Map<String, String>> result, String taggedLine,
			String noTaggedLine, Map<String, Map<String, Category>> parseData) {
		final TokenizerFactory<Word> tf = PTBTokenizer.factory();
		int wordCount = 0;
		taggedLine = Global.removeAnchorTextTag(taggedLine);
		taggedLine = Global.removeRolePhraseTag(taggedLine);

		try {
			final List<Word> tokens_words = tf.getTokenizer(new StringReader(taggedLine)).tokenize();

			boolean inside = false;
			for (Word w : tokens_words) {
				if (!inside) {
					if (w.value().contains(Global.getHeadRoleStartTag())) {
						inside = true;
						continue;
					} else if (w.value().contains(Global.getHeadRoleEndTag())) {
						continue;
					} else {
						result.get(wordCount++).put("TAG", "O");
					}
				} else {
					if (w.value().contains(Global.getHeadRoleEndTag())) {
						inside = false;
						continue;
					} else {
						result.get(wordCount++).put("TAG", "ROLE");
					}
				}
			}
		} catch (NullPointerException e) {
			throw e;
		}
	}

	/**
	 * Add Tag to positive data. Tag means label of the data which for positive case
	 * it can be "ROLE" or "O" This function only consider Role Phrases as "ROLE"
	 * and tag others as "O"
	 * 
	 * <a><RP Category=""><HR></HR></RP></a>
	 * @param result
	 * @param parseData
	 */
	private static void addPositivLabelsKeepRolePhraseOnly(Map<Integer, Map<String, String>> result, String taggedLine,
			String noTaggedLine, Map<String, Map<String, Category>> parseData) {
		final TokenizerFactory<Word> tf = PTBTokenizer.factory();
		int wordCount = 0;
		taggedLine = Global.removeAnchorTextTag(taggedLine);
		taggedLine = Global.removeHeadRoleTag(taggedLine);

		try {
			final List<Word> tokens_words = tf.getTokenizer(new StringReader(taggedLine)).tokenize();

			boolean inside = false;
			for (Word w : tokens_words) {
				if (!inside) {
					if (w.value().contains(Global.getRolePhraseStartTag("").substring(0, 3))) {
						inside = true;
						continue;
					} else if (w.value().contains(Global.getRolePhraseEndTag())) {
						continue;
					} else {
						result.get(wordCount++).put("TAG", "O");
					}
				} else {
					if (w.value().contains(Global.getRolePhraseEndTag())) {
						inside = false;
						continue;
					} else {
						result.get(wordCount++).put("TAG", "ROLE");
					}
				}
			}
		} catch (NullPointerException e) {
			throw e;
		}
	}

	/**
	 * Add Tag to positive data. Tag means label of the data which for positive case
	 * it can be "ROLE" or "O" This function only consider <a> as "ROLE" and tag
	 * others as "O"
	 * 
	 * <a><RP Category=""><HR></HR></RP></a>
	 * @param result
	 * @param parseData
	 */
	private static void addPositivLabelsKeepAnchorTextOnly(Map<Integer, Map<String, String>> result, String taggedLine,
			String noTaggedLine, Map<String, Map<String, Category>> parseData) {
		final TokenizerFactory<Word> tf = PTBTokenizer.factory();
		int wordCount = 0;
		taggedLine = Global.removeHeadRoleTag(taggedLine);
		taggedLine = Global.removeRolePhraseTag(taggedLine);

		try {
			final List<Word> tokens_words = tf.getTokenizer(new StringReader(taggedLine)).tokenize();

			boolean inside = false;
			for (Word w : tokens_words) {
				if (!inside) {
					if (w.value().contains(Global.getAnchorStartTag())) {
						inside = true;
						continue;
					} else if (w.value().contains(Global.getAnchorEndTag())) {
						continue;
					} else {
						result.get(wordCount++).put("TAG", "O");
					}
				} else {
					if (w.value().contains(Global.getAnchorEndTag())) {
						inside = false;
						continue;
					} else {
						result.get(wordCount++).put("TAG", "ROLE");
					}
				}
			}
		} catch (NullPointerException e) {
			throw e;
		}
	}

	/**
	 * consider window around the word and add features of the window
	 * 
	 * @param result
	 * @param windowSize
	 *            how many words consider before and after main word
	 */
	private static void addContextFeatures(Map<Integer, Map<String, String>> result, int windowSize) {
		for (final Entry<Integer, Map<String, String>> entity : result.entrySet()) {
			final Integer wordPosition = entity.getKey();

			for (int i = 1; i <= windowSize; i++) {
				Map<String, String> previousWord = getFeaturesOfNeighborWord(wordPosition, wordPosition - i, result, i);
				result.put(wordPosition, previousWord);
			}

			for (int i = 1; i <= windowSize; i++) {
				Map<String, String> nextWord = getFeaturesOfNeighborWord(wordPosition, wordPosition + i, result, i);
				result.put(wordPosition, nextWord);
			}
		}
	}

	/**
	 * Currently only uses - Word - POS - NER
	 * 
	 * @param wordPosition
	 * @param neighborPosition
	 * @param result
	 * @param i
	 * @return
	 */
	private static Map<String, String> getFeaturesOfNeighborWord(int wordPosition, int neighborPosition,
			Map<Integer, Map<String, String>> result, int i) {
		String letter = "P";
		if (neighborPosition > wordPosition) {
			letter = "N";
		}
		if (!result.containsKey(neighborPosition)) {
			final Map<String, String> wordFeature = result.get(wordPosition);
			wordFeature.put(letter + i, "NIL");
			wordFeature.put(letter + i + "Pos", "NIL");
			wordFeature.put(letter + i + "Ner", "NIL");
			return wordFeature;
		} else {
			final Map<String, String> list = result.get(neighborPosition);
			final Map<String, String> wordFeature = result.get(wordPosition);
			wordFeature.put(letter + i, list.get("word"));
			wordFeature.put(letter + i + "Pos", list.get("POS"));
			wordFeature.put(letter + i + "Ner", list.get("NER"));
			return wordFeature;
		}

	}

	/**
	 * Parse result of the Stanford CoreNLP and extract features
	 * 
	 * @param xml
	 * @return
	 */
	public static Map<Integer, Map<String, String>> nerXmlParser(final String xml) {
		try {
			Map<Integer, Map<String, String>> result = new LinkedHashMap<>();
			final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			final org.w3c.dom.Document document = docBuilder
					.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			final Map<String, String> features = new LinkedHashMap<>();
			final NodeList nodeList = document.getElementsByTagName("*");
			int wordPosition = 0;
			int wordCounter = 0;
			for (int i = 0; i < nodeList.getLength(); i++) {
				final Node node = nodeList.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("token")) {
					if (node.hasChildNodes()) {
						for (int j = 0; j < node.getChildNodes().getLength(); j++) {
							final Node childNode = node.getChildNodes().item(j);

							if (childNode.getNodeType() == Node.ELEMENT_NODE) {
								if (childNode.getNodeName().equals("word")) {
									features.put("ID", String.valueOf(wordCounter++));
									features.put("word", childNode.getTextContent());
									if (childNode.getTextContent().charAt(0) >= 65
											&& childNode.getTextContent().charAt(0) <= 90) {
										features.put("STARTCAP", "true");
									} else {
										features.put("STARTCAP", "false");
									}
									if (StringUtils.isAllUpperCase(childNode.getTextContent())) {
										features.put("ALLCAP", "true");
									} else {
										features.put("ALLCAP", "false");
									}
								} else if (childNode.getNodeName().equals("lemma")) {
									features.put("lemma", childNode.getTextContent());
								} else if (childNode.getNodeName().equals("POS")) {
									features.put("POS", childNode.getTextContent());
								} else if (childNode.getNodeName().equals("NER")) {
									features.put("NER", childNode.getTextContent());
								}
							}
						}
					}
					final Map<String, String> map = new LinkedHashMap<>();
					map.putAll(features);
					result.put(wordPosition++, map);
					features.clear();
				}
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
