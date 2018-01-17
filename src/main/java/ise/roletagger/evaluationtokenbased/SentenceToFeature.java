package ise.roletagger.evaluationtokenbased;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.stanford.nlp.ling.Word;
import ise.roletagger.datasetconvertor.TagParser;
import ise.roletagger.datasetconvertor.TaggerType;
import ise.roletagger.model.Category;
import ise.roletagger.model.Global;
import ise.roletagger.model.Position;
import ise.roletagger.model.Token;
import ise.roletagger.util.MyStandfordCoreNLPRegex;
import ise.roletagger.util.MyStanfordCoreNLP;

/**
 * This class is responsible for converting normal sentences or tagged sentences
 * to a features set
 * 
 * @author fbm
 *
 */
public class SentenceToFeature {

	/**
	 * How many words should be considered as a context?
	 */
	private final static int WINDOW_SIZE = 2;
	private final static String RESULT_CONTENT_SEPARATOR = "\t";

	public static Map<Integer, Map<String, String>> convertPlainSentenceToFeatures(final String plainSentence) {
		final Map<Integer, Map<String, String>> result = nerXmlParser(MyStanfordCoreNLP.runNerTaggerXML(plainSentence));		
		addContextFeatures(result, WINDOW_SIZE);
		
		final List<Token> run = MyStandfordCoreNLPRegex.run(plainSentence);
		final List<Integer> wordIds = run.stream().filter(p->"ROLE".equals(p.getContent().get("NER"))).map(p->Integer.parseInt(p.getContent().get("ID"))).collect(Collectors.toList());
		addOtherFeatrues(result,wordIds);
		
		return result;
	}

	public static List<String> featureMapToStringList(final Map<Integer, Map<String, String>> result) {
		final List<String> localfinalResult = new ArrayList<>();
		for (Entry<Integer, Map<String, String>> entity2 : result.entrySet()) {
			final Map<String, String> value = entity2.getValue();
			final StringBuilder l2 = new StringBuilder();

			final String tag = value.get("TAG");
			if (tag == null) {
				continue;
			}
			l2.append(tag + RESULT_CONTENT_SEPARATOR);

			for (Entry<String, String> e : value.entrySet()) {
				/**
				 * This tag should be the last tag always
				 */
				if (e.getKey().equals("N2Ner")) {
					l2.append(e.getKey() + "=" + e.getValue());
				} else if (!e.getKey().equals("TAG") && !e.getKey().equals("ID")) {
					l2.append(e.getKey() + "=" + e.getValue()).append(RESULT_CONTENT_SEPARATOR);
				}
			}
			localfinalResult.add(l2.toString());
		}
		localfinalResult.add("\n");
		return localfinalResult;
	}

	/**
	 * Convert tagged sentence to a feature set + adds corresponding label to it it
	 * can be used to convert Ground Truth sentences to feature set
	 * 
	 * @param taggedLine
	 * @param isPositive
	 * @return
	 */
	public static Map<Integer, Map<String, String>> convertTaggedSentenceToFeatures(final String taggedLine,TaggerType TP ,final boolean isPositive) {
		final Map<String, Map<String, Category>> parseData = TagParser.parse(taggedLine);
		final String noTaggedLine = parseData.get("noTag").keySet().iterator().next().replaceAll("\\s+", " ");

		final Map<Integer, Map<String, String>> result = nerXmlParser(MyStanfordCoreNLP.runNerTaggerXML(noTaggedLine));
		
		final List<Token> tokens = MyStandfordCoreNLPRegex.run(noTaggedLine);
		final List<Integer> wordIds = tokens.stream().filter(p->"ROLE".equals(p.getContent().get("NER"))).map(p->Integer.parseInt(p.getContent().get("ID"))).collect(Collectors.toList());

		addOtherFeatrues(result,wordIds);		
		addContextFeatures(result, WINDOW_SIZE);
		
		try {
			if (isPositive) {
				newWayOfAddingLabel(result, taggedLine,tokens);
			} else {
				addNegativeLabels(result);
			}
		} catch (Exception e) {
			// e.printStackTrace();
		}
		
//		try {
//			if (isPositive) {
//				switch (taggerType) {
////				case ONLY_ANCHOR_TEXT:
////					addPositivLabelsKeepAnchorTextOnly(result, taggedLine);
////					break;
//				case ONLY_HEAD_ROLE:
//					addPositivLabelsKeepHeadRoleOnly(result, taggedLine);
//					break;
//				case ONLY_ROLE_PHRASE:
//					addPositivLabelsKeepRolePhraseOnly(result, taggedLine);
//					break;
//				default:
//					throw new IllegalArgumentException("TaggerType should be selected");
//				}
//			} else {
//				addNegativeLabels(result);
//			}
//		} catch (Exception e) {
//			// e.printStackTrace();
//		}

		
		for (Iterator<Map<String, String>> iterator = result.values().iterator(); iterator.hasNext();) {
			final Map<String,String> map = (Map<String, String>) iterator.next();
			if(map.get("TAG").equals("ROLE")) {
				if(map.get("word").equalsIgnoreCase("Buckingham")) {
					System.err.println("-----------------------------------------------------------");
					System.err.println(noTaggedLine);
					System.err.println(taggedLine);
					System.err.println("-----------------------------------------------------------");
					System.exit(1);
				}
			}
		}
		return result;
	}

	private static void addPositivLabels(Map<Integer, Map<String, String>> result, List<Integer> wordIds) {
		for (int i = 0; i < result.size(); i++) {
			if(wordIds.contains(i)) {
				result.get(i).put("TAG", "ROLE");
			}else {
				result.get(i).put("TAG", "O");
			}
		}
	}

