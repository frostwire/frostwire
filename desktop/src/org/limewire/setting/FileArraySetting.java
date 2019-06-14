package org.limewire.setting;

import org.limewire.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Provides a {@link File} setting value. As a subclass
 * of <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>FileArraySetting</code> object with a
 * {@link SettingsFactory#createFileArraySetting(String, File[])}.
 */
public class FileArraySetting extends AbstractSetting {
    private File[] value;

    /**
     * Creates a new <tt>FileArraySetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the constant key to use for the setting
     * @param defaultValue the default value to use for the setting
     */
    FileArraySetting(Properties defaultProps, Properties props, String key,
                     File[] defaultValue) {
        super(defaultProps, props, key, decode(defaultValue));
        setPrivate(true);
    }

    /**
     * Splits the string into an Array
     */
    private static File[] encode(String src) {
        if (src == null || src.length() == 0) {
            return (new File[0]);
        }
        StringTokenizer tokenizer = new StringTokenizer(src, ";");
        File[] dirs = new File[tokenizer.countTokens()];
        for (int i = 0; i < dirs.length; i++) {
            dirs[i] = new File(tokenizer.nextToken());
        }
        return dirs;
    }

    /**
     * Separates each field of the array by a semicolon
     */
    private static String decode(File[] src) {
        if (src == null || src.length == 0) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        for (File file : src) {
            buffer.append(file.getAbsolutePath()).append(';');
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
    @SuppressWarnings("unused")
    public File[] getValue() {
        return value;
    }

    /**
     * Mutator for this setting.
     *
     * @param value the value to store
     */
    private synchronized void setValue(File[] value) {
        setValueInternal(decode(value));
    }

    /**
     * Mutator for this setting.
     *
     * @param file file to the array.
     */
    @SuppressWarnings("unused")
    public synchronized void add(File file) {
        if (file == null)
            return;
        File[] newValue = new File[value.length + 1];
        System.arraycopy(value, 0, newValue, 0, value.length);
        newValue[value.length] = file;
        setValue(newValue);
    }

    /**
     * Mutator for this setting.
     *
     * @param file Remove file from the array, if it exists.
     * @return false when the array does not contain the file or when the
     * file is <code>null</code>
     */
    @SuppressWarnings("unused")
    public synchronized boolean remove(File file) {
        if (file == null)
            return false;
        int index = indexOf(file);
        if (index == -1) {
            return false;
        }
        File[] newValue = new File[value.length - 1];
        //  copy first half, up to first occurrence's index
        System.arraycopy(value, 0, newValue, 0, index);
        //  copy second half, for the length of the rest of the array
        System.arraycopy(value, index + 1, newValue, index, value.length - index - 1);
        setValue(newValue);
        return true;
    }

    /**
     * Returns true if the given file is contained in this array.
     */
    @SuppressWarnings("unused")
    public synchronized boolean contains(File file) {
        return indexOf(file) >= 0;
    }

    /**
     * Returns the index of the given file in this array, -1 if file is not found.
     */
    private synchronized int indexOf(File file) {
        if (file == null)
            return -1;
        for (int i = 0; i < value.length; i++) {
            try {
                if ((FileUtils.getCanonicalFile(value[i])).equals(FileUtils.getCanonicalFile(file)))
                    return i;
            } catch (IOException ignored) {
            }
        }
        return -1;
    }

    /**
     * Returns the length of the array.
     */
    @SuppressWarnings("unused")
    public synchronized int length() {
        return value.length;
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    @SuppressWarnings("unused")
    protected synchronized void loadValue(String sValue) {
        value = encode(sValue);
    }
}
