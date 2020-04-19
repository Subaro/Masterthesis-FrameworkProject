package de.ovgu.featureide.sampling;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.ovgu.featureide.fm.benchmark.AAlgorithmBenchmark;
import de.ovgu.featureide.fm.benchmark.properties.IntProperty;
import de.ovgu.featureide.fm.benchmark.util.CSVWriter;
import de.ovgu.featureide.fm.benchmark.util.FeatureModelReader;
import de.ovgu.featureide.fm.benchmark.util.Logger;
import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.ClauseList;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.SolutionList;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.NoAbstractCNFCreator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationTester;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.dimacs.DIMACSFormat;
import de.ovgu.featureide.fm.core.io.dimacs.DIMACSFormatCNF;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.manager.IFeatureModelManager;
import de.ovgu.featureide.fm.core.io.manager.SimpleFileHandler;
import de.ovgu.featureide.sampling.algorithms.AJavaMemoryTWiseSamplingAlgorithm;
import de.ovgu.featureide.sampling.eval.properties.AlgorithmProperty;
import de.ovgu.featureide.sampling.eval.samplestability.Sample;
import de.ovgu.featureide.sampling.eval.samplestability.SamplingStabilityEvaluator;
import de.ovgu.featureide.sampling.eval.samplestability.SamplingStabilityEvaluator.SampleSimilarityResult;
import de.ovgu.featureide.sampling.process.GCResult;
import de.ovgu.featureide.sampling.process.MemoryBasedProcessRunner;
import de.ovgu.featureide.sampling.util.PrefixChecker;

