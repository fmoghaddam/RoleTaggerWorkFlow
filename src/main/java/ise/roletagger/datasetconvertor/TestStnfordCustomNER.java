package ise.roletagger.datasetconvertor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import ise.roletagger.util.CustomNERTagger;

/**
 * Run our external test data (currently from new york times) 
 * and report accuracy
 * @author fbm
 *
 */
@Deprecated
public class TestStnfordCustomNER {
	private static final String POSITIVE_DATA = "XXX";
	private static final String NEGATIVE_DATA = "XXX";

	public static void main(String[] args) throws IOException {
		File[] listOfFiles = new File(POSITIVE_DATA).listFiles();

		float tp = 0;
		float tn = 0;
		float fp = 0;
		float fn = 0;

		for(File f:listOfFiles) {
			final List<String> positiveLines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
			for(String posLine:positiveLines) {
				String result = CustomNERTagger.runTaggerString(posLine);
				if(result.contains("<ROLE>")) {
					tp++;
				}else {
					fn++;
				}
				
				
//				
//				final List<Tuple> tuples = TestModel.runCRF(noTaggedLine);
//				if(tuples.size()!=result.size()) {
//					throw new IllegalArgumentException("Size of the tuples and size of the result are not similar");
//				}
//				
//				for(int i=0;i<tuples.size();i++) {
//					final Tuple tuple = tuples.get(i);
//					final Map<String, String> map = result.get(i);
//					final String realTag = map.get("TAG");
//					final String predictaedTag = tuple.b;
//					
//					if(realTag.equalsIgnoreCase(predictaedTag) && realTag.equalsIgnoreCase("O")) {
//						trueNegative++;
//					}else if(realTag.equalsIgnoreCase(predictaedTag) && !realTag.equalsIgnoreCase("O")) {
//						truePositive++;
//					}
//					else {
//						if(realTag.equalsIgnoreCase("O")) {
//							falsePositive++;
//						}else {
//							falseNegative++;
//						}
//					}
//				}
				
				
			}
		}

		listOfFiles = new File(NEGATIVE_DATA).listFiles();
		for(File f:listOfFiles) {
			final List<String> negativeLines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
			for(String negLine:negativeLines) {
				String result = CustomNERTagger.runTaggerString(negLine);
				if(result.contains("<ROLE>")) {
					fp++;
				}else {
					tn++;
				}
			}
		}

		System.err.println("accuracy = "+(tp+tn)/(tp+tn+fp+fn));
		System.err.println("precision = "+(tp)/(tp+fp));
		System.err.println("recall = "+(tp)/(tp+fn));
	}
}
