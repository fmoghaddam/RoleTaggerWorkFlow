package ise.roletagger.pipeline;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ESWCrefactoring.BaseLineDictionaryEvaluationOnTrainSet;
import ESWCrefactoring.ConvertDatasetToFeatureSetForCRFSuite;
import ESWCrefactoring.DatasetUniqefier;
import ESWCrefactoring.GenerateDataSet;
import ise.roletagger.datasetconvertor.TaggerType;

public class PipeLine {

	private static ExecutorService executor = Executors.newSingleThreadExecutor();
	
	public static void main(String[] args) {
		
		try {

			/**Entity generation & Wikidata dictionary generation**/
			//executor.submit(RunRequest.run());
			
			/**Entity generation**/
			//executor.submit(new RunRequestOnlyPersons().run());
			
			/**Now we need to manually clean the dictionaries*/
			
			/**Dictionary Generation**/
			//executor.submit(DictionaryGenerator.run());
			
			/**Pure anchortext Dictionary Generation**/
			//executor.submit(PureAnchorText.run());
			
			/**Category Tree Generation**/
			//executor.submit(CategoryTreeMainGenerator.run());
			//executor.submit(new CategoryTreePostProccessing().run());
			
			/**Dictionary to regexNER**/
			//executor.submit(ConvertDictionaryToRegexNer.run());
			
			/**Dataset Generation**/
			//executor.submit(new DatasetGeneratorWithCategoryTrees().run());
			//executor.submit(DatasetGeneratorWithCategoryTreesV2.run());
			//executor.submit(new DatasetGeneratorWithCategoryTreesV3().run());
			//executor.submit(DatasetGeneratorWithCategoryTreesV5.run());
			executor.submit(GenerateDataSet.run());
			
			/**Dataset generation based on TokensRegex**/
			//executor.submit(TokensRegexAnnotator.run());
			
			/**Dataset uniquefier*/
			executor.submit(DatasetUniqefier.run());
			
			
			/**Dataset to feature set for CRF**/
			executor.submit(new ConvertDatasetToFeatureSetForCRFSuite(0).run());
			//executor.submit(new ConvertDatasetToFeatureSetForCRFSuite(TaggerType.ONLY_ROLE_PHRASE,0).run());
			//executor.submit(new ConvertDatasetToFeatureSetForCRFSuite(TaggerType.ONLY_ANCHOR_TEXT,0).run());
			
			//executor.submit(new ConvertDatasetToFeatureSetForCRFSuite(TaggerType.ONLY_HEAD_ROLE,0.2).run());
			//executor.submit(new ConvertDatasetToFeatureSetForCRFSuite(TaggerType.ONLY_ROLE_PHRASE,0.2).run());
			//executor.submit(new ConvertDatasetToFeatureSetForCRFSuite(TaggerType.ONLY_ANCHOR_TEXT,0.2).run());
			
			/**Feature Engineering*/
			//executor.submit(EliminateFeatures.run());
			
			/**Evaluation**/			
			//TaggerType howToTagGroundTruth = TaggerType.ONLY_HEAD_ROLE;
			//TaggerType whichModelUse = TaggerType.ONLY_ROLE_PHRASE;
			//CRFModels whichCRFModelUse = CRFModels.ONLY_HEAD_ROLE;
			//executor.submit(new GroundTruthStatistics(howToTagGroundTruth).run());
			//executor.submit(new BaseLineDictionaryEvaluationOnTrainSet(TaggerType.ONLY_HEAD_ROLE).run());
			//executor.submit(new CRFEvaluation(howToTagGroundTruth, whichCRFModelUse).run());
			
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			
			System.err.println("PIPE LINE IS DONE. Probably downloading result to local pc");
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

}


