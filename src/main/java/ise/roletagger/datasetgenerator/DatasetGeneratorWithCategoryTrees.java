package ise.roletagger.datasetgenerator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.map.HashedMap;

import ise.roletagger.model.Category;
import ise.roletagger.model.CategoryTrees;
import ise.roletagger.model.DataSourceType;
import ise.roletagger.model.Dataset;
import ise.roletagger.model.Document;
import ise.roletagger.model.Entity;
import ise.roletagger.model.HtmlLink;
import ise.roletagger.model.RoleListProvider;
import ise.roletagger.util.Config;
import ise.roletagger.util.DictionaryRegexPatterns;
import ise.roletagger.util.EntityFileLoader;
import ise.roletagger.util.HTMLLinkExtractorWithoutSentenseSegmnetation;
import ise.roletagger.util.MapUtil;

/**
 * Generates positive, difficult negative and easy negative samples. This
 * version consider all the roles in a sentence and decide if sentence only
 * contains negative samples or contains positive sample. We need to ignore
 * sentences which contain both cases (e.g. Alexander Pope talked with Pope
 * Francis)
 * 
 * In this version I consider not only the anchor texts, but also normal
 * sentence to extract positive samples. So for example if I am in the
 * Barack_Obama page, then probably all the "president" words are positive
 * 
 * Moreover this version tries to keep all the roles annotated in one sentence
 * and then add it to dataset.
 * 
 * @author fbm
 *
 */
public class DatasetGeneratorWithCategoryTrees {

	/**
	 * This contains the positive and negative samples
	 */
	private static final Dataset DATASET = new Dataset();
	/**
	 * This folder contains all the wikipedia pages which are already cleaned by a
	 * python code from https://github.com/attardi/wikiextractor and contains the
	 * links and anchor text
	 */
	private static String WIKI_FILES_FOLDER = Config.getString("WIKI_FILES_FOLDER", "");
	/**
	 * This file is a dump which contains relation between each entity and its
	 * category entity dbp:subject category
	 */
	private static String ENTITY_CATEGORY_FILE = Config.getString("ENTITY_CATEGORY_FILE", "");
	/**
	 * This folder files related to category trees which are already calculated as a
	 * preprocess by my another project named "CategoryTreeGeneration"
	 * https://github.com/fmoghaddam/CategoryTreeGeneration
	 */
	private static String CATEGORY_TREE_FOLDER = Config.getString("CATEGORY_TREE_FOLDER_CLEAN", "");
	/**
	 * Number of thread for parallelization
	 */
	private static int NUMBER_OF_THREADS = Config.getInt("NUMBER_OF_THREADS", 1);
	/**
	 * Contains mapping between urls and entities. It will be loaded based on
	 * "data/entities" folder These are the seed as input list (persons,
	 * titles,...)
	 */
	private static Map<String, Entity> entityMap;
	private static ExecutorService executor;
	/**
	 * Contains mapping between entities and their category It uses dbpedia dump. It
	 * uses dct:subject
	 */
	private static EntityToListOfCategories entityToCategoryList;
	/**
	 * Contains category treeS
	 */
	private static final CategoryTrees categoryTrees = new CategoryTrees();

	private static Map<Category, Pattern> patterns = new HashedMap<>();
	private static Pattern pattern;
	/**
	 * Contains mapping between each role and their categories
	 */
	private static final TreeMap<String, Set<Category>> regexTextToCategories = new TreeMap<>(
			String.CASE_INSENSITIVE_ORDER);
	/**
	 * Reads already calculated dictionary of roles from folder "dictionary/manually
	 * cleaned"
	 */
	private static RoleListProvider dictionaries;

	private static final Map<String, Integer> usedEntityFromDictionary = new ConcurrentHashMap<>();

	private static final Logger LOG = Logger.getLogger(DatasetGeneratorWithCategoryTrees.class.getSimpleName());

	public static void main(String[] args) {

//		NUMBER_OF_THREADS = Integer.parseInt(args[0]);
//		WIKI_FILES_FOLDER = args[1];
//		ENTITY_CATEGORY_FILE = args[2];

		final Thread t = new Thread(run());
		t.setDaemon(false);
		t.start();
	}

