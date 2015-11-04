package org.limewire.setting;

import org.limewire.setting.evt.SettingListener;

/**
 * Defines the interface for a setting that can be used
 * within this package.
 */
public interface Setting {

    /**
     * Registers a {@link SettingListener}
     */
    public void addSettingListener(SettingListener l);

    /**
     * Removes a {@link SettingListener}
     */
    public void removeSettingListener(SettingListener l);

    /**
     * Returns all {@link SettingListener}s or null of there are none
     */
    public SettingListener[] getSettingListeners();

    /**
     * Reload value from properties object
     */
    public void reload();

    /**
     * Revert to the default value.
     * It is critically important that the DEFAULT_VALUE is valid,
     * otherwise an infinite loop will be encountered when revertToDefault
     * is called, as invalid values call revertToDefault.
     * Because default values are hard-coded into the program, this is okay.
     */
    public boolean revertToDefault();

    /**
     * Determines whether or not this value should always be saved to disk.
     */
    public boolean shouldAlwaysSave();

    /**
     * Sets whether or not this setting should always save, even if
     * it is default.
     * Returns this so it can be used during assignment.
     */
    public Setting setAlwaysSave(boolean alwaysSave);

    /**
     * Sets whether or not this setting should be reported in bug reports.
     */
    public Setting setPrivate(boolean isPrivate);

    /**
     * Determines whether or not a setting is private.
     */
    public boolean isPrivate();

    /**
     * Determines whether or not the current value is the default value.
     */
    public boolean isDefault();

    /** Get the key for this setting.     */
    public String getKey();

    /**  Returns the value as stored in the properties file.    */
    public String getValueAsString();
}