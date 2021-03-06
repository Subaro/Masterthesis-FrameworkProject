package de.ovgu.featureide.sampling.config.properties;

import de.ovgu.featureide.sampling.config.SamplingConfig;

/**
 * A generic property for all {@link Long}-based values in
 * {@link SamplingConfig} files.
 * 
 * @author Joshua Sprey
 * @author Sebastian Krieter
 */
public class LongProperty extends AProperty<Long> {

	/**
	 * Creates a new {@link Long}-based property with a given key.
	 * 
	 * @param key Identifer for the property.
	 */
	public LongProperty(String key) {
		super(key, 0L);
	}

	/**
	 * Creates a new {@link Long}-based property with a given key and default value.
	 * 
	 * @param key          Identifer for the property.
	 * @param defaultValue Default value for the property.
	 */
	public LongProperty(String key, Long defaultValue) {
		super(key, defaultValue);
	}

	@Override
	protected Long cast(String valueString) throws Exception {
		return Long.parseLong(valueString);
	}

}