	public static Runnable run() {
		return () -> {
			System.out.println("Loading skos category trees....");
			categoryTrees.load(CATEGORY_TREE_FOLDER);

			executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

			System.out.println("Loading seeds(list of persons, wikidata)....");
			entityMap = EntityFileLoader.loadData(DataSourceType.WIKIDATA_LIST_OF_PRESON, null);
			entityMap.putAll(EntityFileLoader.loadData(DataSourceType.WIKIPEDIA_LIST_OF_PERSON_MANUAL, null));

			System.out.println("Loading extracted roles (dictionaries)....");
			dictionaries = DictionaryRegexPatterns.getDictionaries();

			/**
			 * Contains all the roles in one regex The roles are coming for precalculated
			 * dictionaries
			 */
			final StringBuilder ceoRegexPattern = new StringBuilder();
			ceoRegexPattern.append("(?im)");

			final StringBuilder monarchRegexPattern = new StringBuilder();
			monarchRegexPattern.append("(?im)");

			final StringBuilder presidentRegexPattern = new StringBuilder();
			presidentRegexPattern.append("(?im)");

			final StringBuilder popeRegexPattern = new StringBuilder();
			popeRegexPattern.append("(?im)");

			for (Entry<Category, LinkedHashSet<String>> roleEntity : dictionaries.getInverseData().entrySet()) {
				switch (roleEntity.getKey()) {
				case CHAIR_PERSON_TAG:
					boolean ceoFirst = true;
					for (String role : roleEntity.getValue()) {
						if (role.charAt(0) == '<' && role.charAt(role.length() - 1) == '>') {
							continue;
						}
						if (ceoFirst) {
							ceoFirst = false;
							ceoRegexPattern.append("(\\b").append(role).append("\\b)");
						} else {
							ceoRegexPattern.append("|").append("(\\b").append(role).append("\\b)");
						}
					}
					break;
				case MONARCH_TAG:
					boolean monarchFirst = true;
					for (String role : roleEntity.getValue()) {
						if (role.charAt(0) == '<' && role.charAt(role.length() - 1) == '>') {
							continue;
						}
						if (monarchFirst) {
							monarchFirst = false;
							monarchRegexPattern.append("(\\b").append(role).append("\\b)");
						} else {
							monarchRegexPattern.append("|").append("(\\b").append(role).append("\\b)");
						}
					}
					break;
				case POPE_TAG:
					boolean popeFirst = true;
					for (String role : roleEntity.getValue()) {
						if (role.charAt(0) == '<' && role.charAt(role.length() - 1) == '>') {
							continue;
						}
						if (popeFirst) {
							popeFirst = false;
							popeRegexPattern.append("(\\b").append(role).append("\\b)");
						} else {
							popeRegexPattern.append("|").append("(\\b").append(role).append("\\b)");
						}
					}
					break;
				case HEAD_OF_STATE_TAG:
					boolean presidentFirst = true;
					for (String role : roleEntity.getValue()) {
						if (role.charAt(0) == '<' && role.charAt(role.length() - 1) == '>') {
							continue;
						}
						if (presidentFirst) {
							presidentFirst = false;
							presidentRegexPattern.append("(\\b").append(role).append("\\b)");
						} else {
							presidentRegexPattern.append("|").append("(\\b").append(role).append("\\b)");
						}
					}
					break;
				default:
					throw new IllegalArgumentException();
				}
			}

			final StringBuilder regexPattern = new StringBuilder();
			regexPattern.append("(?im)");
			boolean first = true;
			for (final Entry<String, Set<Category>> roleEntity : dictionaries.getData().entrySet()) {
				final Set<Category> categories = roleEntity.getValue();
				final String originalrole = roleEntity.getKey();
				final String role = originalrole.replaceAll("\\.", "\\\\.");
				if (role.charAt(0) == '<' && role.charAt(role.length() - 1) == '>') {
					continue;
				}
				if (first) {
					first = false;
					regexPattern.append("(\\b").append(role).append("\\b)");
				} else {
					regexPattern.append("|").append("(\\b").append(role).append("\\b)");
				}
				regexTextToCategories.put(originalrole, categories);
			}
			pattern = Pattern.compile(regexPattern.toString());

			patterns.put(Category.CHAIR_PERSON_TAG, Pattern.compile(ceoRegexPattern.toString()));
			patterns.put(Category.HEAD_OF_STATE_TAG, Pattern.compile(presidentRegexPattern.toString()));
			patterns.put(Category.MONARCH_TAG, Pattern.compile(monarchRegexPattern.toString()));
			patterns.put(Category.POPE_TAG, Pattern.compile(popeRegexPattern.toString()));

			System.out.println("Extracting mapping between entites and categories....");
			entityToCategoryList = new EntityToListOfCategories(ENTITY_CATEGORY_FILE);
			entityToCategoryList.parse();

			System.out.println("Start....");
			checkWikiPages();
		};
	}

