package ise.roletagger.word2vec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ise.roletagger.datasetconvertor.TagParser;
import ise.roletagger.model.Category;
import ise.roletagger.util.CharactersUtils;
import ise.roletagger.util.Config;
import ise.roletagger.util.FileUtil;
import ise.roletagger.util.MyStandfordCoreNLPRegex;

public class PrepareDataForTraining {

	private static final String DATASET_BASE_ADDRESS = Config.getString("WHERE_TO_WRITE_DATASET", "");
	
	private static final String POSITIVE_DATA = DATASET_BASE_ADDRESS+Config.getString("POSITIVE_DATASET_NAME", "")+"Unique";
	private static final String NEGATIVE_DATA = DATASET_BASE_ADDRESS+Config.getString("DIFFICULT_NEGATIVE_DATASET_NAME", "")+"Unique";
	
	public static void main(String[] args) {
		final Thread t = new Thread(run());
		t.setDaemon(false);
		t.start();
	}
	
	public static Runnable run() {
		return () -> {
			try {
				final List<String> positiveLines = Files.readAllLines(Paths.get(POSITIVE_DATA), StandardCharsets.UTF_8);
				final List<String> negativeLines = Files.readAllLines(Paths.get(NEGATIVE_DATA), StandardCharsets.UTF_8);
				
				final List<String> cleanPositiveLines = new ArrayList<>();
				final List<String> cleanNegativeLines = new ArrayList<>();
				
				for(String positive:positiveLines) {
					final Map<String, Map<String, Category>> parseData = TagParser.parse(positive);
					final String noTaggedLine = parseData.get("noTag").keySet().iterator().next().toLowerCase();
					final String stopWordsremoved = CharactersUtils.removeStopWords(noTaggedLine);
					String normalized = MyStandfordCoreNLPRegex.normzalize(stopWordsremoved);
					cleanPositiveLines.add(normalized);
				}
				
				FileUtil.writeDataToFile(cleanPositiveLines,POSITIVE_DATA+"CleanForWord2Vec",false);
				
				for(String negative:negativeLines) {
					final Map<String, Map<String, Category>> parseData = TagParser.parse(negative);
					final String noTaggedLine = parseData.get("noTag").keySet().iterator().next().toLowerCase();
					final String stopWordsremoved = CharactersUtils.removeStopWords(noTaggedLine);
					String normalized = MyStandfordCoreNLPRegex.normzalize(stopWordsremoved);
					cleanNegativeLines.add(normalized);
				}
				FileUtil.writeDataToFile(cleanNegativeLines,NEGATIVE_DATA+"CleanForWord2Vec",false);
				
			}catch(Exception e) {
				e.printStackTrace();
			}
		};
	}

}
