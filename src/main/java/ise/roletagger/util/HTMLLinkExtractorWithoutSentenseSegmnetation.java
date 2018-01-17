package ise.roletagger.util;

import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.Word;
import ise.roletagger.model.Global;
import ise.roletagger.model.HtmlLink;
import ise.roletagger.model.NounPhrase;
import ise.roletagger.model.TagPosition;
import ise.roletagger.model.TagPositions;


public class HTMLLinkExtractorWithoutSentenseSegmnetation {

	private Pattern patternTag, patternLink;
	private Matcher matcherTag, matcherLink;

	private static final String HTML_A_TAG_PATTERN = "(?i)<a([^>]+)>(.+?)</a>";
	private static final String HTML_A_HREF_TAG_PATTERN =
			"\\s*(?i)href\\s*=\\s*(\"([^\"]*\")|'[^']*'|([^'\">\\s]+))";


	public HTMLLinkExtractorWithoutSentenseSegmnetation() {
		patternTag = Pattern.compile(HTML_A_TAG_PATTERN);
		patternLink = Pattern.compile(HTML_A_HREF_TAG_PATTERN);
	}

	public static String cleanAnchorTexts(String sentenceString) {
		return sentenceString.replaceAll("<[^>]*>", "");
	}
	/**
	 * Validate html with regular expression
	 *
	 * @param html
	 *            html content for validation
	 * @return Vector links and link text
	 */
	public Vector<HtmlLink> grabHTMLLinks(final String sentenceString) {
		Vector<HtmlLink> result = new Vector<HtmlLink>();
		final String sentenceWithoutHtmlTag = sentenceString.replaceAll("<[^>]*>", "");
		matcherTag = patternTag.matcher(sentenceString);

		while (matcherTag.find()) {

			String href = matcherTag.group(1); // href
			String linkText = matcherTag.group(2); // link text

			matcherLink = patternLink.matcher(href);

			while (matcherLink.find()) {

				String link = matcherLink.group(1); // link
				HtmlLink obj = new HtmlLink();
				obj.setUrl(link);
				obj.setAnchorText(linkText);
				obj.setFullSentence(sentenceWithoutHtmlTag);
				result.add(obj);
			}
		}
		return result;
	}
	
	/**
	 * Validate html with regular expression
	 * Try to find noun phrases and replace anchortext with noun phrase.
	 * This can fix following problem form page 18 google presentation ("RoleTagger Meeting 23.11.2017")
	 * President <a href=”barack_obama”>Obama</a>
	 * @param html
	 *            html content for validation
	 * @return Vector links and link text
	 */
	public Vector<HtmlLink> grabHTMLLinksFromNounPhrases(final String sentenceString) {
		Vector<HtmlLink> result = new Vector<HtmlLink>();
		final String sentenceWithoutHtmlTag = sentenceString.replaceAll("<[^>]*>", "");
		
		final List<NounPhrase> nounPhrases = MyStanfordCoreNLP.getAllHighLevelNounPhrases(sentenceWithoutHtmlTag);
		final List<Word> tokenizeWithoutTag = MyStanfordCoreNLP.tokenize(sentenceWithoutHtmlTag);
		final List<Word> tokenizeWithTag = MyStanfordCoreNLP.tokenize(sentenceString);
		final TagPositions anchorTextPositions = new TagPositions();
		int start = 0;
		int end = 0;
		int offset = 0;
		String url = null;
		for(int i=0;i<tokenizeWithTag.size();i++) {
			final Word word = tokenizeWithTag.get(i);
			if(word.toString().contains("<a href=")) {
				url =  word.toString();
				start = i-offset;
				offset++;
			}else if(word.toString().contains("</a>")) {
				end = (i-1)-offset;				
				anchorTextPositions.add(new TagPosition(url, start, end));
				start = 0;
				end = 0;
				offset++;
			}
		}
		
		
		//Converting nouinPhrase to anchortext
		for(final NounPhrase np:nounPhrases) {
			for(TagPosition p: anchorTextPositions.getPositions()) {
				if(np.getPosition().contains(p)){
					//StringBuilder sb = new StringBuilder();
					//for(int i=p.getStartIndex();i<=p.getEndIndex();i++) {
					//	sb.append(tokenizeWithoutTag.get(i)).append(" ");
					//}
					np.setText(p.getTag()+np.getText()+Global.getAnchorEndTag());
				}else if(p.contains(np.getPosition())) {
					StringBuilder sb = new StringBuilder();
					for(int i=p.getStartIndex();i<=p.getEndIndex();i++) {
						sb.append(tokenizeWithoutTag.get(i)).append(" ");
					}
					np.setText(p.getTag()+sb.toString().trim()+Global.getAnchorEndTag());
				}else if(p.hasOverlap(np.getPosition())) {
					System.err.println("I DONT KNOW WHAT TO DO");
				}else {
					//ignore
				}
			}
		}
		
		
		for(NounPhrase np:nounPhrases) {
			final String npWithoutHtmlTag = np.getText().replaceAll("<[^>]*>", "");
			matcherTag = patternTag.matcher(np.getText());

			while (matcherTag.find()) {

				String href = matcherTag.group(1); // href
				//String linkText = matcherTag.group(2); // link text

				matcherLink = patternLink.matcher(href);

				while (matcherLink.find()) {
					String link = matcherLink.group(1); // link
					HtmlLink obj = new HtmlLink();
					obj.setUrl(link);
					obj.setAnchorText(npWithoutHtmlTag);
					obj.setFullSentence(sentenceWithoutHtmlTag);
					result.add(obj);
				}
			}
		}
		return result;
	}

}


