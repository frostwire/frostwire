/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.limewire.setting;

import org.apache.commons.io.IOUtils;
import org.limewire.util.FileUtils;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

/**
 * Coordinates the creating, storing and reloading of persistent data to and
 * from disk for {@link AbstractSetting} objects. Each <code>Setting</code> creation
 * method takes the name of the key and the default value, and all settings
 * are typed. Since duplicate keys aren't allowed, you must choose a unique
 * string for your setting key name, otherwise an exception,
 * <code>IllegalArgumentException</code> is thrown.
 * <p>
 * When you add a new <code>Setting</code> subclass, add a public synchronized
 * method to <code>SettingsFactory</code> to create an instance of the setting.
 * For example, subclass {@link IntSetting}, <code>SettingsFactory</code> has
 * {@link #createIntSetting(String, int)} and
 * <p>
 * An example of creating an {@link IntSetting} that uses setting.txt, without the key
 * MAX_MESSAGE_SIZE previously included:
 * <pre>
 * File f = new File("setting.txt");
 * SettingsFactory sf = new SettingsFactory(f);
 *
 * IntSetting intsetting = sf.createIntSetting("MAX_MESSAGE_SIZE", 1492);
 *
 * System.out.println("1: " + intsetting.getValue());
 * intsetting.setValue("2984");
 * System.out.println("2: " + intsetting.getValue());
 * sf.save();
 *
 * Output:
 * 1: 1492
 * 2: 2984
 * </pre>
 * <p>
 * With the call sf.save(), setting.txt now includes:
 * <pre>
 * MAX_MESSAGE_SIZE=2984
 * </pre>
 * Additionally, the value stored in disk is loaded for each key
 * you specify regardless of the default value in the create method. For example
 * with "MAX_MESSAGE_SIZE=2984" stored in setting.txt:
 * <pre>
 * File f = new File("setting.txt");
 * SettingsFactory sf = new SettingsFactory(f);
 *
 * IntSetting intsetting = sf.createIntSetting("MAX_MESSAGE_SIZE", 0);
 * System.out.println(intsetting.getValue());
 * sf.save();
 * Output:
 * 2984
 * </pre>
 * font.txt still includes:
 * <pre>
 * MAX_MESSAGE_SIZE=2984
 * </pre>
 * If setting.txt didn't have the key MAX_MESSAGE_SIZE prior to the
 * <code>createIntSetting</code> call, then the MAX_MESSAGE_SIZE is 0.
 */
public final class SettingsFactory implements Iterable<AbstractSetting> {
    /**
     * Time interval, after which the accumulated information expires
     */
    private static final long EXPIRY_INTERVAL = 14 * 24 * 60 * 60 * 1000; //14 days
    /**
     * Marked true in the event of an error in the load/save of any settings file
     */
    private static boolean loadSaveFailureEncountered = false;
    /**
     * The header written to the settings file.
     */
    private final String HEADING;
    /**
     * <tt>Properties</tt> instance for the default values.
     */
    private final Properties DEFAULT_PROPS = new Properties();
    /**
     * The <tt>Properties</tt> instance containing all settings.
     */
    private final Properties PROPS = new Properties(DEFAULT_PROPS);
    /**
     * An internal Setting to store the last expire time
     */
    private LongSetting LAST_EXPIRE_TIME = null;
    /**
     * <tt>File</tt> object from which settings are loaded and saved
     */
    private final File SETTINGS_FILE;
    /**
     * List of all settings associated with this factory
     * LOCKING: must hold this monitor
     */
    private final ArrayList<AbstractSetting> settings = new ArrayList<>(10);
    /**
     * Whether or not expirable settings have expired.
     */
    private boolean expired = false;

    /**
     * Creates a new <tt>SettingsFactory</tt> instance with the specified file
     * to read from and write to.
     *
     * @param settingsFile the file to read from and to write to
     * @param heading      heading to use when writing property file
     */
    public SettingsFactory(File settingsFile, String heading) {
        SETTINGS_FILE = settingsFile;
        if (SETTINGS_FILE.isDirectory()) SETTINGS_FILE.delete();
        HEADING = heading;
        reload();
    }

