package de.ovgu.featureide.sampling.config.properties;

import de.ovgu.featureide.sampling.config.SamplingConfig;

/**
 * Some operation are randomized and use the value of this property as a seed.
 * 
 * @author Joshua Sprey
 * @author Sebastian Krieter
 */
public class Seed extends LongProperty {

	/**
	 * Identifier key for the seed property in a {@link SamplingConfig} file.
	 */
	private static final String SEED_PROPERTY_ID = "seed";

	/**
	 * Creates a new seed property and generates a seed equals to the
	 * {@link System#currentTimeMillis()}.
	 */
	public Seed() {
		super("seed", System.currentTimeMillis());
	}

	/**
	 * Creates a new seed property with a given default value.
	 */
	public Seed(long defaultValue) {
		super("seed", defaultValue);
	}

}
