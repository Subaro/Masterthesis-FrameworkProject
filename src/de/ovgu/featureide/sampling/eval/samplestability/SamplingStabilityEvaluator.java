package de.ovgu.featureide.sampling.eval.samplestability;

import java.util.ArrayList;
import java.util.List;

import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.ConfigurationAnalyzer;
import de.ovgu.featureide.fm.core.configuration.FeatureNotFoundException;
import de.ovgu.featureide.fm.core.configuration.Selection;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.manager.IFeatureModelManager;
import de.ovgu.featureide.sampling.eval.samplestability.metrics.FIMDC;
import de.ovgu.featureide.sampling.eval.samplestability.metrics.ICSTMetric;
import de.ovgu.featureide.sampling.eval.samplestability.metrics.MSOC;
import de.ovgu.featureide.sampling.eval.samplestability.metrics.ROIC;

public class SamplingStabilityEvaluator {

	public class SampleSimilarityResult {
		public double resultFIMDC = -1;
		public double resultICST = -1;
		public double resultMSOC = -1;
		public double resultROIC = -1;
	}

	private IFeatureModelManager fmNew;
	private IFeatureModelManager fmOld;

	private Sample sampleNew = null;
	private Sample sampleOld = null;

	public SamplingStabilityEvaluator(IFeatureModelManager fmOld, Sample sampleOld, IFeatureModelManager fmNew,
			Sample sampleNew) {
		this.fmOld = fmOld;
		this.sampleOld = sampleOld.omitNegatives();
		this.fmNew = fmNew;
		this.sampleNew = sampleNew.omitNegatives();
	}

	private List<String> ConfToString(Configuration c) {
		List<String> list = new ArrayList<>();
		for (IFeature sf : c.getSelectedFeatures()) {
			list.add(sf.getName());
		}
		return list;
	}

	public SampleSimilarityResult execut() {
		SampleSimilarityResult result = new SampleSimilarityResult();
		// 1) ROIC
		ROIC roic = new ROIC();
		result.resultROIC = roic.analyze(fmOld, sampleOld, fmNew, sampleNew);
//		System.out.println("Result roic: " + result);

		// 2) MSOC
		MSOC msoc = new MSOC();
		result.resultMSOC = msoc.analyze(fmOld, sampleOld, fmNew, sampleNew);
//		System.out.println("Result msoc: " + result ); 

		// 3) FIMDC
		FIMDC fimdc = new FIMDC();
		result.resultFIMDC = fimdc.analyze(fmOld, sampleOld, fmNew, sampleNew);
//		System.out.println("Result fimdc: " + result);

		// 4) ICSTMetric
		ICSTMetric icst = new ICSTMetric();
		result.resultICST = icst.analyze(fmOld, sampleOld, fmNew, sampleNew);
//		System.out.println("Result icst: " + result);
		return result;
	}

	private List<List<String>> getValidConf(List<List<String>> sample, FeatureModelManager fm) {
		List<List<String>> validConfs = new ArrayList<>();

		for (List<String> c : sample) {
			try {
				Configuration conf = ListToConfig(c, fm);
				ConfigurationAnalyzer analyszer = new ConfigurationAnalyzer(fm.getVariableFormula(), conf);
				if (analyszer.isValid()) {
					List<String> featureList = ConfToString(conf);
					validConfs.add(featureList);
				} else {
					System.out.println("Invalid Conf found");
				}
			} catch (FeatureNotFoundException fnfEx) {
				System.out.println("Feature not Found exception");
				continue;
			}

		}
		return validConfs;
	}

	private Configuration ListToConfig(List<String> list, FeatureModelManager fm) {
		final Configuration configuration = new Configuration(fm.getVariableFormula());
		for (final String selection : list) {
			configuration.setManual(selection, Selection.SELECTED);
		}
		return configuration;
	}

}
