package TEST;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import edu.stanford.nlp.process.Tokenizer;
public class TestTokenier {

	public static void main(String[] args) {
		StringReader s= new StringReader("my name is <HR>salam</HR> is:s");
		PTBTokenizerFactory<Word> factory = (PTBTokenizerFactory<Word>)PTBTokenizer.factory();
		  // Stanford's tokenizer actually changes words to American...altering our original text. Stop it!!
		  factory.setOptions("americanize=false");
		  Tokenizer<Word> tokenizer = factory.getTokenizer(new BufferedReader(s));
		  tokenizer.tokenize().forEach(p -> System.err.println(p));
	}

}