public class TWiseParameterSampler
		extends AAlgorithmBenchmark<SolutionList, AJavaMemoryTWiseSamplingAlgorithm, GCResult> {

	protected static final AlgorithmProperty algorithmsProperty = new AlgorithmProperty();
	protected static Path externalAlgorithmPath;
	private static final String PARAMETER_ALGORITHM = "-alg";
	private static final String PARAMETER_COVERAGE = "-t";
	private static final String PARAMETER_INPUTSYSTEM_PATH = "-in";
	private static final String PARAMETER_OUTPUTSYSTEM_PATH = "-out";
	protected static final IntProperty tProperty = new IntProperty("t");
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			Logger.getInstance().logInfo("Configuration path and name not specified!", 0, false);
			return;
		}

		// Set path to external algorithms
		externalAlgorithmPath = Paths.get("algos");

		final TWiseParameterSampler evaluator = new TWiseParameterSampler(args[0], args[1]);
		if (evaluator.parseParameter(args)) {
			evaluator.init();
			evaluator.run();
			evaluator.dispose();
		} else {
			Logger.getInstance().logInfo("Stopping framework. Reason: see [Error] above!", 0, false);
		}
	}
	private static String toString(List<String> sample) {
		StringBuilder sb = new StringBuilder();
		for (String string : sample) {
			sb.append(string);
			sb.append(",");
		}
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}
	/**
	 * Saves samples for the current system temporarly. It contains all samples from
	 * one model and for all system iteration. Each system iteration represents one
	 * list
	 */
	public Sample[][] curentSystemSamples = null;
	public boolean isSampleStabilityConsidered = false;

	/**
	 * Saves samples for the last system temporarly. It contains all samples from
	 * one model and for all system iteration. Each system iteration represents one
	 * list
	 */
	public Sample[][] previousSystemSamples = null;

	protected Path samplesDir, curSampleDir;

	/**
	 * Contains all systems feature models to be used for the stability calculation
	 */
	public IFeatureModel[] systems = null;

	public TWiseParameterSampler(String configPath, String configName) throws Exception {
		super(configPath, configName);
	}

	@Override
	protected void adaptAlgorithm(AJavaMemoryTWiseSamplingAlgorithm algorithm) throws Exception {
	}

	@Override
	protected CNF adaptModel() {
		final CNF randomCNF = modelCNF.randomize(new Random(config.randomSeed.getValue() + systemIteration));
		final DIMACSFormatCNF format = new DIMACSFormatCNF();
		final Path fileName = config.tempPath.resolve("model" + "." + format.getSuffix());
		SimpleFileHandler.save(fileName, randomCNF, format);
		return randomCNF;
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		extendCSVWriter(getModelCSVWriter(), Arrays.asList("Configurations", "Features", "Constraints"));
		extendCSVWriter(getDataCSVWriter(), Arrays.asList("Size", "Validity", "Coverage", "ROIC", "MSOC", "FIMD",
				"ICST", "Runtime", "Throughput", "TotalCreatedBytes", "TotalPauseTime", "AveragePauseTime"));
	}

	private void createModelEntry(Path model) {
		try {
			String systemName = model.toFile().getName().replaceFirst("[.][^.]+$", "");
			Logger.getInstance().logInfo("preparing " + systemName + "...", 3, true);

			// Load model
			FeatureModelReader fmReader = new FeatureModelReader();
			fmReader.setPathToModels(model.toFile().getParentFile().toPath());
			IFeatureModel fm = fmReader.read(systemName);
			if (fm == null) {
				throw new NullPointerException();
			}
			// Create new model directory
			Path modelDir = config.modelPath.resolve(systemName);
			Files.createDirectories(modelDir);

			// Save model as dimacs into model path
			final DIMACSFormat format = new DIMACSFormat();
			final Path fileName = modelDir.resolve("model.dimacs");
			SimpleFileHandler.save(fileName, fm, format);

			// Add model name to model.txt
			String content = systemName + "\n";
			Files.write(config.configPath.resolve("models.txt"), content.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			Logger.getInstance().logError(e);
		}
	}

	@Override
	protected MemoryBasedProcessRunner getNewProcessRunner() {
		return new MemoryBasedProcessRunner();
	}

	@Override
	protected GCResult getNewResult() {
		return new GCResult();
	}

	@Override
	public void init() throws Exception {
		super.init();
		isSampleStabilityConsidered = PrefixChecker.getLongestCommonPrefix(config.systemNames).length() > 5;
		systems = new IFeatureModel[config.systemNames.size()];
	}

	private boolean isAcceptedModel(Path file) {
		return file.toString().endsWith(".xml") || file.toString().endsWith(".dimacs");
	}

	@SuppressWarnings({ "unchecked", "resource", "unused" })
	public boolean isAlgorithmAvailable(String algorithmName) {
		ClassLoader cl = null;
		try {
			// Load all external algorithms
			File file = externalAlgorithmPath.toFile();
			URL url = file.toURI().toURL();
			URL[] urls = new URL[] { url };
			cl = new URLClassLoader(urls);
		} catch (MalformedURLException e) {
			return false;
		}
		try {
			if (cl != null) {
				Class<AJavaMemoryTWiseSamplingAlgorithm> cls;
				cls = (Class<AJavaMemoryTWiseSamplingAlgorithm>) cl.loadClass(algorithmName);
				return cls != null;
			}
		} catch (ClassNotFoundException e) {
			return false;
		}
		return false;
	}

	/**
	 * Reads arguments and configures the program accordingly.
	 * 
	 * Valid Parameters: -alg: Determines the only algorithm to use (expects a class
	 * name extending the ATWiseSampling class)
	 * 
	 * -t: Determines the t coverage (1,2,3...)
	 * 
	 * -in: Determines the input path for systems to use (Excpect input path
	 * containing models)
	 * 
	 * -out: Determines the output path (valid system path)
	 * 
	 * @param args List containing all parameters
	 */
	private boolean parseParameter(String[] args) {
		Logger.getInstance().logInfo("Reading arguments...", 0, false);
		// Configure depending of program arguments
		List<String> arguments = Arrays.asList(args);

		// 1) (-alg) Algorithm (expects a class name extending the ATWiseSampling class)
		if (arguments.contains(PARAMETER_ALGORITHM)) {
			int index = arguments.indexOf(PARAMETER_ALGORITHM);
			if ((index + 1) < arguments.size()) {
				String algorithmName = arguments.get(index + 1);
				String result = algorithmName;
				for (String string : algorithmsProperty.getValue()) {
					result += "," + string;
				}
				algorithmsProperty.setValue(result);
				if (isAlgorithmAvailable(algorithmName)) {
					Logger.getInstance().logInfo("[-alg] = : " + algorithmName + " was found", 1, false);
				} else {
					Logger.getInstance().logInfo("[Error] [-alg] = : " + algorithmName + " was not found!", 1, false);
					return false;
				}
			} else {
				Logger.getInstance().logInfo("[Error] [-alg] Value is missing (algorithm's absolute class name)", 1,
						false);
				return false;
			}
		}

		// 2) (-t) t_wise coverage
		if (arguments.contains(PARAMETER_COVERAGE)) {
			int index = arguments.indexOf(PARAMETER_COVERAGE);
			if ((index + 1) < arguments.size()) {
				String value = arguments.get(index + 1);
				try {
					int coverage = Integer.parseInt(value);
					if (coverage >= 1 && coverage <= 5) {
						tProperty.setValue(value);
						Logger.getInstance().logInfo("[-t] = : " + arguments.get(index), 1, false);
					} else {
						Logger.getInstance().logInfo("[Error] [-t] = \"" + arguments.get(index)
								+ "\" not valid. Please specify a valid coverage (1-5)", 1, false);
						return false;
					}
				} catch (Exception e) {
					Logger.getInstance().logInfo("[Error] [-t] = \"" + arguments.get(index)
							+ "\" not valid. Please specify a valid coverage (1-5)", 1, false);
					return false;
				}
			} else {
				Logger.getInstance().logInfo("[Error] [--t] Value is missing (1-5)", 1, false);
				return false;
			}
		}

		// 3) Specify systems path
		if (arguments.contains(PARAMETER_INPUTSYSTEM_PATH)) {
			int index = arguments.indexOf(PARAMETER_INPUTSYSTEM_PATH);
			if ((index + 1) < arguments.size()) {
				String absolutePathToSystems = arguments.get(index + 1);
				try {
					// Check that the path exist
					Path pathToSystems = Paths.get(absolutePathToSystems);
					if (Files.exists(pathToSystems)) {
						Logger.getInstance().logInfo("[-in] = \"" + absolutePathToSystems.toString() + "\"", 1, false);
						// Load all .xml files or .dimac files and copy the .dimacs format as individual
						// systems into the model's path
						Files.write(config.configPath.resolve("models.txt"), "".getBytes(),
								StandardOpenOption.TRUNCATE_EXISTING);
						long count = 0;
						try (Stream<Path> paths = Files.walk(pathToSystems)) {
							count = paths.filter(this::isAcceptedModel).count();
						}
						if (count <= 0) {
							Logger.getInstance().logInfo("[Error] [-in] Input path does not contain any valid models.",
									2, false);
							return false;
						} else {
							Logger.getInstance().logInfo("Found " + count + " models. Start preparation...", 2, false);
							try (Stream<Path> paths = Files.walk(pathToSystems)) {
								paths.filter(this::isAcceptedModel).forEach(this::createModelEntry);
							}
							Logger.getInstance().logInfo("Done!", 3, false);
						}
					} else {
						// Path is non-existent
						Logger.getInstance().logInfo("[Error] [-in] = \"" + absolutePathToSystems.toString()
								+ "\" is non-existent! (valid aboslute path)", 1, false);
						return false;
					}
				} catch (Exception e) {
					// String cannot be parsed to path or cannot be created => invalid
					Logger.getInstance().logInfo("[Error] [-in] = \"" + absolutePathToSystems.toString()
							+ "\" is invalid! (valid aboslute path)", 1, false);
					return false;
				}
			} else {
				Logger.getInstance().logInfo("[Error] [-in] Value is missing (valid absolute path)", 1, false);
				return false;
			}
		}

		// 4) Specify output path
		if (arguments.contains(PARAMETER_OUTPUTSYSTEM_PATH)) {
			int index = arguments.indexOf(PARAMETER_OUTPUTSYSTEM_PATH);
			if ((index + 1) < arguments.size()) {
				String absolutePathToOutput = arguments.get(index + 1);
				try {
					// Check that the path exist
					Path pathToOutput = Paths.get(absolutePathToOutput);
					if (Files.exists(pathToOutput)) {
						Logger.getInstance().logInfo("[-out] = \"" + absolutePathToOutput.toString() + "\"", 1, false);
						config.outputPathProperty.setValue(pathToOutput.toString());
						config.outputRootPath = pathToOutput;
					} else {
						// Path is not existent => create
						Logger.getInstance().logInfo("[-out] = \"" + absolutePathToOutput.toString()
								+ "\" is non-existent. Will be created...", 1, false);

						// Create directory
						Files.createDirectories(pathToOutput);
						Logger.getInstance().logInfo("Done!", 2, false);
						config.outputPathProperty.setValue(pathToOutput.toString());
						config.outputRootPath = pathToOutput;
					}
				} catch (Exception e) {
					// String cannot be parsed to path or cannot be created => invalid
					Logger.getInstance().logInfo("[Error] [-out] = \"" + absolutePathToOutput.toString()
							+ "\" is invalid! (valid aboslute path)", 1, false);
					return false;
				}
			} else {
				Logger.getInstance().logInfo("[Error] [-out] Value is missing (valid absolute path)", 1, false);
				return false;
			}
		}
		Logger.getInstance().logInfo(" ", 0, false);
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected List<AJavaMemoryTWiseSamplingAlgorithm> prepareAlgorithms() {
		ArrayList<AJavaMemoryTWiseSamplingAlgorithm> algorithms = new ArrayList<>();

		ClassLoader cl = null;
		try {
			// Load all external algorithms
			File file = externalAlgorithmPath.toFile();
			URL url = file.toURI().toURL();
			URL[] urls = new URL[] { url };
			cl = new URLClassLoader(urls);
		} catch (MalformedURLException e) {
			Logger.getInstance().logError(e);
		}

		for (String algorithmName : algorithmsProperty.getValue()) {
			final int tValue = tProperty.getValue();
			final Path sampleFile = config.tempPath.resolve("sample.csv");
			final Path modelFile = config.tempPath.resolve("model.dimacs");
			final Path gcCollectorFile = config.tempPath.resolve("runtimeGC.log");
			switch (algorithmName) {
			default:
				// Try if the given string is a class name for an external algorithm
				try {
					if (cl != null) {
						Class<AJavaMemoryTWiseSamplingAlgorithm> cls;
						cls = (Class<AJavaMemoryTWiseSamplingAlgorithm>) cl.loadClass(algorithmName);
						try {
							algorithms.add(cls.getDeclaredConstructor(int.class, Path.class, Path.class, Path.class)
									.newInstance(tValue, sampleFile, modelFile, gcCollectorFile));
						} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
								| InvocationTargetException | NoSuchMethodException | SecurityException e) {
							Logger.getInstance().logError(e);
						}
					}
				} catch (ClassNotFoundException e) {
					Logger.getInstance().logError(e);
				}
				break;
			}
		}

		// Create temp lists when stability is going to be calculated
		if (isSampleStabilityConsidered) {
			if (systemIteration == 0) {
				// Each list represents one system iteration, and, thus contains all samples of
				// all algorithms
				curentSystemSamples = new Sample[config.systemIterations.getValue()][algorithms.size()];
				// Each list represents one system iteration, and, thus contains all samples of
				// all algorithms
				previousSystemSamples = new Sample[config.systemIterations.getValue()][algorithms.size()];
			} else {
				previousSystemSamples = curentSystemSamples;
				curentSystemSamples = new Sample[config.systemIterations.getValue()][algorithms.size()];
			}
		}
		return algorithms;
	}

	@Override
	protected CNF prepareModel() throws Exception {
		final String systemName = config.systemNames.get(systemIndex);

		FeatureModelReader fmReader = new FeatureModelReader();
		fmReader.setPathToModels(config.modelPath);
		IFeatureModel fm = fmReader.read(systemName);
		if (fm == null) {
			throw new NullPointerException();
		}
		systems[systemIndex] = fm;
		CNF modelCNF = new FeatureModelFormula(fm).getElement(new NoAbstractCNFCreator()).normalize();

		curSampleDir = samplesDir.resolve(systemName);
		Files.createDirectories(curSampleDir);
		final DIMACSFormatCNF format = new DIMACSFormatCNF();
		final Path fileName = curSampleDir.resolve("model." + format.getSuffix());
		SimpleFileHandler.save(fileName, modelCNF, format);

		return modelCNF;
	}

	protected void randomizeConditions(List<List<ClauseList>> groupedConditions, Random random) {
		for (List<ClauseList> group : groupedConditions) {
			Collections.shuffle(group, random);
		}
		Collections.shuffle(groupedConditions, random);
	}

	private List<String> reorderSolution(List<String> solution) {
		Collections.sort(solution);
		return solution;
	}

	@Override
	protected void setupDirectories() throws IOException {
		super.setupDirectories();

		samplesDir = config.outputPath.resolve("samples");
		Files.createDirectories(samplesDir);
	}

	@Override
	protected void writeData(CSVWriter dataCSVWriter) {
		super.writeData(dataCSVWriter);

		final SolutionList configurationList = result.getResult();
		if (configurationList != null) {
			// Create sample from solution list
			Sample sample = new Sample();
			for (LiteralSet config : configurationList.getSolutions()) {
				List<String> configList = new ArrayList<>(config.size());
				for (int lit : config.getLiterals()) {
					String name = randomizedModelCNF.getVariables().getName(lit);
					if (lit < 0) {
						configList.add("-" + name);
					} else {
						configList.add(name);
					}
				}
				sample.add(configList);
			}
			// Save sample
			curentSystemSamples[systemIteration - 1][algorithmIndex] = sample;

			// Write sample metrics
			writeSamplesInfo(dataCSVWriter);
			// Write memory metrics
			writeMemory(dataCSVWriter);
			// Save sample
			writeSamples(config.systemNames.get(systemIndex) + "_" + algorithmList.get(algorithmIndex) + "_"
					+ systemIteration + "_" + algorithmIteration, sample);
		} else {
			// Write default values
			for (int i = 0; i < 8; i++) {
				dataCSVWriter.addValue(-1);
			}
		}
	}

	protected void writeMemory(CSVWriter memoryCSVWriter) {
		// Add memory data to memory
		NumberFormat nf = NumberFormat.getInstance();
		nf.setGroupingUsed(false); // remove the dots grouping each 3 digits for CSV format
		nf.setMaximumFractionDigits(5); // remove the fraction digits
		memoryCSVWriter.addValue(nf.format(result.getStatisticCompleteRuntime()));
		memoryCSVWriter.addValue(nf.format(result.getStatisticThroughput()));
		memoryCSVWriter.addValue(nf.format(result.getStatisticCreatedBytesTotal()));
		memoryCSVWriter.addValue(nf.format(result.getStatisticPauseTimeTotal()));
		memoryCSVWriter.addValue(nf.format(result.getStatisticPauseTimeAvg()));
	}

	protected void writeSamples(final String sampleMethod, final Sample sample) {
		try {
			Files.write(curSampleDir.resolve(sampleMethod + ".sample"), sample.stream().map(this::reorderSolution)
					.map(TWiseParameterSampler::toString).collect(Collectors.toList()));
		} catch (IOException e) {
			Logger.getInstance().logError(e);
		}
	}

	/**
	 * Writes information about the samples (Size, Validity, Coverage Completeness)
	 * 
	 * @param dataCSVWriter writer
	 */
	private void writeSamplesInfo(CSVWriter dataCSVWriter) {
		final SolutionList configurationList = result.getResult();
		// Size
		dataCSVWriter.addValue(configurationList.getSolutions().size());
		if (configurationList.getSolutions().size() > 0) {
			// Validity
			List<LiteralSet> samples = configurationList.getSolutions();
			TWiseConfigurationTester tester = new TWiseConfigurationTester(randomizedModelCNF);
			tester.setNodes(
					TWiseConfigurationGenerator.convertLiterals(randomizedModelCNF.getVariables().getLiterals()));
			tester.setT(tProperty.getValue());
			tester.setSample(samples);

			Logger.getInstance().logInfo("\tTesting configuration validity...", 2, true);
			dataCSVWriter.addValue(tester.getValidity().getValidInvalidRatio());

			// Completeness
			Logger.getInstance().logInfo("\tCalculating configuration coverage...", 2, true);
			dataCSVWriter.addValue(tester.getCoverage().getCoverage());

			// Stability
			if (isSampleStabilityConsidered) {
				if (systemIndex >= 1) {
					Sample currentSample = curentSystemSamples[systemIteration - 1][algorithmIndex];
					Sample previousSample = previousSystemSamples[systemIteration - 1][algorithmIndex];
					if (currentSample != null && previousSample != null) {
						IFeatureModelManager currentFM = FeatureModelManager.getInstance(systems[systemIndex]);
						IFeatureModelManager previousFM = FeatureModelManager.getInstance(systems[systemIndex - 1]);
						SamplingStabilityEvaluator core = new SamplingStabilityEvaluator(previousFM, previousSample,
								currentFM, currentSample);
						SampleSimilarityResult similarityResult = core.execut();
						NumberFormat nf = NumberFormat.getInstance();
						nf.setGroupingUsed(false); // remove the dots grouping each 3 digits for CSV format
						nf.setMaximumFractionDigits(5); // remove the fraction digits
						dataCSVWriter.addValue(nf.format(similarityResult.resultROIC));
						dataCSVWriter.addValue(nf.format(similarityResult.resultMSOC));
						dataCSVWriter.addValue(nf.format(similarityResult.resultFIMDC));
						dataCSVWriter.addValue(nf.format(similarityResult.resultICST));
					} else {
						// Just print -1 for skipped iterations
						dataCSVWriter.addValue(-1);
						dataCSVWriter.addValue(-1);
						dataCSVWriter.addValue(-1);
						dataCSVWriter.addValue(-1);
					}
				} else {
					// Just print -1 for first iteration
					dataCSVWriter.addValue(-1);
					dataCSVWriter.addValue(-1);
					dataCSVWriter.addValue(-1);
					dataCSVWriter.addValue(-1);
				}
			}
		} else {
			dataCSVWriter.addValue(1);
			dataCSVWriter.addValue(0);
		}
	}

}
