package de.ovgu.featureide.sampling.process;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.ovgu.featureide.sampling.algorithms.ASamplingAlgorithm;
import de.ovgu.featureide.sampling.logger.ErrStreamCollector;
import de.ovgu.featureide.sampling.logger.ErrStreamReader;
import de.ovgu.featureide.sampling.logger.Logger;
import de.ovgu.featureide.sampling.logger.OutStreamReader;
import de.ovgu.featureide.sampling.logger.StreamRedirector;

/**
 * Controls the instantiation, execution and monitoring of the sampling process.
 * 
 * @author Joshua Sprey
 * @author Sebastian Krieter
 */
public class SamplingProcessRunner {

	private long timeout = Long.MAX_VALUE;

	/**
	 * @return The timeout for the sampling process.
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * Executes a sampling process via a {@link Process}. Every kind of command can
	 * be used, and thus, all kind of programs are supported.
	 * 
	 * @param algorithm The algorithm to evaluate.
	 * @param result    Empty result object that should be filled.
	 */
	public SamplingResults run(ASamplingAlgorithm algorithm) {
		SamplingResults result = new SamplingResults();
		boolean terminatedInTime = false;
		long startTime = 0, endTime = 0;
		try {
			System.gc();
			algorithm.preProcess();

			Logger.getInstance().logInfo(algorithm.getCommand(), 1, true);

			final List<String> command = algorithm.getCommandElements();
			if (!command.isEmpty()) {
				final ProcessBuilder processBuilder = new ProcessBuilder(command);
				Process process = null;

				final ErrStreamCollector errStreamCollector = new ErrStreamCollector();
				final StreamRedirector errRedirector = new StreamRedirector(
						Arrays.asList(new ErrStreamReader(), errStreamCollector));
				final StreamRedirector outRedirector = new StreamRedirector(
						Arrays.asList(new OutStreamReader(), algorithm));
				final Thread outThread = new Thread(outRedirector);
				final Thread errThread = new Thread(errRedirector);
				try {
					startTime = System.nanoTime();
					process = processBuilder.start();

					outRedirector.setInputStream(process.getInputStream());
					errRedirector.setInputStream(process.getErrorStream());
					outThread.start();
					errThread.start();

					terminatedInTime = process.waitFor(timeout, TimeUnit.MILLISECONDS);
					endTime = System.nanoTime();
					result.setTerminatedInTime(terminatedInTime);
					result.setNoErrorOccured(errStreamCollector.getErrList().isEmpty());
					result.setRuntime((endTime - startTime) / 1_000_000L);
				} finally {
					if (process != null) {
						process.destroyForcibly();
					}
				}
			} else {
				result.setTerminatedInTime(false);
				result.setNoErrorOccured(false);
				result.setRuntime(SamplingResults.INVALID_TIME);
			}
		} catch (Exception e) {
			Logger.getInstance().logError(e, true);
			result.setTerminatedInTime(false);
			result.setNoErrorOccured(false);
			result.setRuntime(SamplingResults.INVALID_TIME);
		}
		try {
			setResult(algorithm, result);
		} catch (Exception e) {
			Logger.getInstance().logError(e, true);
			if (terminatedInTime) {
				result.setNoErrorOccured(false);
			}
		}
		try {
			algorithm.postProcess();
		} catch (Exception e) {
			Logger.getInstance().logError(e);
		}
		return result;
	}

	/**
	 * Retrieves the results from the algorithm and saves them in the results data
	 * structure.
	 * 
	 * @param algorithm The evaluated algorithm.
	 * @param result    The results structure waiting to be filled.
	 * @throws IOException
	 */
	protected void setResult(ASamplingAlgorithm algorithm, SamplingResults result) throws IOException {
		result.setComputedSample(algorithm.parseResults());
		result.setMemoryResults(algorithm.parseMemory());
	}

	/**
	 * Sets the timeout for the sampling process.
	 * 
	 * @param timeout Timeout to set.
	 */
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
}
