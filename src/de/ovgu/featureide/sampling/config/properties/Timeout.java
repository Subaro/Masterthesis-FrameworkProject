package de.ovgu.featureide.sampling.config.properties;

import de.ovgu.featureide.sampling.config.SamplingConfig;

/**
 * This property holds the identifier for time that should be considered when
 * executing sampling algorithms.
 * 
 * @author Joshua Sprey
 * @author Sebastian Krieter
 */
public class Timeout extends LongProperty {

	/**
	 * Identifier key for the timeout property in a {@link SamplingConfig} file.
	 */
	private static final String TIMEOUT_PROPERTY_ID = "timeout";

	public Timeout() {
		super(TIMEOUT_PROPERTY_ID, Long.MAX_VALUE);
	}

}
