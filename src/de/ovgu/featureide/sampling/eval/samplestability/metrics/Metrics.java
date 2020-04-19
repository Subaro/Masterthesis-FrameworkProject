package de.ovgu.featureide.sampling.eval.samplestability.metrics;

public enum Metrics {
	FIMDC("fimdc"), ICST("icst"), MSOC("msoc"), ROIC("roic");

	private String metric = "";

	private Metrics(String metric) {
		this.metric = metric;
	}

	public String getMetricName() {
		return this.metric;
	}

}
