package org.limewire.setting;

/**
 * Defines an abstract class to reload and save a value, revert to a
 * default value and mark a value as always saving.
 * <p>
 * If saving is turned off, then underlying settings will not be saved. If
 * saving is turned on, then underlying settings still have the option not
 * to save settings to disk.
 */
public interface SettingsGroup {
    /**
     * Loads Settings from disk
     */
    void reload();

    /**
     * Saves the current Settings to disk
     */
    boolean save();

    /**
     * Reverts all Settings to their default values
     */
    boolean revertToDefault();
}