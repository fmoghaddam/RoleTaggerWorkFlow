package TEST;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ise.roletagger.model.Global;

public class TestTest {

	public static void main(String[] args) {
		String s = "he <a>is <RP Category='POPE_TAG'><HR>presidnet</HR></RP> of me</a>. he <a>is <RP Category='POPE_TAG'><HR>presidnet</HR></RP> of me</a>.";
		
		s= Global.removeAnchorTextTag(s);
		s= Global.removeRolePhraseTag(s);

		Pattern  p = Pattern.compile("<HR>.*?<?/HR>");
		Matcher m = p.matcher(s);

		System.err.println(s);
		int offset = 0;
		while(m.find()) {
			System.err.println((m.start()+4-4+offset)+"  "+(m.end()-5-4+offset));
			offset-=9;
		}

	}

}