	private static void checkWikiPages() {
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

			//DATASET.printPositiveDatasetStatistic();
			DATASET.printPositiveDataset();

			//DATASET.printNegativeDatasetStatistic();
			DATASET.printNegativeDatasetDifficult();

			DATASET.printNegativeDatasetEasy();

			printDictionaryUsageStatisitcs();
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
	}

	private static void printDictionaryUsageStatisitcs() {
		System.err.println("TOTAL DIC SIZE = " + dictionaries.getData().size());
		System.err.println("USED ENTITIES FROM DIC = " + usedEntityFromDictionary.size());
		System.err
		.println("PERCENTAGE = " + (usedEntityFromDictionary.size() * 100.) / (dictionaries.getData().size()));

		final Map<String, Integer> sortByValueDescending = MapUtil.sortByValueDescending(usedEntityFromDictionary);
		for (Entry<String, Integer> e : sortByValueDescending.entrySet()) {
			LOG.info(e.getKey() + "\t" + e.getValue());
			System.err.println(e.getKey() + "\t" + e.getValue());
		}
	}

	private static Runnable handle(String pathToSubFolder) {
		final Runnable r = () -> {
			try {
				final List<Document> documents = getDocuments(pathToSubFolder);
				for (final Document document : documents) {
					final Entity entity = entityMap.get(document.getTitle().replace(" ", "%20"));
					/**
					 * This is a normal wikipedia page
					 */
					if (entity == null) {
						for (final String line : document.getSentences()) {
							if (line.contains("<a href")) {
								handlelineWithAnchorText(line);
							}
						}
					}
					/**
					 * This is a important wikipedia page
					 * because it is in our important wikipeida pages list such as
					 * Barack_Obama, Queen Elizabeth, ...
					 */
					else {
						for (final String line : document.getSentences()) {
							if (line.contains("<a href")) {
								handlelineWithAnchorText(line);
							} else {
								handlelineWithoutAnchorText(line, entity);
							}
						}
					}
				}
				System.out.println("File " + pathToSubFolder + " has been processed.");
			} catch (Exception e) {
				e.printStackTrace();
			}
		};
		return r;
	}

