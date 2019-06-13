package org.limewire.setting;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * TODO: Make all these SetSetting classes extend from an AbstractSetSetting<T> class
 * <p>
 * Provides a <code>String</code> <code>Set</code> setting value. As a
 * subclass of <code>Setting</code>, the setting has a key.
 * <p>
 * <code>StringSetSetting</code> class includes methods to add/remove
 * <code>String</code>s, get <code>String</code> values as an array and return
 * the number of <code>String</code>s. Unlike {@link StringArraySetting}, you
 * can add and remove individual <code>String</code>s to the set while
 * maintaining the existing set.
 * <p>
 * Create a <code>StringSetSetting</code> object with a
 * {@link SettingsFactory#createStringSetSetting(String, String)}.
 */
public class StringSetSetting extends AbstractSetting {
    private Set<String> value;

    StringSetSetting(Properties defaultProps, Properties props,
                     String key, String defaultValue) {
        super(defaultProps, props, key, defaultValue);
    }

    /**
     * Splits the string into a Set
     */
    private static Set<String> encode(String src) {
        if (src == null || src.length() == 0)
            return new HashSet<>();
        StringTokenizer tokenizer = new StringTokenizer(src, ";");
        int size = tokenizer.countTokens();
        Set<String> set = new HashSet<>();
        for (int i = 0; i < size; i++)
            set.add(tokenizer.nextToken());
        return set;
    }

    /**
     * Separates each field of the array by a semicolon
     */
    private static String decode(Set<String> src) {
        if (src == null || src.isEmpty())
            return "";
        StringBuilder buffer = new StringBuilder();
        for (String str : src) {
            buffer.append(str).append(';');
        }
        if (buffer.length() > 0) {
            buffer.setLength(buffer.length() - 1);
        }
        return buffer.toString();
    }

    /**
     * Returns the value of this setting.
     *
     * @return the value of this setting
     */
    public synchronized Set<String> getValue() {
        return value;
    }

    /**
     * Mutator for this setting.
     *
     * @param value the value to store
     */
    public synchronized void setValue(Set<String> value) {
        setValueInternal(decode(value));
    }

    /**
     * Gets the value as an array.
     */
    public synchronized String[] getValueAsArray() {
        return value.toArray(new String[0]);
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    protected synchronized void loadValue(String sValue) {
        value = encode(sValue);
    }

    public synchronized boolean add(String s) {
        if (value.add(s)) {
            setValueInternal(decode(value));
            return true;
        }
        return false;
    }

    public synchronized boolean remove(String s) {
        if (value.remove(s)) {
            setValueInternal(decode(value));
            return true;
        }
        return false;
    }

    public synchronized boolean contains(String s) {
        return value.contains(s);
    }
}