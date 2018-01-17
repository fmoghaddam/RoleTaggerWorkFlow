package ise.roletagger.baselines;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import ise.roletagger.model.AnchorText;
import ise.roletagger.model.Category;
import ise.roletagger.model.DataSourceType;
import ise.roletagger.model.Dictionary;
import ise.roletagger.model.Entity;
import ise.roletagger.model.HtmlLink;
import ise.roletagger.util.Config;
import ise.roletagger.util.DictionaryRegexPatterns;
import ise.roletagger.util.EntityFileLoader;
import ise.roletagger.util.HTMLLinkExtractor;

/**
 * This class gather all the anchor text form wikipedia
 * and without any normalization put them in a dictionary
 * and write them to separate folders under "ADDRESS_OF_ANCHORTEXT_DICTIONARY_FOLDER"
 * 
 * @author fbm
 *
 */
public class PureAnchorText {
	
	/**
	 * Parameters
	 * Dictionary generation configuration
	 * Which datasource?
	 * Which category?
	 */
	private static final DataSourceType ENTITY_DATA_SOURCE = DataSourceType.WIKIDATA_LIST_OF_PRESON;
	private static final Category ENTITY_DATA_SOURCE_CATEGORY = null;
	private static final String ADDRESS_OF_DICTIONARY_FOLDER = Config.getString("ADDRESS_OF_ANCHORTEXT_DICTIONARY_FOLDER","")+ENTITY_DATA_SOURCE.getText();

	//private static final Logger LOG = Logger.getLogger(PureAnchorText.class.getCanonicalName());
	private static final Dictionary DICTIONARY = new Dictionary();

	private static String WIKI_FILES_FOLDER = Config.getString("WIKI_FILES_FOLDER", "");
	private static int NUMBER_OF_THREADS = Config.getInt("NUMBER_OF_THREADS", 1);

	private static Map<String, Entity> entityMap;
	private static ExecutorService executor;

	private static Map<Category, List<Pattern>> headRolePatterns;
	
	public static void main(String[] args) {
		final Thread t = new Thread(run());
		t.setDaemon(false);
		t.start();
	}

	public static Runnable run() {
		final Runnable r = () -> {
			try {
				headRolePatterns = DictionaryRegexPatterns.getHeadRolePatterns();
				executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
				entityMap = EntityFileLoader.loadData(ENTITY_DATA_SOURCE,ENTITY_DATA_SOURCE_CATEGORY);
				checkWikiPages(WIKI_FILES_FOLDER);
			}catch(Exception e) {
				e.printStackTrace();
			}
		};
		return r;
	}

	private static void checkWikiPages(String WIKI_FILES_FOLDER) {
		try {
			final File[] listOfFolders = new File(WIKI_FILES_FOLDER).listFiles();
			Arrays.sort(listOfFolders);
			for (int i = 0; i < listOfFolders.length; i++) {
				final String subFolder = listOfFolders[i].getName();
				final File[] listOfFiles = new File(WIKI_FILES_FOLDER + File.separator + subFolder + File.separator)
						.listFiles();
				Arrays.sort(listOfFiles);
				for (int j = 0; j < listOfFiles.length; j++) {
					final String file = listOfFiles[j].getName();
					executor.execute(handle(
							WIKI_FILES_FOLDER + File.separator + subFolder + File.separator + File.separator + file));
				}
			}
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			DICTIONARY.printDictioanryToFilesBasedOnCategories(ADDRESS_OF_DICTIONARY_FOLDER);
			DICTIONARY.printResult();
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
	}

	private static Runnable handle(String pathToSubFolder) {
		return () -> {
			try {
				final BufferedReader br = new BufferedReader(new FileReader(pathToSubFolder));
				String line;
				while ((line = br.readLine()) != null) {
					if(!line.contains("<")) {
						continue;
					}
					final HTMLLinkExtractor htmlLinkExtractor = new HTMLLinkExtractor();
					final Vector<HtmlLink> links = htmlLinkExtractor.grabHTMLLinks(line);
					for (Iterator<?> iterator = links.iterator(); iterator.hasNext();) {
						final HtmlLink htmlLink = (HtmlLink) iterator.next();
						final Entity entity = entityMap.get(htmlLink.getUrl());
						if (entity != null) {
							final String linkText = htmlLink.getAnchorText().trim();
							if(containHeadRole(linkText,entity)) {
								if (linkText != null && !linkText.isEmpty()) {
									DICTIONARY.merge(new AnchorText(linkText), entity);
								}
							}
						}
					}
				}
				br.close();
				System.out.println("File " + pathToSubFolder + " has been processed.");
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
	}

	/**
	 * Check to see if a given text contains headRole or not.
	 * only specific category would be considered
	 * for example for an anchortext related to presidency we don't need to check 
	 * Pope headroles
	 * @param linkText
	 * @param entity
	 * @return
	 */
	private static boolean containHeadRole(String linkText, Entity entity) {
		final List<Pattern> headRoleList = headRolePatterns.get(entity.getCategoryFolder());
		for (final Pattern p : headRoleList) {
			final Matcher matcher = p.matcher(linkText);
			if (matcher.find()) {
				return true;
			}
		}
		return false;
	}
	
	protected static String removeFullNameAndEntityNameWordByWord(String anchorText, Entity entity) {
		String result = new String(anchorText);
		String[] split = result.toString().split(" ");
		StringBuilder linkTextRefactored = new StringBuilder();
		for (final String word : split) {
			if (entity.getEntityName().contains(word)) {
				continue;
			}
			if (entity.getName().contains(word)) {
				continue;
			} else {
				linkTextRefactored.append(word).append(" ");
			}
		}
		result = linkTextRefactored.toString();
		result = result.replaceAll("\\s+", " ");
		result = result.trim();
		return result;
	}
}
