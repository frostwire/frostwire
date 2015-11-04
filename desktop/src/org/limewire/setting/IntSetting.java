package org.limewire.setting;

import java.util.Properties;


/**
 * Provides an int setting value. As a subclass of <code>Setting</code>, 
 * the setting has a key.
 * <p>
 * Create a <code>IntSetting</code> object with a 
 * {@link SettingsFactory#createIntSetting(String, int)}.
 */
public final class IntSetting extends AbstractNumberSetting<Integer> {
    
    private int value;

	/**
	 * Creates a new <tt>IntSetting</tt> instance with the specified
	 * key and default value.
	 */
	IntSetting(Properties defaultProps, Properties props, String key, 
                                                              int defaultInt) {
        super(defaultProps, props, key, String.valueOf(defaultInt), 
                                                            false, null, null);
	}

    /**
     * Constructor for Settable setting which specifies a remote-key and max and
     * min permissible values.
     */
	IntSetting(Properties defaultProps, Properties props, String key, 
          int defaultInt, int minRemoteVal, int maxRemoteVal) {
		super(defaultProps, props, key, String.valueOf(defaultInt), true,
                            new Integer(minRemoteVal), new Integer(maxRemoteVal));
    }
        
	/**
	 * Returns the value of this setting.
	 * 
	 * @return the value of this setting
	 */
	public int getValue() {
        return value;
	}

	/**
	 * Mutator for this setting.
	 *
	 * @param value the value to store
	 */
	public void setValue(int value) {
	    setValueInternal(String.valueOf(value));
	}
    
    /** Load value from property string value
     * @param sValue property string value
     *
     */
    protected void loadValue(String sValue) {
        try {
            value = Integer.parseInt(sValue.trim());
        } catch(NumberFormatException nfe) {
            revertToDefault();
        }
    }
    
    protected Comparable<Integer> convertToComparable(String value) {
        return new Integer(value);
    }
    
    @Override
    public Integer getDefaultValue() {
    	Object valueObj = super.getDefaultValue();
    	
        try {
            return Integer.parseInt(valueObj.toString().trim());
        } catch (Exception e) {
        	return null;
        }
    }
}
