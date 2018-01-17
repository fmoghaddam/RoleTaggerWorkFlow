package TEST;

import java.util.HashMap;
import java.util.Map;

import ESWCrefactoring.ConvertDatasetToFeatureSetForCRFSuite;
import ESWCrefactoring.SentenceToFeature;
import ise.roletagger.datasetconvertor.TagParser;
import ise.roletagger.evaluationtokenbased.CRFModels;
import ise.roletagger.evaluationtokenbased.RunCRFSuite;
import ise.roletagger.model.Category;
import ise.roletagger.model.Global;

public class TestCRFForGivenSentence {

	public static void main(String[] args) {
		//String s = "The CEO serves at the discretion of the Board of Directors.";
		//String s = "Martin Luter King did not run for president but talk with Mrs. Pope and Pope Francis and Alexander Pope";
		//String s ="president and the ceo of Iran";
		//String s= "Very few Americans believe that all abortions all the time are all right.";
		//String s = "Pope Francis is a good guy and he is good state.";
		//final String stopWordsremoved = CharactersUtils.removeStopWords(s);
		//String normalized = MyStandfordCoreNLPRegex.normzalize(stopWordsremoved);
		//String s = "WASHINGTON, Dec. 14— President-elect Bush today specified the role that he sees for Dan Quayle, the man who will occupy the job Mr. Bush held for the eight years.";
		String s = "Alexander Pope was affected by the recently enacted Test Acts, which upheld the status of the established Church of England and banned Catholics from teaching, attending a university, voting, or holding public office on pain of perpetual imprisonment. ";// is the father of President Bush.";
//		
		String taggedLine = ConvertDatasetToFeatureSetForCRFSuite.normalizeTaggedSentence(s);
		final Map<String, Map<String, Category>> parseData = TagParser.parse(taggedLine);
		String noTaggedLine = parseData.get("noTag").keySet().iterator().next();
		noTaggedLine = noTaggedLine.trim();
		final Map<Integer, Map<String, String>> result = SentenceToFeature.convertTaggedSentenceToFeatures(taggedLine, s.contains(Global.getHeadRoleStartTag()));
		
//		final Map<Integer, Map<String, String>> result = new HashMap<>();
//		
//		Map<String,String> map = new HashMap<>();
//		map.put("TAG", "ROLE");
//		map.put("word", "President");
//		result.put(0, new HashMap<>(map));
		
//		map.put("TAG", "ROLE");
//		map.put("word", "Queen");
//		result.put(0, new HashMap<>(map));
//		
//		map.put("TAG", "O");
//		map.put("word", "is");
//		result.put(1, new HashMap<>(map));
//		
//		map.put("TAG", "ROLE");
//		map.put("word", "President");
//		result.put(2, new HashMap<>(map));
//		
//		map.put("TAG", "ROLE");
//		map.put("word", "Pope");
//		result.put(2, new HashMap<>(map));
		
		RunCRFSuite.run(CRFModels.ONLY_HEAD_ROLE,result).forEach(p -> System.err.println(p));

	}

}
