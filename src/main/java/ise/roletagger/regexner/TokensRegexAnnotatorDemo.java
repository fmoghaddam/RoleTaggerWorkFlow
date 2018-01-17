package ise.roletagger.regexner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.MultiPatternMatcher;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.XMLOutputter;
import edu.stanford.nlp.util.CoreMap;
import ise.roletagger.util.DictionaryRegexPatterns;
import ise.roletagger.util.MyStanfordCoreNLP;

/**
 * Demo illustrating how to use TokensRegexAnnotator.
 */
public class TokensRegexAnnotatorDemo {

	public static void main(String[] args) throws IOException {
		//testWithPattern();
		demo();
	}

	private static void demo() throws FileNotFoundException {
		PrintWriter out;

		String rules;
		rules = "edu/stanford/nlp/ling/tokensregex/demo/rules/roles.rules.filter.txt";
		out = new PrintWriter(System.out);

		Properties properties = new Properties();
		properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,tokensregex");
		properties.setProperty("customAnnotatorClass.tokensregex", "edu.stanford.nlp.pipeline.TokensRegexAnnotator");
		properties.setProperty("tokensregex.rules", rules);
		StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
		Annotation annotation;
		
		
		@SuppressWarnings("resource")
		Scanner scanner = new Scanner(System.in);

		while(true) {
			String input = scanner.nextLine();
			//String input = "general";
			annotation = new Annotation(input);

			pipeline.annotate(annotation);

			out.println();
			out.println("The top level annotation");
			out.println(annotation.toShorterString());

			final nu.xom.Document doc = XMLOutputter.annotationToDoc(annotation, pipeline);
			final String runXXXX = MyStanfordCoreNLP.runRoleTaggerString(input, doc.toXML());
			
			System.err.println(runXXXX);
			List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
			for (CoreMap sentence : sentences) {
				for (CoreLabel token:sentence.get(CoreAnnotations.TokensAnnotation.class)) {
					String word = token.get(CoreAnnotations.TextAnnotation.class);
					String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
					String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
					String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
					String normalized = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
					out.println("token: " + "word="+word + ", lemma="+lemma + ", pos=" + pos + ", ne=" + ne + ", normalized=" + normalized);
				}
			}
			out.flush();
		}
	}

	private static void testWithPattern() {
		Properties properties = new Properties();
		properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,tokensregexdemo");
		properties.setProperty("customAnnotatorClass.tokensregexdemo", "edu.stanford.nlp.pipeline.TokensRegexAnnotator");
		properties.setProperty("tokensregexdemo.rules", "edu/stanford/nlp/ling/tokensregex/demo/rules/colors.rules.txt");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
		Annotation annotation;

		Scanner scanner = new Scanner(System.in);

		while(true) {
			String input = scanner.nextLine();
			annotation = new Annotation(input);
			pipeline.annotate(annotation);


			List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
			for (CoreMap sentence : sentences) {
				List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);


				String listOfHEadRole = getHeadRoles();

				//final String p1 = "([{pos:DT}]+ [{pos:JJ}]* /King|king/ [{pos:IN}]* [{ner:LOCATION}]* )";
				//final String p2 = "([{pos:DT}]+ [{pos:JJ}]* /President|president/ [{pos:IN}]* [{ner:LOCATION}]* )";

				final String fullPattern = "([{pos:DT}]+ [{pos:JJ}]* "+listOfHEadRole+" [{pos:IN}]* [{ner:LOCATION}]* )";

				//TokenSequencePattern pattern = TokenSequencePattern.compile(p1);
				//TokenSequenceMatcher matcher = pattern.getMatcher(tokens);

				final List<TokenSequencePattern> tokenSequencePatterns = Arrays.asList(TokenSequencePattern.compile(fullPattern));
				final MultiPatternMatcher multiMatcher = TokenSequencePattern.getMultiPatternMatcher(tokenSequencePatterns);
				final List<SequenceMatchResult<CoreMap>> x = multiMatcher.findNonOverlapping(tokens);


				for(int i=0;i<x.size();i++) {
					System.err.println(x.get(i).group());
				}



				//				while (matcher.find()) {
				//					String matchedString = matcher.group();
				//					List<CoreMap> matchedTokens = matcher.groupNodes();
				//					System.err.println(matchedString);
				//					System.err.println(matchedTokens);
				//				}
			}
		}
	}

	private static String getHeadRoles() {
		final StringBuilder result = new StringBuilder();
		result.append("/");
		final List<String> collect = DictionaryRegexPatterns.getDictionaries().getHeadRoleMap().values().stream()
				.flatMap(x -> x.stream()).collect(Collectors.toList());
		for(String role:collect) {
			result.append(role).append("|").append(role.toLowerCase()).append("|").append(role.toUpperCase()).append("|").append(role.substring(0, 1).toUpperCase() + role.substring(1)).append("|");
		}

		result.replace(result.length()-1, result.length()-1, "");
		result.append("/");

		return result.toString();
	}

}