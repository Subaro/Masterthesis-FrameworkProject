package de.ovgu.featureide.sampling.algorithms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import de.ovgu.featureide.fm.benchmark.process.Algorithm;
import de.ovgu.featureide.fm.benchmark.util.Logger;
import de.ovgu.featureide.fm.core.analysis.cnf.SolutionList;

public abstract class AJavaMemoryTWiseSamplingAlgorithm extends Algorithm<SolutionList> {

	protected final Path fmFile;
	protected final Path gcCollectorPath;
	protected final Path outputFile;
	protected final int t;

	public AJavaMemoryTWiseSamplingAlgorithm(int t, Path outputFile, Path fmFile, Path gcCollectorPath) {
		this.t = t;
		this.outputFile = outputFile;
		this.fmFile = fmFile;
		this.gcCollectorPath = gcCollectorPath;
	}

	/**
	 * At this point the user must configure the command to invoke their sampling
	 * algorithm.<br>
	 * <br>
	 * 
	 * <b>Note: The user commands are added to a pre defined set of commands. The
	 * pre defined set is:</b><br>
	 * <br>
	 * 
	 * <i>java (GC commands)</i><br>
	 * <br>
	 * 
	 * The final command will have the following syntax:<br>
	 * <br>
	 * 
	 * <i>java (GC commands) (user commands)</i><br>
	 * <br>
	 * 
	 * Example:<br>
	 * <br>
	 * 
	 * <i>java -da -Xloggc:logICPL.txt -XX:+PrintGCDetails -XX:+PrintGCDateStamps
	 * -Xmx12g -Xms2g -cp ".;..;../lib/*" no.sintef.ict.splcatool.SPLCATool -t
	 * t_wise -fm model.dimacs -s 2 -o sampleICPL.csv -a</i><br>
	 * <br>
	 * 
	 * where:<br>
	 * <br>
	 * 
	 * <b><i>java (GC commands)</i></b> == <i>java -da -Xloggc:logICPL.txt
	 * -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xmx12g -Xms2g</i><br>
	 * <b><i>(user commands)</i></b> == <i>-cp ".;..;../lib/*"
	 * no.sintef.ict.splcatool.SPLCATool -t t_wise -fm model.dimacs -s 2 -o
	 * sampleICPL.csv -a</i>
	 */
	@Override
	protected void addCommandElements() {
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if ((obj == null)) {
			return false;
		}
		final Algorithm<?> other = (Algorithm<?>) obj;
		return Objects.equals(this.getFullName(), other.getFullName());
	}

	/**
	 * Path to the garbage collector file.
	 */
	public final Path getPathOfGarbageCollectorFile() {
		return gcCollectorPath;
	}

	/**
	 * Path for the model file. The model file will be a <i>DIMACS</i> model. This
	 * model shold be sampled.
	 */
	public final Path getPathOfModelFile() {
		return fmFile;
	}

	/**
	 * The path for the output file.
	 */
	public final Path getPathOfOutputFile() {
		return outputFile;
	}

	/**
	 * Defines the t-value for the t-coverage.
	 */
	public final int getT() {
		return t;
	}

	@Override
	public final void postProcess() throws Exception {
		try {
			Files.deleteIfExists(getPathOfOutputFile());
			Files.deleteIfExists(getPathOfGarbageCollectorFile());
		} catch (IOException e) {
			Logger.getInstance().logError(e);
		}
	}

	@Override
	public final void preProcess() throws Exception {
		commandElements.clear();
		addCommandElement("java");
		addCommandElement("-da");
		addCommandElement("-Xmx24g");
		addCommandElement("-Xms2g");
		addCommandElement("-Xlog:gc:" + getPathOfGarbageCollectorFile());
		//addCommandElement("-XX:+PrintGCDateStamps");
		addCommandElements();
	}
}
