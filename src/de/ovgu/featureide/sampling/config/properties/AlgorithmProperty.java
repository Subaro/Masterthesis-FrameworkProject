package de.ovgu.featureide.sampling.config.properties;

import de.ovgu.featureide.sampling.config.SamplingConfig;

/**
 * This property holds the identifier for all the algorithms that should be
 * executed with the framework.
 * 
 * @author Joshua Sprey
 */
public class AlgorithmProperty extends StringListProperty {

	/**
	 * Identifier key for the algorithms property in a {@link SamplingConfig} file.
	 */
	private static final String ALGORITHM_PROPERTY_ID = "algorithms";

	public AlgorithmProperty() {
		super(ALGORITHM_PROPERTY_ID);
	}

}
