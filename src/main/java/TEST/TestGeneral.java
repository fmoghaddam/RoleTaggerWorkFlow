package TEST;

import ESWCrefactoring.GenerateDataSet;
import ise.roletagger.util.CharactersUtils;

public class TestGeneral {

	public static void main(String[] args) {
//		String s = "N2====";
//		final int indexOf = s.indexOf("=");
//		final String key = s.substring(0, indexOf);
//		final String value = s.substring(indexOf+1, s.length());
//		
//		System.err.println(key);
//		System.err.println(value);
		
//		String s = "HE:IS <a href=\"saasad\">is`````</a> king's fathers borther";
		String s = "KinGs better,CoE is";
		//System.err.println(CharactersUtils.normalize(s));
		System.err.println(GenerateDataSet.normalizeTaggedSentence(s));
		
//		String text = "he]'p] is, Ѷ V -> V Ȳ->Y (good guy) öff            \"asd\" 'salam' شس is:bush o-o-o J.F Kenedy pope's i;s";
//		System.err.println(text);
//		System.err.println(CharactersUtils.normalize(text));
//		text = "HE <a href=\"saasad\">is</a> king's fathers borther";
//		System.err.println(CharactersUtils.normalize(text));
	}

}
