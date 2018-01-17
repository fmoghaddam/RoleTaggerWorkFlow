package ise.roletagger.dataset2WSD;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ise.roletagger.datasetconvertor.TagParser;
import ise.roletagger.model.Category;
import ise.roletagger.model.Global;
import ise.roletagger.util.CharactersUtils;
import ise.roletagger.util.Config;
import ise.roletagger.util.MyStandfordCoreNLPRegex;

public class WSDMapGenerator {

private static final String DATASET_BASE_ADDRESS = Config.getString("WHERE_TO_WRITE_DATASET", "");
	
	private static final String POSITIVE_DATA = DATASET_BASE_ADDRESS+Config.getString("POSITIVE_DATASET_NAME", "")+"Unique";
	private static final String NEGATIVE_DATA = DATASET_BASE_ADDRESS+Config.getString("DIFFICULT_NEGATIVE_DATASET_NAME", "")+"Unique";
	
	private static final Map<String,Map<String,Double>> positiveWDSMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private static final Map<String,Map<String,Double>> negativeWDSMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	
	public static void main(String[] args) {
		final Thread t = new Thread(run());
		t.setDaemon(false);
		t.start();
	}
	
	public static Runnable run() {
		return () -> {
			handle();
		};
	}

	public static void handle() {
		try {
			final List<String> positiveLines = Files.readAllLines(Paths.get(POSITIVE_DATA), StandardCharsets.UTF_8);
			final List<String> negativeLines = Files.readAllLines(Paths.get(NEGATIVE_DATA), StandardCharsets.UTF_8);
			
			for(String positive:positiveLines) {
				final Map<String, Map<String, Category>> parseData = TagParser.parse(positive);
				final String noTaggedLine = parseData.get("noTag").keySet().iterator().next().toLowerCase();					
				final String stopWordsremoved = CharactersUtils.removeStopWords(noTaggedLine);
				final String normalized = MyStandfordCoreNLPRegex.normzalize(stopWordsremoved);
				final Set<String> tokens = tokenizeSentence(normalized);
				
				
				final List<String> headRoles = parseHeadRoles(positive);
				for(String headRole:headRoles) {
					final Map<String, Double> listOfSencesWord = positiveWDSMap.get(headRole);
					if(listOfSencesWord==null) {
						final Map<String,Double> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
						for(String t:tokens) {
							map.put(t, 1.);
						}
						positiveWDSMap.put(headRole, map);
					}else {
						final Map<String,Double> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
						map.putAll(listOfSencesWord);
						for(String t:tokens) {
							Double frequency = map.get(t);
							if(frequency==null) {
								map.put(t,1.);
							}else {
								map.put(t, (frequency+1));
							}
						}
						positiveWDSMap.put(headRole, map);
					}
				}
			}
			
			for(String negative:negativeLines) {
				final Map<String, Map<String, Category>> parseData = TagParser.parse(negative);
				final String noTaggedLine = parseData.get("noTag").keySet().iterator().next().toLowerCase();					
				final String stopWordsremoved = CharactersUtils.removeStopWords(noTaggedLine);
				final String normalized = MyStandfordCoreNLPRegex.normzalize(stopWordsremoved);
				final Set<String> tokens = tokenizeSentence(normalized);
				
				final List<String> headRoles = parseHeadRoles(negative);
				for(String headRole:headRoles) {
					final Map<String, Double> listOfSencesWord = negativeWDSMap.get(headRole);
					if(listOfSencesWord==null) {
						final Map<String,Double> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
						for(String t:tokens) {
							map.put(t, 1.);
						}
						negativeWDSMap.put(headRole, map);
					}else {
						final Map<String,Double> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
						map.putAll(listOfSencesWord);
						for(String t:tokens) {
							Double frequency = map.get(t);
							if(frequency==null) {
								map.put(t,1.);
							}else {
								map.put(t, (frequency+1));
							}
						}
						negativeWDSMap.put(headRole, map);
					}
				}
			}
			filterLowFrequency(5,positiveWDSMap);
			filterLowFrequency(5,negativeWDSMap);
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	private static Set<String> tokenizeSentence(String sentence) {
		return new HashSet<>(Arrays.asList(sentence.split(" ")));
	}

	private static List<String> parseHeadRoles(String sentence) {
		final Pattern pattern = Pattern.compile(Global.getHeadRoleStartTag()+".+?"+Global.getHeadRoleEndTag());
		final Matcher matcher = pattern.matcher(sentence);
		List<String> result = new ArrayList<>();
		while (matcher.find()) {
			result.add(Global.removeHeadRoleTag(matcher.group()));
		}
		return result;
	}

	public static Map<String, Map<String, Double>> getPositivewdsmap() {
		return positiveWDSMap;
	}

	public static Map<String, Map<String, Double>> getNegativewdsmap() {
		return negativeWDSMap;
	}
	
	private static void filterLowFrequency(int minFreq,Map<String,Map<String,Double>> dictionary) {
		for(Entry<String, Map<String, Double>> entry:dictionary.entrySet()) {
			for(Entry<String, Double> e: entry.getValue().entrySet()) {
				if(e.getValue()<minFreq) {
					entry.getValue().remove(e.getKey()); 
				}
			}
		}
	}
	
}
