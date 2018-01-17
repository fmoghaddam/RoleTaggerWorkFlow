package TEST;

import java.util.Map;
import java.util.Set;

import ise.roletagger.dataset2WSD.WSDMapGenerator;
import ise.roletagger.util.CharactersUtils;
import ise.roletagger.util.MyStandfordCoreNLPRegex;

public class TestWSDForDifficultNegativeSamples {

	public static void main(String[] args) {
		//String s = "The is a powerfull piece in chess";
		String s = "The Nissan was a Japanese luxury limousine produced by Nissan from 1965 to 2010";

		WSDMapGenerator.handle();
		
		final Map<String, Map<String, Double>> positivewdsmap = WSDMapGenerator.getPositivewdsmap();
		final Map<String, Map<String, Double>> negativewdsmap = WSDMapGenerator.getNegativewdsmap();
		
		final String stopWordsremoved = CharactersUtils.removeStopWords(s).toLowerCase();
		String normalized = MyStandfordCoreNLPRegex.normzalize(stopWordsremoved);
		
		final Set<String> positiveKingSense = positivewdsmap.get("president").keySet();
		final Set<String> negativeKingSense = negativewdsmap.get("president").keySet();
		
		System.err.println(compare(positiveKingSense,normalized));
		System.err.println(compare(negativeKingSense,normalized));
		
		
		
		
	}

	private static float compare(Set<String> kingSense, String normalized) {
		final String[] split = normalized.split(" ");
		float length = split.length;
		float sum = 0;
		for(String s:split) {
			if(kingSense.contains(s)) {
				sum++;
			}
		}
		
		return sum/length;
	}

}
