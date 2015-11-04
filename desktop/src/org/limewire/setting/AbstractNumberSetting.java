package org.limewire.setting;

import java.util.Properties;

/** 
 * Provides a {@link Number} setting value and 
 * ensures any value you set in the future falls within a range. As a subclass 
 * of <code>Setting</code>, the setting has a key. If the value is set outside 
 * the number range, the value is set to the closer value of either the minimum 
 * or maximum range value. For example, if the range is [0,2] and you set the 
 * value to 8, the value is actually set to 2.
 * <p>
 * Additionally, <code>AbstractNumber</code> defines a method 
 * for subclasses to convert a string to a {@link Comparable}. 
 */
public abstract class AbstractNumberSetting<T extends Number & Comparable<T>> extends AbstractSetting {

    /**
     * Adds a safeguard against remote making a setting take a value beyond the
     * reasonable max 
     */
    protected final T MAX_VALUE;

    /**
     * Adds a safeguard against remote making a setting take a value below the
     * reasonable min
     */
    protected final T MIN_VALUE;
    
    /** Whether or not this is a remote setting. */
    private final boolean remote;
    
    protected AbstractNumberSetting(Properties defaultProps, Properties props,
                                    String key, String defaultValue, 
                                    boolean remote, T min, T max) {
        super(defaultProps, props, key, defaultValue);
        this.remote = remote;
        if(max != null && min != null) {//do we need to check max, min?
            if(max.compareTo(min) < 0) //max less than min?
                throw new IllegalArgumentException("max less than min");
        }
        MAX_VALUE = max;
        MIN_VALUE = min;
        setValueInternal(getValueAsString()); // performs normalization
    }

    /**
     * Set new property value
     * @param value new property value
     */
    @Override
    protected void setValueInternal(String value) {
        if(remote) {
            assert MAX_VALUE != null : "remote setting created with no max";
            assert MIN_VALUE != null : "remote setting created with no min";
        }
        value = normalizeValue(value);
        super.setValueInternal(value);
    }


    /**
     * Normalizes a value to an acceptable value for this setting.
     */
    protected String normalizeValue(String value) {
        Comparable<T> comparableValue = null;
        try {
            comparableValue = convertToComparable(value);
        } catch (NumberFormatException e) {
            return DEFAULT_VALUE;
        }
        if (MAX_VALUE != null && comparableValue.compareTo(MAX_VALUE) > 0) {
            return MAX_VALUE.toString();
        } else if (MIN_VALUE != null && comparableValue.compareTo(MIN_VALUE) < 0) {
            return MIN_VALUE.toString();
        }
        return value;
    }

    /** Converts a String to a Comparable of the same type as MAX_VALUE and MIN_VALUE.     */
    abstract protected Comparable<T> convertToComparable(String value);
    
}
