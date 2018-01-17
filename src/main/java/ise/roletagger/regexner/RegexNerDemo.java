package ise.roletagger.regexner;

import java.util.Properties;
import java.util.Scanner;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.XMLOutputter;
import ise.roletagger.util.MyStanfordCoreNLP;

public class RegexNerDemo {
	public static void main(String[] args){
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, ner, regexner");
		props.put("regexner.mapping", "regexNer2.txt");
		props.put("regexner.ignorecase", "true");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		
		
		Annotation annotation;
		
		
		@SuppressWarnings("resource")
		Scanner scanner = new Scanner(System.in);

		while(true) {
			String input = scanner.nextLine();
			annotation = new Annotation(input);
			pipeline.annotate(annotation);
			final nu.xom.Document doc = XMLOutputter.annotationToDoc(annotation, pipeline);
			System.err.println(doc.toXML());
			System.err.println("----------------------------------------");
			System.err.println(MyStanfordCoreNLP.runRoleTaggerString(input, doc.toXML()));
		}
	}
}
