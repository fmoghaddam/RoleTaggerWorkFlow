package ise.roletagger.datasetgenerator;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import ise.roletagger.model.Category;
import ise.roletagger.model.CategoryTrees;
import ise.roletagger.model.DataSourceType;
import ise.roletagger.model.Dataset;
import ise.roletagger.model.Document;
import ise.roletagger.model.Entity;
import ise.roletagger.model.Global;
import ise.roletagger.model.HtmlLink;
import ise.roletagger.model.RoleListProvider;
import ise.roletagger.model.TagPosition;
import ise.roletagger.model.TagPositions;
import ise.roletagger.util.Config;
import ise.roletagger.util.DictionaryRegexPatterns;
import ise.roletagger.util.EntityFileLoader;
import ise.roletagger.util.FileUtil;
import ise.roletagger.util.HTMLLinkExtractorWithoutSentenseSegmnetation;

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
 * In this version I want to increase the quality of the generated positive and
 * negative sentences
 * 
 * @author fbm
 *
 */
@Deprecated
public class DatasetGeneratorWithCategoryTreesV4 {

	/**
	 * Sentences more than this number of href will be ignored. This way I would
	 * like to ignore lists
	 */
	private static final int MAX_NUMBRE_OF_HREF_TO_CONSIDER = 10;
	private static final String WHERE_TO_WRITE_DATASET = Config.getString("WHERE_TO_WRITE_DATASET", "");
	/**
	 * This contains the positive and negative samples
	 */
	private static final Dataset DATASET = new Dataset();
	/**
	 * This folder contains all the wikipedia pages which are already cleaned by a
	 * python code from https://github.com/attardi/wikiextractor and contains the
	 * links and anchor text
	 */
	private static final String WIKI_FILES_FOLDER = Config.getString("WIKI_FILES_FOLDER", "");
	/**
	 * This file is a dump which contains relation between each entity and its
	 * category entity dbp:subject category
	 */
	private static final String ENTITY_CATEGORY_FILE = Config.getString("ENTITY_CATEGORY_FILE", "");
	/**
	 * This folder files related to category trees which are already calculated as a
	 * preprocess by my another project named "CategoryTreeGeneration"
	 * https://github.com/fmoghaddam/CategoryTreeGeneration
	 */
	private static final String CATEGORY_TREE_FOLDER = Config.getString("CATEGORY_TREE_FOLDER_CLEAN", "");
	/**
	 * Number of thread for parallelization
	 */
	private static final int NUMBER_OF_THREADS = Config.getInt("NUMBER_OF_THREADS", 0);
	/**
	 * Contains mapping between urls and entities. It will be loaded based on
	 * "data/entities" folder These are the seed as input list (persons, titles,...)
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

	/**
	 * Contains mapping between a category and all the roles that this category has
	 * from dictionary. Every role goes to a new regex pattern to be able to select
	 * the longest match
	 */
	private static Map<Category, List<Pattern>> categoryToRolePatterns;
	/**
	 * Contains mapping between a category and all the head roles that this category has
	 * from dictionary. Every head role goes to a new regex pattern to be able to select
	 * the longest match
	 */
	private static Map<Category, List<Pattern>> headRolePatterns;

	private static RoleListProvider dictionaries;

	private static final Map<String, Integer> usedEntityFromDictionary = new ConcurrentHashMap<>();

	public static void main(String[] args) {
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

			dictionaries = DictionaryRegexPatterns.getDictionaries();
			headRolePatterns = DictionaryRegexPatterns.getHeadRolePatterns();
			categoryToRolePatterns = DictionaryRegexPatterns.getCategoryToRolePatterns();

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

			FileUtil.deleteFolder(WHERE_TO_WRITE_DATASET);
			DATASET.printPositiveDataset();
			DATASET.printNegativeDatasetDifficult();
			DATASET.printPoitiveNegativeDifficultDataset();

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

		// final Map<String, Integer> sortByValueDescending =
		// MapUtil.sortByValueDescending(usedEntityFromDictionary);
		// for (Entry<String, Integer> e : sortByValueDescending.entrySet()) {
		// LOG.info(e.getKey() + "\t" + e.getValue());
		// System.err.println(e.getKey() + "\t" + e.getValue());
		// }
	}

