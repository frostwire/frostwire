package org.limewire.setting;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;


/**
 * Provides a {@link File} <code>Set</code> setting value. As a subclass of 
 * <code>Setting</code>, the setting has a key.
 * <p> 
 * <code>FileSetSetting</code> class includes methods to add/remove 
 * <code>File</code>s, get <code>File</code> values as an array and return 
 * the length of the <code>File</code> set.
 * <p>
 * Create a <code>FileSetSetting</code> object with a 
 * {@link SettingsFactory#createFileSetSetting(String, File[])}.
 */
 
public class FileSetSetting extends AbstractSetting {
    
    private Set<File> value;

	/**
	 * Creates a new <tt>FileSetSetting</tt> instance with the specified
	 * key and default value.
	 *
	 * @param key the constant key to use for the setting
	 * @param defaultValue the default value to use for the setting
	 */
	FileSetSetting(Properties defaultProps, Properties props, String key, File[] defaultValue) {
        super(defaultProps, props, key, decode(new HashSet<File>(Arrays.asList(defaultValue))));
        setPrivate(true);
	}

	/**
	 * Returns the value of this setting.
	 * 
	 * @return the value of this setting
	 */
	public Set<File> getValue() {
        return value;
	}
	
	/** Gets the value as an array. */
	public synchronized File[] getValueAsArray() {
	    return value.toArray(new File[value.size()]);
    }

	/**
	 * Mutator for this setting.
	 *
	 * @param value the value to store
	 */
	public void setValue(Set<? extends File> value) {
	    setValueInternal(decode(value));
	}

	/**
	 * Mutator for this setting.
	 * @param file file to add to the array.
	 */
	public synchronized void add(File file) {
	    value.add(file);
	    setValue(value);
	}
    
	/**
	 * Mutator for this setting.
	 *
	 * @param file Remove file from the array, if it exists.
	 * @return false when the array does not contain the file or when the
	 * file is <code>null</code> 
	 */
	public synchronized boolean remove(File file) {
	    if(value.remove(file)) {
	        setValue(value);
	        return true;
	    } else {
	        return false;
	    }
	}
	
	public synchronized void removeAll() {
	    value.clear();
	    setValue(value);
	}
    
	/** Returns true if the given file is contained in this array. */
	public synchronized boolean contains(File file) {
	    return value.contains(file);
	}
	
	/** Returns the length of the array. */
	public synchronized int length() {
	    return value.size();
	}
	
    /** Load value from property string value
     * @param sValue property string value
     *
     */
    protected synchronized void loadValue(String sValue) {
		value = encode(sValue);
    }
    
    /** Splits the string into a Set    */
    private static final Set<File> encode(String src) {
        if (src == null || src.length()==0)
            return new HashSet<File>();
        
        StringTokenizer tokenizer = new StringTokenizer(src, ";");
        int size = tokenizer.countTokens();
        Set<File> set = new HashSet<File>();
        for(int i = 0; i < size; i++)
            set.add(new File(tokenizer.nextToken()));
        return set;
    }
    
    /** Separates each field of the array by a semicolon     */
    private static final String decode(Set<? extends File> src) {
        if (src == null || src.isEmpty())
            return "";
        
        StringBuilder buffer = new StringBuilder();
        for(File file : src) {
            buffer.append(file.getAbsolutePath()).append(';');
        }
        
        if (buffer.length() > 0) {
            buffer.setLength(buffer.length()-1);
        }  
        return buffer.toString();
    }

	/** Removes non-existent members.	 */
	public synchronized void clean() {
	    for(Iterator<File> i = value.iterator(); i.hasNext(); ) {
	        File next = i.next();
	        if(!next.exists())
	            i.remove();
        }
        
	    setValue(value);
    }
}