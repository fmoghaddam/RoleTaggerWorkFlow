package ise.roletagger.featureengineering;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ise.roletagger.util.Config;
import ise.roletagger.util.FileUtil;

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
public class EliminateFeatures {

	private final static int CHUNKING_SIZE = 1;
	private final static int NUMBER_OF_THREADS = Config.getInt("NUMBER_OF_THREADS", 0);

	
	private static final List<String> result = new ArrayList<>();

	private static final String FEATURES_SET_PATH = Config.getString("WHERE_TO_WRITE_CRF_DATASET", "")+File.separator+"trainONLY_HEAD_ROLE0.0";

	public static void main(String[] args) throws IOException {
	}

	public static Runnable run() {
		return () -> {
			try {
				try {
					System.err.println("Reading....");
					final List<String> postiveData = Files.readAllLines(Paths.get(FEATURES_SET_PATH),
							StandardCharsets.UTF_8);
					System.err.println("Reading done.");
					final ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
					for (int i = 0; i < postiveData.size(); i = Math.min(i + CHUNKING_SIZE, postiveData.size())) {
						executor.execute(handle(postiveData, i, Math.min(i + CHUNKING_SIZE, postiveData.size())));
					}
					executor.shutdown();
					executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
					FileUtil.writeDataToFile(result, FEATURES_SET_PATH+"NoInDic", false);
					result.clear();
				} catch (final Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
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
	private static Runnable handle(List<String> data, int start, int end) {
		final Runnable r = () -> {
			int counter =0;
			final List<LinkedHashMap<Integer, LinkedHashMap<String, String>>> input = new ArrayList<LinkedHashMap<Integer,LinkedHashMap<String,String>>>();
			final LinkedHashMap<Integer, LinkedHashMap<String, String>> map = new LinkedHashMap<>();
			for (int i = start; i < end; i++) {
				String line = data.get(i);
				if(line.isEmpty()) {
					if(!map.isEmpty()){
						input.add(new LinkedHashMap<>(map));
						map.clear();
						counter=0;
					}
				}else {
					final LinkedHashMap<String, String> localMap = new LinkedHashMap<>();
					final String[] split = line.split("\t");

					localMap.put("TAG", split[0]);

					for(int k=1;k<split.length;k++) {
						final int indexOf = split[k].indexOf("=");
						final String key = split[k].substring(0, indexOf);
						final String value = split[k].substring(indexOf+1, split[k].length());
						localMap.put(key, value);
					}
					map.put(counter, localMap);
					counter++;
				}
			}

			for(Map<Integer, LinkedHashMap<String, String>> e:input) {
				result.add("\n");
				for(int i=0;i<e.size();i++) {
					StringBuilder res = new StringBuilder();
					for(Entry<String, String> ee:e.get(i).entrySet()) {
						if(ee.getKey().equals("TAG")) {
							res.append(ee.getValue()).append("\t");
						}
						else if(!ee.getKey().equals("IsInDic")) {
							res.append(ee.getKey()+"="+ee.getValue()).append("\t");
						} 
					}
					result.add(res.toString().trim());
				}
			}
			System.err.println(
					"Thread "+ ((start / CHUNKING_SIZE) + 1) + " is done");
		};
		return r;
	}
}