	private static Runnable handle(String pathToSubFolder) {
		final Runnable r = () -> {
			try {
				final List<Document> documents = getDocuments(pathToSubFolder);
				for (final Document document : documents) {
					final Entity entity = entityMap.get(document.getTitle());
					try {
						if (entity == null) {
							for (final String line : document.getSentences()) {
								final int countMatches = StringUtils.countMatches(line, "<a href");
								if (line.contains("<a href") && countMatches <= MAX_NUMBRE_OF_HREF_TO_CONSIDER) {
									handlelineWithAnchorText(line);
								}
							}							
						}
						/**
						 * This page exist in our important pages list
						 */
						else {
							/**
							 * Check anchor text + normal sentence
							 */
							for (final String line : document.getSentences()) {
								if (line.contains("<a href")) {
									if (StringUtils.countMatches(line, "<a href") > MAX_NUMBRE_OF_HREF_TO_CONSIDER) {
										continue;
									}
									handlelineWithAnchorText(line);
								} else {
									handlelineWithoutAnchorText(line, entity);
								}
							}
						}
					} catch (Exception e) {
						System.err.println("Error at " + document.getLocation() + " error: " + e.getMessage());
						e.printStackTrace();
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
		List<Pattern> localRolePhrasePatterns = null;
		List<Pattern> localHeadRolePatterns = null;
		switch (entity.getCategoryFolder()) {
		case CHAIR_PERSON_TAG:
			localRolePhrasePatterns = categoryToRolePatterns.get(Category.CHAIR_PERSON_TAG);
			localHeadRolePatterns = headRolePatterns.get(Category.CHAIR_PERSON_TAG);
			break;
		case HEAD_OF_STATE_TAG:
			localRolePhrasePatterns = categoryToRolePatterns.get(Category.HEAD_OF_STATE_TAG);
			localHeadRolePatterns = headRolePatterns.get(Category.HEAD_OF_STATE_TAG);
			break;
		case MONARCH_TAG:
			localRolePhrasePatterns = categoryToRolePatterns.get(Category.MONARCH_TAG);
			localHeadRolePatterns = headRolePatterns.get(Category.MONARCH_TAG);
			break;
		case POPE_TAG:
			localRolePhrasePatterns = categoryToRolePatterns.get(Category.POPE_TAG);
			localHeadRolePatterns = headRolePatterns.get(Category.POPE_TAG);
			break;
		default:
			throw new IllegalArgumentException();
		}

		final TagPositions tagPositions = new TagPositions();

		StringBuilder fullSentence = new StringBuilder(line);
		for (Pattern p : localRolePhrasePatterns) {
			matcher = p.matcher(fullSentence);
			while (matcher.find()) {
				String foundText = matcher.group();
				handleDictionaryMap(p.pattern());
				int end = matcher.end();
				int start = matcher.start();

				final TagPosition tp = new TagPosition(foundText, start, end);
				if (tagPositions.alreadyExist(tp)) {
					continue;
				}
				tagPositions.add(tp);

				StringBuilder rolePhrase = new StringBuilder(foundText);
				for (Pattern pp : localHeadRolePatterns) {
					matcher = pp.matcher(rolePhrase);
					if (matcher.find()) {
						String foundText2 = matcher.group();
						int end2 = matcher.end();
						int start2 = matcher.start();
						rolePhrase = rolePhrase.replace(start2, end2,
								Global.getHeadRoleStartTag() + foundText2 + Global.getHeadRoleEndTag());
						break;
					}
				}
				int offset = 0;
				if (rolePhrase.toString().contains(Global.getHeadRoleEndTag())) {
					offset += Global.getHeadRoleEndTag().length() + Global.getHeadRoleStartTag().length();
				}
				offset += Global.getRolePhraseStartTag(entity.getCategoryFolder().name()).length()
						+ Global.getRolePhraseEndTag().length();

				tp.setEndIndex(end + offset);
				for (TagPosition tempTP : tagPositions.getPositions()) {
					if (tempTP.getStartIndex() > tp.getStartIndex()) {
						tempTP.setEndIndex(tempTP.getEndIndex() + offset);
						tempTP.setStartIndex(tempTP.getStartIndex() + offset);
					}
				}
				fullSentence = fullSentence.replace(start, end,
						Global.getRolePhraseStartTag(entity.getCategoryFolder().name()) + rolePhrase.toString()
						+ Global.getRolePhraseEndTag());
			}
		}
		if (fullSentence.toString().contains(Global.getRolePhraseEndTag())) {
			DATASET.addOnlyPositiveSentence(fullSentence.toString());
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
	 * @throws UnsupportedEncodingException
	 */
	private static void handlelineWithAnchorText(String line) throws UnsupportedEncodingException {
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
			/**
			 * If anchor text refer to any link in the list and anchor-text contains any
			 * role, It is positive sample
			 */
			if (entity != null) {
				Matcher matcher1 = null;
				List<Pattern> localRolePhrasePatterns = null;
				List<Pattern> localHeadrolePatterns = null;
				switch (entity.getCategoryFolder()) {
				case CHAIR_PERSON_TAG:
					localRolePhrasePatterns = categoryToRolePatterns.get(Category.CHAIR_PERSON_TAG);
					localHeadrolePatterns = headRolePatterns.get(Category.CHAIR_PERSON_TAG);
					break;
				case HEAD_OF_STATE_TAG:
					localRolePhrasePatterns = categoryToRolePatterns.get(Category.HEAD_OF_STATE_TAG);
					localHeadrolePatterns = headRolePatterns.get(Category.HEAD_OF_STATE_TAG);
					break;
				case MONARCH_TAG:
					localRolePhrasePatterns = categoryToRolePatterns.get(Category.MONARCH_TAG);
					localHeadrolePatterns = headRolePatterns.get(Category.MONARCH_TAG);
					break;
				case POPE_TAG:
					localRolePhrasePatterns = categoryToRolePatterns.get(Category.POPE_TAG);
					localHeadrolePatterns = headRolePatterns.get(Category.POPE_TAG);
					break;
				default:
					throw new IllegalArgumentException("Entity category is no matching with our exising categories");
				}
				for (Pattern p : localRolePhrasePatterns) {
					matcher1 = p.matcher(anchorText);
					String linktext1 = htmlLink.getAnchorText();
					if (matcher1.find()) {
						handleDictionaryMap(p.pattern());
						String originalFinding = matcher1.group();
						StringBuilder rolePhrase = new StringBuilder(matcher1.group());
						for (Pattern pp : localHeadrolePatterns) {
							matcher1 = pp.matcher(rolePhrase);
							if (matcher1.find()) {
								String foundText2 = matcher1.group();
								int end2 = matcher1.end();
								int start2 = matcher1.start();
								rolePhrase = rolePhrase.replace(start2, end2,
										Global.getHeadRoleStartTag() + foundText2 + Global.getHeadRoleEndTag());
								break;
							}
						}

						linktext1 = linktext1.replace(originalFinding,
								Global.getRolePhraseStartTag(entity.getCategoryFolder().name()) + rolePhrase
								+ Global.getRolePhraseEndTag());
						originalSentence = originalSentence.replace(htmlLink.getAnchorText(),
								Global.getAnchorStartTag() + linktext1 + Global.getAnchorEndTag());
						decisionCase.add(true);
						break;
					}
				}
			} else {
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
					boolean found = false;
					for (Entry<Category, List<Pattern>> patternEntity : categoryToRolePatterns.entrySet()) {
						for (Pattern p : patternEntity.getValue()) {
							final Matcher matcher2 = p.matcher(anchorText);
							if (matcher2.find()) {
								String linktext2 = htmlLink.getAnchorText();
								String foundText = matcher2.group();
								StringBuilder rolePhrase = new StringBuilder(matcher2.group());
								for (Pattern pp : headRolePatterns.get(patternEntity.getKey())) {
									Matcher matcher1 = pp.matcher(rolePhrase);
									if (matcher1.find()) {
										String foundText2 = matcher1.group();
										int end2 = matcher1.end();
										int start2 = matcher1.start();
										rolePhrase = rolePhrase.replace(start2, end2,
												Global.getHeadRoleStartTag() + foundText2 + Global.getHeadRoleEndTag());
										break;
									}
								}

								linktext2 = linktext2.replace(foundText,
										Global.getRolePhraseStartTag(patternEntity.getKey().name()) + rolePhrase
										+ Global.getRolePhraseEndTag());
								originalSentence = originalSentence.replace(htmlLink.getAnchorText(),
										Global.getAnchorStartTag() + linktext2 + Global.getAnchorEndTag());
								decisionCase.add(false);
								found = true;
								break;
							}
						}
						if (found) {
							break;
						}
					}
				}
			}
		}
		if (decisionCase.size() == 1) {
			if (decisionCase.contains(true)) {
				DATASET.addOnlyPositiveSentence(originalSentence);
			} else {
				DATASET.addOnlyDifficultNegativeSentence(originalSentence);
			}
		} else if (decisionCase.size() == 2) {
			// Means sentence contain positive and negative roles
			// Ignore
			DATASET.addPositiveNegativeSentence(originalSentence);
		} else {
			// Means sentence contain positive and negative roles
			// Ignore
		}
	}

	/**
	 * Reads wikipedia files and convert them to {@link Document} Each file may
	 * contain multiple {@link Document}
	 * 
	 * @param pathTofile
	 *            wikipedia file path
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
					final Document d = new Document(title.replace(" ", "_"), content, pathTofile);
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
