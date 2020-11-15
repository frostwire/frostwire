package org.limewire.setting;

import java.util.Properties;

/**
 * Provides a character array setting value.
 * As a subclass of <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>CharArraySetting</code> object with a
 * {@link SettingsFactory#createCharArraySetting(String, char[])}.
 */
public final class CharArraySetting extends AbstractSetting {
    /**
     * Cached value.
     */
    private char[] value;

    /**
     * Creates a new <tt>SettingBool</tt> instance with the specified
     * key and default value.
     *
     * @param defaultProps the default properties
     * @param props        the set properties
     * @param key          the constant key to use for the setting
     * @param defaultValue the default value to use for the setting
     */
    CharArraySetting(Properties defaultProps, Properties props,
                     String key, char[] defaultValue) {
        super(defaultProps, props, key, new String(defaultValue));
    }

    /**
     * Returns the value of this setting.
     */
    public char[] getValue() {
        return value;
    }

    /**
     * Mutator for this setting.
     *
     * @param value the value to store
     */
    public void setValue(char[] value) {
        setValueInternal(new String(value));
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    protected void loadValue(String sValue) {
        value = sValue.trim().toCharArray();
    }
}
