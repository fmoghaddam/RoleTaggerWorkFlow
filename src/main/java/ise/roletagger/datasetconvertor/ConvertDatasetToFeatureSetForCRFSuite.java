package ise.roletagger.datasetconvertor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ise.roletagger.evaluationtokenbased.SentenceToFeature;
import ise.roletagger.model.Global;
import ise.roletagger.model.TrainTestData;
import ise.roletagger.util.Config;
import ise.roletagger.util.FileUtil;
import ise.roletagger.util.MyStandfordCoreNLPRegex;

/**
 * This class convert dataset files to a structure which CRFSuite CRF can read
 * for training
 * 
 * This version works with output of
 * {@link DatasetGeneratorWithCategoryTrees4thVersion}}
 * 
 * @author fbm
 *
 */
public class ConvertDatasetToFeatureSetForCRFSuite {

	/**
	 * Which part of the sentence should be considered as "ROLE"?
	 */
	private TaggerType TP = TaggerType.ONLY_HEAD_ROLE;
	/**
	 * Name of test/train data file which will be produced
	 */
	private final String TEST_TXT;
	private final String TRAIN_TXT;
	
	private final String WHERE_TO_WRITE_CRF_DATASET = Config.getString("WHERE_TO_WRITE_CRF_DATASET", "");

	private final int CHUNKING_SIZE = 1000;
	private final int NUMBER_OF_THREADS = Config.getInt("NUMBER_OF_THREADS", 0);

	private final String POSITIVE_DATA_ADDRESS = Config.getString("WHERE_TO_WRITE_DATASET", "")
			+ Config.getString("POSITIVE_UNIQUE_DATASET_NAME", "");
	private final String NEGATIVE_DATA_ADDRESS = Config.getString("WHERE_TO_WRITE_DATASET", "")
			+ Config.getString("DIFFICULT_UNIQUE_NEGATIVE_DATASET_NAME", "");
	/**
	 * How much data should be considered as test data?
	 */
	private double TEST_PERCENTAGE = 0.2f;
	private final List<String> finalResult = Collections.synchronizedList(new ArrayList<>());

	/**
	 * With seed
	 */
	private final Random RAND = new Random(1);

	public ConvertDatasetToFeatureSetForCRFSuite(TaggerType tp ,double d) {
		TP = tp;
		TEST_PERCENTAGE = d;
		TEST_TXT = "test"+TP.name()+String.valueOf(d);
		TRAIN_TXT = "train"+TP.name()+String.valueOf(d);
	}
	
	public static void main(String[] args) throws IOException {
		final Thread t = new Thread(new ConvertDatasetToFeatureSetForCRFSuite(TaggerType.ONLY_HEAD_ROLE,0.).run());
		t.setDaemon(false);
		t.start();
	}

	public Runnable run() {
		return () -> {
			try {
				final List<String> positiveLines = Files.readAllLines(Paths.get(POSITIVE_DATA_ADDRESS),
						StandardCharsets.UTF_8);
				final List<String> negativeLines = Files.readAllLines(Paths.get(NEGATIVE_DATA_ADDRESS),
						StandardCharsets.UTF_8);

				final TrainTestData positiveTTD = sampleData(positiveLines, TEST_PERCENTAGE);
				final TrainTestData negativeTTD = sampleData(negativeLines, TEST_PERCENTAGE);

				System.err.println(positiveTTD.getTrainSet().size());
				System.err.println(positiveTTD.getTestSet().size());

				System.err.println(negativeTTD.getTrainSet().size());
				System.err.println(negativeTTD.getTestSet().size());

				generateFullDataset(positiveTTD.getTrainSet(), negativeTTD.getTrainSet(), true);
				generateFullDataset(positiveTTD.getTestSet(), negativeTTD.getTestSet(), false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
	}

	/**
	 * Run in parallel Convert data to the features set which can be used by
	 * CRFSuite First chunk data and pass each chunk to each thread Can be used for
	 * converting train or test data by {@code isTrain}
	 * 
	 * @param postiveData
	 * @param negativeData
	 * @param isTrain
	 */
	private void generateFullDataset(List<String> postiveData, List<String> negativeData, boolean isTrain) {
		try {
			final ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
			for (int i = 0; i < postiveData.size(); i = Math.min(i + CHUNKING_SIZE, postiveData.size())) {
				executor.execute(handle(postiveData, i, Math.min(i + CHUNKING_SIZE, postiveData.size()), true));
			}
			for (int i = 0; i < negativeData.size(); i = Math.min(i + CHUNKING_SIZE, negativeData.size())) {
				executor.execute(handle(negativeData, i, Math.min(i + CHUNKING_SIZE, negativeData.size()), false));
			}
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			FileUtil.createFolder(WHERE_TO_WRITE_CRF_DATASET);
			FileUtil.writeDataToFile(finalResult, WHERE_TO_WRITE_CRF_DATASET + (isTrain ? TRAIN_TXT : TEST_TXT), false);
			finalResult.clear();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns a runnable for processing chunk of data. What this function does is
	 * converting data to features set to be used in CRFSuite
	 * 
	 * @param data
	 * @param start
	 * @param end
	 * @param isPositive
	 * @return
	 */
	private Runnable handle(List<String> data, int start, int end, boolean isPositive) {
		final Runnable r = () -> {
			for (int i = start; i < end; i++) {
				String line = data.get(i);
				final String taggedLine = line;
				
				if (Global.USE_NORMALIZATION ) {
					final String taggedWithRandomString = MyStandfordCoreNLPRegex.normalizeTaggedLine(taggedLine);
					final Map<Integer, Map<String, String>> result = SentenceToFeature.convertTaggedSentenceToFeatures(
							taggedWithRandomString, TP, isPositive);
					finalResult.addAll(SentenceToFeature.featureMapToStringList(result));
				}
				else {
					final Map<Integer, Map<String, String>> result = SentenceToFeature.convertTaggedSentenceToFeatures(taggedLine, TP, isPositive);
					finalResult.addAll(SentenceToFeature.featureMapToStringList(result));
				}
			}
			System.err.println(
					"Thread " + (isPositive ? "positive " : "negative ") + ((start / CHUNKING_SIZE) + 1) + " is done");
		};
		return r;
	}
	
	private TrainTestData sampleData(List<String> lines, double threshold) {
		int total = lines.size();
		int trainSize = (int) (total * (1 - threshold));
		final Set<Integer> indexes = new HashSet<>();
		while (indexes.size() < trainSize) {
			indexes.add((int) (RAND.nextFloat() * total));
		}
		final List<String> train = new ArrayList<>();
		final List<String> test = new ArrayList<>();

		for (int i : indexes) {
			train.add(lines.get(i));
		}

		for (int i = 0; i < total; i++) {
			if (!indexes.contains(i)) {
				test.add(lines.get(i));
			}
		}

		return new TrainTestData(train, test);
	}

}
