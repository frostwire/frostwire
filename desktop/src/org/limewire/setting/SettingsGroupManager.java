package org.limewire.setting;

import org.limewire.concurrent.ExecutorsHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

/**
 * Groups all {@link SettingsGroup} objects in one location to reload, revert to
 * a default value, save, or mark as save-able all <code>Settings</code>
 * objects at once.
 */
public final class SettingsGroupManager {
    /**
     * The singleton instance of SettingsHandler
     */
    private static final SettingsGroupManager INSTANCE = new SettingsGroupManager();
    /**
     * A list of Settings this SettingsHandler is managing
     */
    private final Collection<SettingsGroup> PROPS = Collections.synchronizedList(new ArrayList<>());
    /**
     * The Executor for the Events
     */
    private final Executor executor = ExecutorsHelper.newFixedSizeThreadPool(1, "SettingsHandlerEventDispatcher");

    // never instantiate this class.
    private SettingsGroupManager() {
    }

    /**
     * Returns the singleton instance of the SettingsHandler
     */
    public static SettingsGroupManager instance() {
        return INSTANCE;
    }

    /**
     * Adds a settings class to the list of factories that
     * this handler will act upon.
     */
    void addSettingsGroup(SettingsGroup group) {
        PROPS.add(group);
    }

    /**
     * Reload settings from both the property and configuration files.
     */
    public void reload() {
        synchronized (PROPS) {
            for (SettingsGroup group : PROPS) {
                group.reload();
            }
        }
    }

    /**
     * Save property settings to the property file.
     */
    public boolean save() {
        boolean any = false;
        synchronized (PROPS) {
            for (SettingsGroup group : PROPS) {
                any |= group.save();
            }
        }
        return any;
    }

    /**
     * Revert all settings to their default value.
     */
    public boolean revertToDefault() {
        boolean any = false;
        synchronized (PROPS) {
            for (SettingsGroup group : PROPS) {
                any |= group.revertToDefault();
            }
        }
        return any;
    }

    /**
     * Fires a event on the Executor Thread
     */
    void execute(Runnable evt) {
        executor.execute(evt);
    }
}