	/**
	 * This function gets a sentence which does not have any anchor text but we
	 * think that it can be positive
	 * 
	 * @param line
	 * @param entity
	 */
	private static void handlelineWithoutAnchorText(String line, Entity entity) {
		Matcher matcher = null;
		switch (entity.getCategoryFolder()) {
		case CHAIR_PERSON_TAG:
			matcher = patterns.get(Category.CHAIR_PERSON_TAG).matcher(line);
			break;
		case HEAD_OF_STATE_TAG:
			matcher = patterns.get(Category.HEAD_OF_STATE_TAG).matcher(line);
			break;
		case MONARCH_TAG:
			matcher = patterns.get(Category.MONARCH_TAG).matcher(line);
			break;
		case POPE_TAG:
			matcher = patterns.get(Category.POPE_TAG).matcher(line);
			break;
		default:
			throw new IllegalArgumentException();
		}

		StringBuilder fullSentence = new StringBuilder(line);

		int offset = 0;
		boolean found = false;
		try {
			while (matcher.find()) {
				found = true;
				String foundText = matcher.group();
				int end = matcher.end();
				int start = matcher.start();
				handleDictionaryMap(foundText);

				fullSentence = fullSentence.replace(start + offset, end + offset,
						"<r>" + fullSentence.substring(start + offset, end + offset) + "</r>");

				offset += 7;
			}
			if (found) {
				DATASET.addPositiveData(Category.CHAIR_PERSON_TAG, fullSentence.toString(), fullSentence.toString());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Count number of time an element from dictionary has been used
	 * 
	 * @param foundText
	 */
	private static void handleDictionaryMap(String foundText) {
		final Integer integer = usedEntityFromDictionary.get(foundText);
		if (integer == null) {
			usedEntityFromDictionary.put(foundText, 1);
		} else {
			usedEntityFromDictionary.put(foundText, integer + 1);
		}
	}

	/**
	 * This is exactly same implementation as before we had in
	 * {@link DatasetGeneratorWithCategoryTrees2ndVersion} get a sentence and
	 * decided if it is positive, easyNegative or difficult negative
	 * 
	 * @param line
	 */
	private static void handlelineWithAnchorText(String line) {
		try {
			final HTMLLinkExtractorWithoutSentenseSegmnetation htmlLinkExtractor = new HTMLLinkExtractorWithoutSentenseSegmnetation();
			final Vector<HtmlLink> links = htmlLinkExtractor.grabHTMLLinks(line);
			final Set<Boolean> decisionCase = new HashSet<>();

			String originalSentence = new String(line);

			boolean firstTime = true;
			for (final Iterator<?> iterator = links.iterator(); iterator.hasNext();) {
				final HtmlLink htmlLink = (HtmlLink) iterator.next();
				if (firstTime) {
					originalSentence = new String(htmlLink.getFullSentence());
					firstTime = false;
				}

				String url = htmlLink.getUrl();
				final String anchorText = htmlLink.getAnchorText();
				final Entity entity = entityMap.get(url);
				
				url = java.net.URLDecoder.decode(url,"UTF-8");
				url = url.replaceAll(" ", "_");

				/**
				 * If anchor text refer to any link in the list and anchor-text contains any
				 * role, It is positive sample
				 */
				if (entity != null) {
					Matcher matcher1 = null;
					switch (entity.getCategoryFolder()) {
					case CHAIR_PERSON_TAG:
						matcher1 = patterns.get(Category.CHAIR_PERSON_TAG).matcher(anchorText);
						break;
					case HEAD_OF_STATE_TAG:
						matcher1 = patterns.get(Category.HEAD_OF_STATE_TAG).matcher(anchorText);
						break;
					case MONARCH_TAG:
						matcher1 = patterns.get(Category.MONARCH_TAG).matcher(anchorText);
						break;
					case POPE_TAG:
						matcher1 = patterns.get(Category.POPE_TAG).matcher(anchorText);
						break;
					default:
						throw new IllegalArgumentException("Entity category is no matching with our exising categories");
					}
					String linktext1 = htmlLink.getAnchorText();
					if (matcher1.find()) {
						handleDictionaryMap(matcher1.group());
						linktext1 = linktext1.replace(matcher1.group(), "<r>" + matcher1.group() + "</r>");
						originalSentence = originalSentence.replace(htmlLink.getAnchorText(), "<a>" + linktext1 + "</a>");
						decisionCase.add(true);
					}
				} else {
					final Matcher matcher2 = pattern.matcher(anchorText);
					if (matcher2.find()) {
						String linktext2 = htmlLink.getAnchorText();

						final Set<String> categoriesOfEntity = entityToCategoryList.getEntity2categories().get(url);
						if (categoriesOfEntity == null) {
							continue;
						}

						boolean negativeFlag = true;
						Category existInAnyTree = null;
						for (String cat1 : categoriesOfEntity) {
							existInAnyTree = categoryTrees.existInAnyTree(cat1);
							if (existInAnyTree != null) {
								negativeFlag = false;
								break;
							}
						}
						if (negativeFlag) {
							String foundText = matcher2.group();
							linktext2 = linktext2.replace(foundText, "<r>" + foundText + "</r>");
							originalSentence = originalSentence.replace(htmlLink.getAnchorText(), "<a>" + linktext2 + "</a>");
							decisionCase.add(false);
						}
					}
				}
			}
			if (decisionCase.size() == 1) {
				if (decisionCase.contains(true)) {
					DATASET.addPositiveData(Category.CHAIR_PERSON_TAG, originalSentence, originalSentence);
				} else {
					DATASET.addNegativeDifficultData(Category.CHAIR_PERSON_TAG, originalSentence, originalSentence);
				}
			} else if (decisionCase.size() == 2) {
				// Means sentence contain positive and negative roles
				// Ignore
			} else {
				// Means sentence contain positive and negative roles
				// Ignore
			}
		}catch(final Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Reads wikipedia files and convert them to {@link Document} Each file may
	 * contain multiple {@link Document}
	 * 
	 * @param pathTofile wikipedia file path
	 * @return
	 */
	public static List<Document> getDocuments(String pathTofile) {
		final List<Document> result = new ArrayList<>();
		final Pattern titlePattern = Pattern.compile("<doc.* url=\".*\" title=\".*\">");
		try {
			final List<String> lines = Files.readAllLines(Paths.get(pathTofile), StandardCharsets.UTF_8);
			String title = "";
			final List<String> content = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				final String line = lines.get(i);
				if (line.isEmpty()) {
					continue;
				}
				final Matcher titleMatcher = titlePattern.matcher(line);
				if (titleMatcher.find()) {
					content.clear();
					title = lines.get(++i);
					continue;
				} else if (line.equals("</doc>")) {
					final Document d = new Document(title, content, pathTofile);
					result.add(d);
				} else {
					content.add(line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
}
