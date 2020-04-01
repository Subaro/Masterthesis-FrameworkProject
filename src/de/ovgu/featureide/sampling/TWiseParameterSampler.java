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
import de.ovgu.featureide.fm.benchmark.properties.StringListProperty;
import de.ovgu.featureide.fm.benchmark.util.CSVWriter;
import de.ovgu.featureide.fm.benchmark.util.FeatureModelReader;
import de.ovgu.featureide.fm.benchmark.util.Logger;
import de.ovgu.featureide.fm.core.analysis.cnf.CNF;
import de.ovgu.featureide.fm.core.analysis.cnf.ClauseList;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet;
import de.ovgu.featureide.fm.core.analysis.cnf.LiteralSet.Order;
import de.ovgu.featureide.fm.core.analysis.cnf.SolutionList;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.analysis.cnf.formula.NoAbstractCNFCreator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationGenerator;
import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.twise.TWiseConfigurationTester;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.io.dimacs.DIMACSFormat;
import de.ovgu.featureide.fm.core.io.dimacs.DIMACSFormatCNF;
import de.ovgu.featureide.fm.core.io.manager.FileHandler;
import de.ovgu.featureide.sampling.algorithms.AJavaMemoryTWiseSamplingAlgorithm;
import de.ovgu.featureide.sampling.eval.properties.AlgorithmProperty;
import de.ovgu.featureide.sampling.process.GCResult;
import de.ovgu.featureide.sampling.process.MemoryBasedProcessRunner;

public class TWiseParameterSampler extends AAlgorithmBenchmark<SolutionList, AJavaMemoryTWiseSamplingAlgorithm, GCResult> {

	protected static Path externalAlgorithmPath;
	private static final String PARAMETER_ALGORITHM = "-alg";
	private static final String PARAMETER_COVERAGE = "-t";
	private static final String PARAMETER_INPUTSYSTEM_PATH = "-in";

	private static final String PARAMETER_OUTPUTSYSTEM_PATH = "-out";

	protected static final AlgorithmProperty algorithmsProperty = new AlgorithmProperty();
	protected static final StringListProperty tProperty = new StringListProperty("t");

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

	protected Path samplesDir, curSampleDir;

	public TWiseParameterSampler(String configPath, String configName) throws Exception {
		super(configPath, configName);
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		extendCSVWriter(getModelCSVWriter(), Arrays.asList("Configurations", "Features", "Constraints"));
		extendCSVWriter(getDataCSVWriter(), Arrays.asList("Size", "Validity", "Coverage", "Runtime", "Throughput",
				"TotalCreatedBytes", "TotalPauseTime", "AveragePauseTime"));
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
			FileHandler.save(fileName, fm, format);

			// Add model name to model.txt
			String content = systemName + "\n";
			Files.write(config.configPath.resolve("models.txt"), content.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			Logger.getInstance().logError(e);
		}
	}

	private boolean isAcceptedModel(Path file) {
		return file.toString().endsWith(".xml") || file.toString().endsWith(".dimacs");
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
			for (String tValueString : tProperty.getValue()) {
				final int tValue = Integer.parseInt(tValueString);
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
		}

		return algorithms;
	}

	@Override
	protected void writeData(CSVWriter dataCSVWriter) {
		super.writeData(dataCSVWriter);

		final SolutionList configurationList = result.getResult();
		if (configurationList != null) {
			// Write sample metrics
			writeSamplesInfo(dataCSVWriter);
			// Write memory metrics
			writeMemory(dataCSVWriter);
		} else {
			// Write default values
			for (int i = 0; i < 8; i++) {
				dataCSVWriter.addValue(-1);
			}
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
			tester.setSample(samples);

			Logger.getInstance().logInfo("\tTesting configuration validity...", 2, true);
			dataCSVWriter.addValue(tester.getValidity().getValidInvalidRatio());

			// Completeness
			Logger.getInstance().logInfo("\tCalculating configuration coverage...", 2, true);
			tester.setNodes(TWiseConfigurationGenerator.convertLiterals(randomizedModelCNF.getVariables().getLiterals())); 
			dataCSVWriter.addValue(tester.getCoverage().getCoverage());
		} else {
			dataCSVWriter.addValue(1);
			dataCSVWriter.addValue(0);
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

	private static String toString(LiteralSet literalSet) {
		StringBuilder sb = new StringBuilder();
		for (int literal : literalSet.getLiterals()) {
			sb.append(literal);
			sb.append(',');
		}
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	@Override
	protected CNF adaptModel() {
		final CNF randomCNF = modelCNF.randomize(new Random(config.randomSeed.getValue() + systemIteration));
		final DIMACSFormatCNF format = new DIMACSFormatCNF();
		final Path fileName = config.tempPath.resolve("model" + "." + format.getSuffix());
		FileHandler.save(fileName, randomCNF, format);
		return modelCNF;
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
	protected CNF prepareModel() throws Exception {
		final String systemName = config.systemNames.get(systemIndex);

		FeatureModelReader fmReader = new FeatureModelReader();
		fmReader.setPathToModels(config.modelPath);
		IFeatureModel fm = fmReader.read(systemName);
		if (fm == null) {
			throw new NullPointerException();
		}
		CNF modelCNF = new FeatureModelFormula(fm).getElement(new NoAbstractCNFCreator()).normalize();

		curSampleDir = samplesDir.resolve(String.valueOf(systemIndex));
		Files.createDirectories(curSampleDir);
		final DIMACSFormatCNF format = new DIMACSFormatCNF();
		final Path fileName = curSampleDir.resolve("model." + format.getSuffix());
		FileHandler.save(fileName, modelCNF, format);

		return modelCNF;
	}

	protected void randomizeConditions(List<List<ClauseList>> groupedConditions, Random random) {
		for (List<ClauseList> group : groupedConditions) {
			Collections.shuffle(group, random);
		}
		Collections.shuffle(groupedConditions, random);
	}

	private LiteralSet reorderSolution(LiteralSet solution) {
		LiteralSet adaptedSolution = solution.adapt(randomizedModelCNF.getVariables(), modelCNF.getVariables());
		adaptedSolution.setOrder(Order.INDEX);
		return adaptedSolution;
	}

	@Override
	protected void setupDirectories() throws IOException {
		super.setupDirectories();

		samplesDir = config.outputPath.resolve("samples");
		Files.createDirectories(samplesDir);
	}

	protected void writeSamples(final String sampleMethod, final List<LiteralSet> configurationList) {
		try {
			Files.write(curSampleDir.resolve(sampleMethod + ".sample"), configurationList.stream()
					.map(this::reorderSolution).map(TWiseParameterSampler::toString).collect(Collectors.toList()));
		} catch (IOException e) {
			Logger.getInstance().logError(e);
		}
	}

	@Override
	protected void adaptAlgorithm(AJavaMemoryTWiseSamplingAlgorithm algorithm) throws Exception {
	}

}
