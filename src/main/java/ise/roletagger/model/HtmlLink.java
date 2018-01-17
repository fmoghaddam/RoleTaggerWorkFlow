package ise.roletagger.model;

import ise.roletagger.util.URLUTF8Encoder;

public class HtmlLink {

	String url;
	String anchorText;
	String fullSentence;

	public HtmlLink(){};

	public void setFullSentence(String sentenceWithoutHtmlTag) {
		fullSentence = sentenceWithoutHtmlTag;
	}

	@Override
	public String toString() {
		return "HtmlLink [url=" + url + ", anchorText=" + anchorText + ", fullSentence=" + fullSentence + "]";
	}

	public String getUrl() {
		return URLUTF8Encoder.decodeJavaNative(url);
	}

	public String getFullSentence(){
		return fullSentence;
	}

	public void setUrl(String link) {
		this.url = replaceInvalidChar(link);
	}

	public String getAnchorText() {
		return new String(anchorText);
	}

	public void setAnchorText(String linkText) {
		this.anchorText = linkText;
	}

	private String replaceInvalidChar(String link){
		link = link.replaceAll("'", "");
		link = link.replaceAll("\"", "");
		return link;
	}
}