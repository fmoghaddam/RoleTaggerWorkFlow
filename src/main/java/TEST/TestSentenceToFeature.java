package TEST;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ESWCrefactoring.SentenceToFeature;
import ise.roletagger.datasetconvertor.TaggerType;
import ise.roletagger.model.Global;
import ise.roletagger.util.CharactersUtils;

public class TestSentenceToFeature {

	public static void main(String[] args) {
		//String s = "However, the <RP Category='HEAD_OF_STATE_TAG'><HR>U.S. press</HR></RP> continued to refer to her as the \"Tin Plate Heiress\".";
		//String s = "I am ₱ <RP Category='HEAD_OF_STATE_TAG'>sss <HR>he of state</HR></RP> of pope.";
		//String s = "The second installment of the two part episode was scheduled to air the following week, but was preempted by a speech by then-<a><RP Category='HEAD_OF_STATE_TAG'><HR>President</HR></RP> Reagan</a>.";
		//String s = "he <a>is <RP Category='POPE_TAG'><HR>presidnet</HR></RP> of me</a>.he <a>is <RP Category='POPE_TAG'><HR>presidnet</HR></RP> of me</a>.";
		//String s = "The Senate shall choose their other Officers, and also a <RP Category='HEAD_OF_STATE_TAG'>President</RP> pro tempore, in the absence of the Vice <RP Category='HEAD_OF_STATE_TAG'>President</RP>, or when he shall exercise the Office of <RP Category='HEAD_OF_STATE_TAG'>President</RP> of the United States.";
		//String s = "<RP Category='HEAD_OF_STATE_TAG'><HR>President</HR> Bush</RP>: 10:30 A.M., CNN Newsmaker Sunday, interview.";
		//String s = "The company incorporated in 1920 with Sexton as <RP Category='HEAD_OF_STATE_TAG'><HR>President</HR></RP> and Buckingham as Secretary and Treasurer.";
		String s = "WASHINGTON, Dec. 14— <RP Category='HEAD_OF_STATE_TAG'><HR>President-elect</HR> Bush</RP> today specified the role that he sees for Dan Quayle, the man who will occupy the job Mr. Bush held for the eight years.";
		s = normalizeTaggedSentence(s);
		//s = s.replace("-", " ");
		//final List<Word> tokenize = MyStanfordCoreNLP.tokenize(s);
		//tokenize.forEach(c->System.err.println(c));
		
		Map<Integer, Map<String, String>> convertTaggedSentenceToFeatures = SentenceToFeature.convertTaggedSentenceToFeatures(s, true);
		convertTaggedSentenceToFeatures.values().forEach(p->System.err.println(p));
		
		//SentenceToFeature.featureMapToStringList(convertTaggedSentenceToFeatures).forEach(x->System.out.println(x));;
		
		
		//System.err.println("------------------------------------------");
		//convertTaggedSentenceToFeatures = SentenceToFeature.convertPlainSentenceToFeatures("he is president of my pope");
		//convertTaggedSentenceToFeatures.values().forEach(p->System.err.println(p));
		
		

	}
	
	public static String normalizeTaggedSentence(String line) {
		final Map<String,String> result = new HashMap<>();
		line = Global.removeRolePhraseTag(line);
		Pattern p = Pattern.compile("</?HR>");
		Matcher matcher = p.matcher(line);
		while(matcher.find()) {
			final String saltString = " "+CharactersUtils.getSaltString()+" ";
			line = line.replace(matcher.group(0),saltString);
			result.put(saltString, matcher.group(0));
		}
		
		line = CharactersUtils.normalizeTrainSentence(line);
		
		for(Entry<String, String> e:result.entrySet()) {
			line = line.replace(e.getKey(), e.getValue());
		}
		
		line = line.replaceAll("\\s+"," ");
		return line;
	}

}
