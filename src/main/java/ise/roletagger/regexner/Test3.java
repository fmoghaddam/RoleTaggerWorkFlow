package ise.roletagger.regexner;

import ise.roletagger.evaluationtokenbased.CRFModels;
import ise.roletagger.evaluationtokenbased.RunCRFSuite;
import ise.roletagger.util.MyStandfordCoreNLPRegex;

public class Test3 {

	public static void main(String[] args) {
		//String s = "he is <RP Category='POPE_CAT'>the <HR>Pope</HR>'s mother</RP> is good <RP Category='CEO_TAG'> <RP Category='CEO_TAG'>";
		String s = "that the Pope's 12fj @@!! ₹ visit vice-president J.r. -- äuslander is cammander-in-chief";
//		final String normzalize = MyStandfordCoreNLPRegex.normzalize(s);
//		System.err.println(normzalize);
//		
//		System.err.println(MyStandfordCoreNLPRegex.convertToLemma(normzalize));
//		final List<Token> run = MyStandfordCoreNLPRegex.run(normzalize);
//		
//		
//		run.forEach(p -> System.err.println(p));
		
		
//		TagParser.replaceAlltheTagsWithRandomString(s);
		
//		System.err.println(MyStandfordCoreNLPRegex.normalizeAndConvertToLemma(s));
		
//		RunCRFSuite.run(s, CRFModels.ONLY_HEAD_ROLE).forEach(p->System.err.println(p));
		s = s.replaceAll("[^\\w\\s]", " ").replaceAll("[\\d]", "").replaceAll("\\s+"," ").toLowerCase().trim();
		System.err.println(s);
		System.err.println(MyStandfordCoreNLPRegex.normalizeAndConvertToLemma(s));
	}

}
