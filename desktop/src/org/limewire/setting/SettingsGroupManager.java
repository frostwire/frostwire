package org.limewire.setting;

import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.setting.evt.SettingsGroupManagerEvent;
import org.limewire.setting.evt.SettingsGroupManagerEvent.EventType;
import org.limewire.setting.evt.SettingsGroupManagerListener;

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
     * Returns all {@link SettingsGroupManagerListener}s or null if there are none
     */
    private SettingsGroupManagerListener[] getSettingsHandlerListeners() {
        return null;
    }

    /**
     * Adds a settings class to the list of factories that
     * this handler will act upon.
     */
    void addSettingsGroup(SettingsGroup group) {
        PROPS.add(group);
        fireSettingsHandlerEvent(EventType.SETTINGS_GROUP_ADDED, group);
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
        fireSettingsHandlerEvent(EventType.RELOAD, null);
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
        if (any) {
            fireSettingsHandlerEvent(EventType.SAVE, null);
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
        if (any) {
            fireSettingsHandlerEvent(EventType.REVERT_TO_DEFAULT, null);
        }
        return any;
    }

    /**
     * Fires a SettingsHandlerEvent
     */
    private void fireSettingsHandlerEvent(EventType type, SettingsGroup group) {
        fireSettingsHandlerEvent(new SettingsGroupManagerEvent(type, this, group));
    }

    /**
     * Fires a SettingsHandlerEvent
     */
    private void fireSettingsHandlerEvent(final SettingsGroupManagerEvent evt) {
        if (evt == null) {
            throw new NullPointerException("SettingsHandlerEvent is null");
        }
    }

    /**
     * Fires a event on the Executor Thread
     */
    void execute(Runnable evt) {
        executor.execute(evt);
    }
}