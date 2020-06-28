package de.ovgu.featureide.sampling.config.properties;

import de.ovgu.featureide.sampling.config.SamplingConfig;

/**
 * Interface for entries in the {@link SamplingConfig}.
 * 
 * @author Joshua Sprey
 * @author Sebastian Krieter
 */
public interface IProperty {

	/**
	 * Retrieves the identifier for this property.
	 * 
	 * @return The properties identifier as {@link String}.
	 */
	String getKey();

	/**
	 * Retrieves the object for this property.
	 * 
	 * @return The properties object.
	 */
	Object getValue();

	/**
	 * Sets the object for this property.
	 * 
	 * @return The properties object.
	 */
	boolean setValue(String valueString);

}
