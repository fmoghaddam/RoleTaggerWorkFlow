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

import ise.roletagger.util.Config;
import ise.roletagger.util.FileUtil;

public class EliminateFeaturesOneThread {


	private static final List<LinkedHashMap<Integer, LinkedHashMap<String, String>>> input = new ArrayList<LinkedHashMap<Integer,LinkedHashMap<String,String>>>();
	private static final List<String> result = new ArrayList<>();

	private static final String FEATURES_SET_PATH = Config.getString("WHERE_TO_WRITE_CRF_DATASET", "")+File.separator+"trainONLY_HEAD_ROLE0.0";

	public static void main(String[] args) throws IOException {
		final Thread t = new Thread(run());
		t.setDaemon(false);
		t.start();
	}

	public static Runnable run() {
		return () -> {
			try {
				final List<String> lines = Files.readAllLines(Paths.get(FEATURES_SET_PATH),
						StandardCharsets.UTF_8);
				int counter =0;

				final LinkedHashMap<Integer, LinkedHashMap<String, String>> map = new LinkedHashMap<>();
				for(String line:lines) {
					//System.err.println(line);
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

						for(int i=1;i<split.length;i++) {
							final int indexOf = split[i].indexOf("=");
							final String key = split[i].substring(0, indexOf);
							final String value = split[i].substring(indexOf+1, split[i].length());
							localMap.put(key, value);
						}
						map.put(counter, localMap);
						counter++;
					}
				}

				for(Map<Integer, LinkedHashMap<String, String>> e:input) {
					result.add("\n\n");
					for(int i=0;i<e.size();i++) {
						StringBuilder r = new StringBuilder();
						for(Entry<String, String> ee:e.get(i).entrySet()) {
							if(ee.getKey().equals("TAG")) {
								r.append(ee.getValue()).append("\t");
							}
							else if(!ee.getKey().equals("IsInDic")) {
								r.append(ee.getKey()+"="+ee.getValue()).append("\t");
							} 
						}
						result.add(r.toString().trim());
					}
				}

				FileUtil.writeDataToFile(result, FEATURES_SET_PATH+"NoInDic", false);
			}catch (Exception e) {
				e.printStackTrace();
			}
		};
	}

}
