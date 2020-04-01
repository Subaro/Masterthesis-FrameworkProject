package de.ovgu.featureide.sampling.process;

import de.ovgu.featureide.fm.benchmark.process.Result;
import de.ovgu.featureide.fm.core.analysis.cnf.SolutionList;

public class GCResult extends Result<SolutionList> {

	protected double statisticCompleteRuntime = 0;
	protected long statisticCreatedBytesTotal = 0;
	protected double statisticPauseTimeAvg = 0;
	protected double statisticPauseTimeTotal = 0;
	protected double statisticThroughput = 0;

	public double getStatisticCompleteRuntime() {
		return statisticCompleteRuntime;
	}

	public long getStatisticCreatedBytesTotal() {
		return statisticCreatedBytesTotal;
	}

	public double getStatisticPauseTimeAvg() {
		return statisticPauseTimeAvg;
	}

	public double getStatisticPauseTimeTotal() {
		return statisticPauseTimeTotal;
	}

	public double getStatisticThroughput() {
		return statisticThroughput;
	}

	public void setStatisticCompleteRuntime(double statisticCompleteRuntime) {
		this.statisticCompleteRuntime = statisticCompleteRuntime;
	}

	public void setStatisticCreatedBytesTotal(long statisticCreatedBytesTotal) {
		this.statisticCreatedBytesTotal = statisticCreatedBytesTotal;
	}

	public void setStatisticPauseTimeAvg(double statisticPauseTimeAvg) {
		this.statisticPauseTimeAvg = statisticPauseTimeAvg;
	}

	public void setStatisticPauseTimeTotal(double statisticPauseTimeTotal) {
		this.statisticPauseTimeTotal = statisticPauseTimeTotal;
	}

	public void setStatisticThroughput(double statisticThroughput) {
		this.statisticThroughput = statisticThroughput;
	}
}
