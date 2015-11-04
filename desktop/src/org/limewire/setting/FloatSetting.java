package org.limewire.setting;

import java.util.Properties;


/**
 * Provides a float setting value. As a subclass of 
 * <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>FloatSetting</code> object with a 
 * {@link SettingsFactory#createFloatSetting(String, float)}.
 */
public class FloatSetting extends AbstractNumberSetting<Float> {
    
    private float value;

	/**
	 * Creates a new <tt>FloatSetting</tt> instance with the specified
	 * key and default value.
	 *
     * @param defaultProps
	 * @param key the constant key to use for the setting
	 * @param defaultFloat the default value to use for the setting
	 */
	FloatSetting(Properties defaultProps, Properties props, String key, 
                                                         float defaultFloat) {
		super(defaultProps, props, key, String.valueOf(defaultFloat), 
                                                             false, null, null);
	}

    FloatSetting(Properties defaultProps, Properties props, String key, 
                 float defaultFloat, float min, float max) {
		super(defaultProps, props, key, String.valueOf(defaultFloat), 
              true, new Float(min), new Float(max) );
	}
        
	/**
	 * Returns the value of this setting.
	 * 
	 * @return the value of this setting
	 */
	public float getValue() {
        return value;
	}

	/**
	 * Mutator for this setting.
	 *
	 * @param value the value to store
	 */
	public void setValue(float value) {
	    setValueInternal(String.valueOf(value));
	}
    
    /** Load value from property string value
     * @param sValue property string value
     *
     */
    protected void loadValue(String sValue) {
        try {
            value = Float.valueOf(sValue.trim()).floatValue();
        } catch(NumberFormatException nfe) {
            revertToDefault();
        }
    }

    protected Comparable<Float> convertToComparable(String value) {
        return new Float(value);
    }

}
