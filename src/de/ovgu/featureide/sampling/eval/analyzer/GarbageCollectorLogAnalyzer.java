package de.ovgu.featureide.sampling.eval.analyzer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.tagtraum.perf.gcviewer.imp.DataReaderException;
import com.tagtraum.perf.gcviewer.imp.DataReaderFacade;
import com.tagtraum.perf.gcviewer.model.GCModel;
import com.tagtraum.perf.gcviewer.model.GCResource;
import com.tagtraum.perf.gcviewer.model.GcResourceFile;

import de.ovgu.featureide.fm.benchmark.util.Logger;
import de.ovgu.featureide.sampling.process.GCResult;

/***
 * Uses the GCViewer library to extract essential memory consumption information
 * from GC log files.
 * 
 * @author Joshua Sprey
 *
 */
public class GarbageCollectorLogAnalyzer {

	private final Path pathToLogFile;
	
	/** This output stream omits all logs*/
	private OutputStream emptyStream = new OutputStream() {
		@Override
		public void write(int b) throws IOException {}
	};

	public GarbageCollectorLogAnalyzer(Path pathToLogFile) {
		this.pathToLogFile = pathToLogFile;
	}

	public void processGCResults(GCResult result) {
		if (Files.exists(pathToLogFile)) {
			long numOfLines = 0;
			PrintStream oldErr = System.err;
			PrintStream oldOut = System.out;
			System.setErr(new PrintStream(emptyStream));
			System.setOut(new PrintStream(emptyStream));
			try (Stream<String> lines = Files.lines(pathToLogFile, Charset.defaultCharset())) {
				numOfLines = lines.count();
				if (numOfLines > 3) {
					GCResource logFile = new GcResourceFile(pathToLogFile.toFile());
					DataReaderFacade dataReaderFacade = new DataReaderFacade();
					GCModel model = dataReaderFacade.loadModel(logFile);
					result.setStatisticCreatedBytesTotal(model.getFreedMemoryByGC().getSum() / 1024L);
					result.setStatisticThroughput(model.getThroughput());
					result.setStatisticPauseTimeAvg(model.getPause().average());
					result.setStatisticPauseTimeTotal(model.getPause().getSum());
					result.setStatisticCompleteRuntime(model.getRunningTime());
					return;
				}
			} catch (DataReaderException e) {
			} catch (IOException e1) {
			} finally {
			    System.setErr(oldErr);
			    System.setOut(oldOut);
			}
		}

		Logger.getInstance().logInfo(
				"[Error] Could not load garbage collector file. Reason could be that the program did not run correctly or the runtime did not exceed some seconds.",
				3, true);
		result.setStatisticCreatedBytesTotal(-1);
		result.setStatisticThroughput(-1);
		result.setStatisticPauseTimeAvg(-1);
		result.setStatisticPauseTimeTotal(-1);
		result.setStatisticCompleteRuntime(-1);
	}
}
