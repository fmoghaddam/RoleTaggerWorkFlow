package ise.roletagger.regexner;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;

import ise.roletagger.model.Category;
import ise.roletagger.util.DictionaryRegexPatterns;
import ise.roletagger.util.HTMLLinkExtractorWithoutSentenseSegmnetation;

public class Test2 {

	public static void main(String[] args) {
		
//		TokensRegexAnnotator.check("Tsar Alexander II of Russia visited King Carlos I of Portugal in Iran");
		
//		System.err.println(HTMLLinkExtractorWithoutSentenseSegmnetation.cleanAnchorTexts("<a href=\"fasadasdad\">he s</a>"));
		
//		System.err.println(StringUtils.difference("He is presient of US and good", "He is <ROLE> and good"));
//		diff_match_patch dmp = new diff_match_patch();
//		final LinkedList<Diff> diff_main = dmp.diff_main("he is king of Iran", "he is <R> of <R>");
//		for(Diff diff: diff_main) {
//			if(diff.operation.equals(Operation.DELETE)) {
//				System.err.println(diff);
//			}
//			final Operation operation = diff.operation;
//		}
		
		final Map<Category, LinkedHashSet<String>> categoryToRolePatterns = DictionaryRegexPatterns.getDictionaries().getInverseData();
		for(Entry<Category, LinkedHashSet<String>> e:categoryToRolePatterns.entrySet()) {
			StringBuilder s = new StringBuilder();
			for(String p : e.getValue()) {
				s.append(addSlash(p)).append("|").append("\n");
			}
			s = new StringBuilder(s.toString().replace(s.toString(),s.toString().substring(0,s.length()-1)));
			System.err.println(s.toString());
		}
	}

	private static String addSlash(String p) {
		StringBuilder result = new StringBuilder();
		result.append("/");
		result.append(p.replace(" ", "/ /"));
		result.append("/");
		return result.toString();
	}
}
