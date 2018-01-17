package ise.roletagger.regexner;

import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.XMLOutputter;
import ise.roletagger.model.NounPhrase;
import ise.roletagger.util.MyStanfordCoreNLP;

public class Test {
	private static StanfordCoreNLP pipeline;

	private static final String rules = "edu/stanford/nlp/ling/tokensregex/demo/rules/roles.rules.txt";
	private static final Properties properties = new Properties();
	static {
		properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,tokensregexdemo");
		properties.setProperty("customAnnotatorClass.tokensregexdemo",
				"edu.stanford.nlp.pipeline.TokensRegexAnnotator");
		properties.setProperty("tokensregexdemo.rules", rules);
		pipeline = new StanfordCoreNLP(properties);
	}

	public static void main(String[] args) {


		Scanner sc = new Scanner(System.in);
		System.err.println("start");
		while (true) {
			String text = sc.nextLine();
			if(text.isEmpty()) {
				continue;
			}
			System.err.println(MyStanfordCoreNLP.runNerTaggerXML(text));
			//final List<NounPhrase> allHighLevelNounPhrases = MyStanfordCoreNLP.getAllHighLevelNounPhrases(text);
			//for(NounPhrase np:allHighLevelNounPhrases) {
			//String line = np.getText();
			String line = text;
			Annotation annotation;
			final String sentenceWithoutHtmlTag = line.replaceAll("<[^>]*>", "");
			annotation = new Annotation(sentenceWithoutHtmlTag);
			pipeline.annotate(annotation);

			final nu.xom.Document doc = XMLOutputter.annotationToDoc(annotation, pipeline);

			final String runXXXX = MyStanfordCoreNLP.runRoleTaggerString(sentenceWithoutHtmlTag, doc.toXML());
			if(runXXXX.contains("ROLE")) {
				System.err.println(sentenceWithoutHtmlTag);
				System.err.println(runXXXX);
				System.err.println("-----------------------------------------------");
			}
			//}
		}
	}

}