	private static void addOtherFeatrues(Map<Integer, Map<String, String>> result, List<Integer> wordIds) {
		for (int i = 0; i < result.size(); i++) {
			if(wordIds.contains(i)) {
				result.get(i).put("IsInDic", "1");
			}else {
				result.get(i).put("IsInDic", "0");
			}
		}
	}

	/**
	 * Add Tag to negative data. Tag means label of the data which for negative case
	 * it is always "O"
	 * 
	 * @param result
	 */
	public static void addNegativeLabels(Map<Integer, Map<String, String>> result) {
		for (int i = 0; i < result.size(); i++) {
			result.get(i).put("TAG", "O");
		}
	}

	/**
	 * This function look at the positions of <HR> tag
	 * and try to find ROLE and O
	 * @param result
	 * @param taggedLine
	 * @param tokens
	 */
	private static void newWayOfAddingLabel(Map<Integer, Map<String, String>> result, String taggedLine, List<Token> tokens) {
		taggedLine = Global.removeAnchorTextTag(taggedLine);
		taggedLine = Global.removeRolePhraseTag(taggedLine);
		
		Pattern  p = Pattern.compile("<HR>.*?<?/HR>");
		Matcher m = p.matcher(taggedLine);

		int offset = 0;
		List<Position> positions = new ArrayList<>();
		while(m.find()) {
			positions.add(new Position((m.start()+4-4+offset), (m.end()-5-4+offset)));
			offset-=9;
		}

		int wordCount = 0;
		int start = 0;
		int end = 0;
		for(Entry<Integer, Map<String, String>> e:result.entrySet()) {
			final Token token = tokens.get(wordCount++);

			start = Integer.parseInt(token.getContent().get("CharacterOffsetBegin"));
			end = Integer.parseInt(token.getContent().get("CharacterOffsetEnd"));
			
			boolean found = false;
			for(Position pos:positions) {
				if(new Position(start, end).contains(pos)){
					result.get(e.getKey()).put("TAG", "ROLE");
					found = true;
					break;
				}
			}
			if(!found) {
				result.get(e.getKey()).put("TAG", "O");
			}
		}
	}
	/**
	 * Add Tag to positive data. Tag means label of the data which for positive case
	 * it can be "ROLE" or "O" This function only consider Head Roles as "ROLE" and
	 * tag others as "O"
	 * 
	 * <a><RP Category="">
	 * <HR>
	 * </HR>
	 * </RP></a>
	 * 
	 * @param result
	 */
	private static void addPositivLabelsKeepHeadRoleOnly(Map<Integer, Map<String, String>> result, String taggedLine) {

		int wordCount = 0;
		taggedLine = Global.removeAnchorTextTag(taggedLine);
		taggedLine = Global.removeRolePhraseTag(taggedLine);
		try {
			final List<Word> tokens_words = MyStanfordCoreNLP.tokenize(taggedLine);
//			boolean isBeginning = true;
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
//						isBeginning = true;
					}
				} else {
					if (w.value().contains(Global.getHeadRoleEndTag())) {
						inside = false;
						continue;
					} else {
//						if (isBeginning) {
//							result.get(wordCount++).put("TAG", "B-ROLE");
//							isBeginning = false;
//						} else {
//							result.get(wordCount++).put("TAG", "I-ROLE");
//						}
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
	 * <a><RP Category="">
	 * <HR>
	 * </HR>
	 * </RP></a>
	 * 
	 * @param result
	 */
	private static void addPositivLabelsKeepRolePhraseOnly(Map<Integer, Map<String, String>> result, String taggedLine) {
		int wordCount = 0;
		taggedLine = Global.removeAnchorTextTag(taggedLine);
		taggedLine = Global.removeHeadRoleTag(taggedLine);

		try {
			final List<Word> tokens_words = MyStanfordCoreNLP.tokenize(taggedLine);
			
//			boolean isBeginning = true;
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
//						isBeginning = true;
					}
				} else {
					if (w.value().contains(Global.getRolePhraseEndTag())) {
						inside = false;
						continue;
					} else {
//						if (isBeginning) {
//							result.get(wordCount++).put("TAG", "B-ROLE");
//							isBeginning = false;
//						} else {
//							result.get(wordCount++).put("TAG", "I-ROLE");
//						}
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
	 * <a><RP Category="">
	 * <HR>
	 * </HR>
	 * </RP></a>
	 * 
	 * @param result
	 */
	private static void addPositivLabelsKeepAnchorTextOnly(Map<Integer, Map<String, String>> result, String taggedLine) {
		int wordCount = 0;
		taggedLine = Global.removeHeadRoleTag(taggedLine);
		taggedLine = Global.removeRolePhraseTag(taggedLine);

		try {
			final List<Word> tokens_words = MyStanfordCoreNLP.tokenize(taggedLine);

			boolean inside = false;
			boolean isBeginning = true;
			for (Word w : tokens_words) {
				if (!inside) {
					if (w.value().contains(Global.getAnchorStartTag())) {
						inside = true;
						continue;
					} else if (w.value().contains(Global.getAnchorEndTag())) {
						continue;
					} else {
						result.get(wordCount++).put("TAG", "O");
						isBeginning = true;
					}
				} else {
					if (w.value().contains(Global.getAnchorEndTag())) {
						inside = false;
						continue;
					} else {
						if (isBeginning) {
							result.get(wordCount++).put("TAG", "B-ROLE");
							isBeginning = false;
						} else {
							result.get(wordCount++).put("TAG", "I-ROLE");
						}
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
