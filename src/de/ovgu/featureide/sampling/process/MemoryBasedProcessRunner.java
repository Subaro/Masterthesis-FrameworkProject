package de.ovgu.featureide.sampling.process;

import java.io.IOException;

import de.ovgu.featureide.fm.benchmark.process.ProcessRunner;
import de.ovgu.featureide.fm.core.analysis.cnf.SolutionList;
import de.ovgu.featureide.sampling.algorithms.AJavaMemoryTWiseSamplingAlgorithm;
import de.ovgu.featureide.sampling.eval.analyzer.GarbageCollectorLogAnalyzer;

public class MemoryBasedProcessRunner extends ProcessRunner<SolutionList, AJavaMemoryTWiseSamplingAlgorithm, GCResult> {

	@Override
	protected void setResult(AJavaMemoryTWiseSamplingAlgorithm algorithm, GCResult result) throws IOException {
		super.setResult(algorithm, result);

		GarbageCollectorLogAnalyzer analyzer = new GarbageCollectorLogAnalyzer(
				algorithm.getPathOfGarbageCollectorFile());
		analyzer.processGCResults(result);
	}

}