    /**
     * Indicated if a failure has occurred for delayed reporting
     */
    public static boolean hasLoadSaveFailure() {
        return loadSaveFailureEncountered;
    }

    /**
     * Saves a failure event for delayed reporting
     */
    private static void markFailure() {
        loadSaveFailureEncountered = true;
    }

    /**
     * Resets the failure flag
     */
    public static void resetLoadSaveFailure() {
        loadSaveFailureEncountered = false;
    }

    /**
     * Returns the iterator over the settings stored in this factory.
     * <p>
     * LOCKING: The caller must ensure that this factory's monitor
     * is held while iterating over the iterator.
     */
    public synchronized Iterator<AbstractSetting> iterator() {
        return settings.iterator();
    }

    /**
     * Reloads the settings with the predefined settings file from
     * disk.
     */
    public synchronized void reload() {
        // If the props file doesn't exist, the init sequence will prompt
        // the user for the required values, so return.  If this is not 
        // loading frostwire.props, but rather something like themes.txt,
        // we also return, as attempting to load an invalid file will
        // not do any good.
        if (!SETTINGS_FILE.isFile()) {
            setExpireValue();
            return;
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(SETTINGS_FILE);
            try {
                PROPS.load(fis);
            } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
                // Ignored -- Use best guess
            } catch (IOException e) {
                // Serious Problems --- Use defaults
                markFailure();
            }
        } catch (FileNotFoundException e) {
            if (SETTINGS_FILE.exists()) {
                markFailure();
            }
        } finally {
            IOUtils.closeQuietly(fis);
        }
        // Reload all setting values
        for (Setting set : settings)
            set.reload();
        setExpireValue();
    }

    /**
     * Sets the last expire time if not already set.
     */
    private synchronized void setExpireValue() {
        // Note: this has only an impact on launch time when this
        // method is called by the constructor of this class!
        if (LAST_EXPIRE_TIME == null) {
            LAST_EXPIRE_TIME = createLongSetting("LAST_EXPIRE_TIME", 0);
            // Set flag to true if Settings are expired. See
            // createExpirable<whatever>Setting at the bottom
            expired =
                    (LAST_EXPIRE_TIME.getValue() + EXPIRY_INTERVAL <
                            System.currentTimeMillis());
            if (expired)
                LAST_EXPIRE_TIME.setValue(System.currentTimeMillis());
        }
    }

    /**
     * Reverts all settings to their factory defaults.
     */
    public synchronized boolean revertToDefault() {
        boolean any = false;
        for (Setting setting : settings) {
            any |= setting.revertToDefault();
        }
        return any;
    }

    /**
     * Save setting information to property file
     * We want to NOT save any properties which are the default value,
     * as well as any older properties that are no longer in use.
     * To avoid having to manually encode the file, we clone
     * the existing properties and manually remove the ones
     * which are default and aren't required to be saved.
     * It is important to do it this way (as opposed to creating a new
     * properties object and adding only those that should be saved
     * or aren't default) because 'adding' properties may fail if
     * certain settings classes haven't been statically loaded yet.
     * (Note that we cannot use 'store' since it's only available in 1.2)
     */
    public synchronized void save() {
        Properties toSave = (Properties) PROPS.clone();
        //Add any settings which require saving or aren't default
        for (Setting set : settings) {
            if (!set.shouldAlwaysSave() && set.isDefault())
                toSave.remove(set.getKey());
        }
        OutputStream out = null;
        try {
            // some bugs were reported where the settings file was a directory.
            if (SETTINGS_FILE.isDirectory())
                SETTINGS_FILE.delete();
            // some bugs were reported where the settings file's parent
            // directory was deleted.
            File parent = SETTINGS_FILE.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            FileUtils.setWriteable(SETTINGS_FILE);
            if (SETTINGS_FILE.exists() && !SETTINGS_FILE.canRead()) {
                SETTINGS_FILE.delete();
            }
            try {
                out = new BufferedOutputStream(new FileOutputStream(SETTINGS_FILE));
            } catch (IOException ioe) {
                // Try again.
                if (SETTINGS_FILE.exists()) {
                    SETTINGS_FILE.delete();
                    out = new BufferedOutputStream(new FileOutputStream(SETTINGS_FILE));
                }
            }
            if (out != null) {
                // save the properties to disk.
                toSave.store(out, HEADING);
            } else {
                markFailure();
            }
        } catch (IOException e) {
            markFailure();
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    public String toString() {
        return PROPS.toString();
    }

    /**
     * Return settings properties with current values
     */
    public Properties getProperties() {
        return PROPS;
    }

    /**
     * Creates a new <tt>StringSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized StringSetting createStringSetting(String key,
                                                          String defaultValue) {
        StringSetting result =
                new StringSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>BooleanSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized BooleanSetting createBooleanSetting(String key,
                                                            boolean defaultValue) {
        BooleanSetting result =
                new BooleanSettingImpl(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal((AbstractSetting) result);
        return result;
    }

    /**
     * Creates a new <tt>IntSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized IntSetting createIntSetting(String key,
                                                    int defaultValue) {
        IntSetting result =
                new IntSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>ByteSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    synchronized ByteSetting createByteSetting(String key,
                                               byte defaultValue) {
        ByteSetting result =
                new ByteSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>LongSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized LongSetting createLongSetting(String key,
                                                      long defaultValue) {
        LongSetting result =
                new LongSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>FileSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized FileSetting createFileSetting(String key,
                                                      File defaultValue) {
        String parentString = defaultValue.getParent();
        if (parentString != null) {
            File parent = new File(parentString);
            if (!parent.isDirectory()) {
                parent.mkdirs();
            }
        }
        FileSetting result =
                new FileSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>ColorSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    synchronized ColorSetting createColorSetting(String key,
                                                 Color defaultValue) {
        ColorSetting result =
                ColorSetting.createColorSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>CharArraySetting</tt> instance for a character array
     * setting with the specified key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized CharArraySetting createCharArraySetting(String key,
                                                                char[] defaultValue) {
        CharArraySetting result = new CharArraySetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>FloatSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized FloatSetting createFloatSetting(String key,
                                                        float defaultValue) {
        FloatSetting result =
                new FloatSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>StringArraySetting</tt> instance for a String array
     * setting with the specified key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized StringArraySetting
    createStringArraySetting(String key, String[] defaultValue) {
        StringArraySetting result =
                new StringArraySetting(DEFAULT_PROPS, PROPS, key,
                        defaultValue);
        handleSettingInternal(result);
        return result;
    }

    synchronized StringSetSetting
    createStringSetSetting(String key, String defaultValue) {
        StringSetSetting result =
                new StringSetSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>FileArraySetting</tt> instance for a File array
     * setting with the specified key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    synchronized FileArraySetting createFileArraySetting(String key, File[] defaultValue) {
        FileArraySetting result =
                new FileArraySetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new <tt>FileSetSetting</tt> instance for a File array
     * setting with the specified key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    public synchronized FileSetSetting createFileSetSetting(String key, File[] defaultValue) {
        FileSetSetting result = new FileSetSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    /**
     * Creates a new expiring <tt>IntSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    @SuppressWarnings("unused")
    public synchronized IntSetting createExpirableIntSetting(String key,
                                                             int defaultValue) {
        IntSetting result = createIntSetting(key, defaultValue);
        if (expired)
            result.revertToDefault();
        return result;
    }

    /**
     * Creates a new <tt>FontNameSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key          the key for the setting
     * @param defaultValue the default value for the setting
     */
    synchronized FontNameSetting createFontNameSetting(String key,
                                                       String defaultValue) {
        FontNameSetting result =
                new FontNameSetting(DEFAULT_PROPS, PROPS, key, defaultValue);
        handleSettingInternal(result);
        return result;
    }

    private synchronized void handleSettingInternal(AbstractSetting setting) {
        settings.add(setting);
        setting.reload();
    }
}
