package org.limewire.setting;

import org.limewire.setting.evt.SettingsGroupEvent;
import org.limewire.setting.evt.SettingsGroupEvent.EventType;
import org.limewire.setting.evt.SettingsGroupListener;

import java.util.Collection;

/**
 * An abstract implementation of SettingsGroup
 */
abstract class AbstractSettingsGroup implements SettingsGroup {
    /**
     * List of {@link SettingsGroupListener}s
     */
    private Collection<SettingsGroupListener> listeners;

    /**
     * Returns all {@link SettingsGroupListener}s or null if there are none
     */
    private SettingsGroupListener[] getSettingsGroupListeners() {
        synchronized (this) {
            if (listeners == null) {
                return null;
            }
            return listeners.toArray(new SettingsGroupListener[0]);
        }
    }

    /* (non-Javadoc)
     * @see org.limewire.setting.SettingsGroup#getShouldSave()
     */
    boolean getShouldSave() {
        return true;
    }

    /**
     * Fires a SettingsEvent
     */
    void fireSettingsEvent(EventType type) {
        fireSettingsEvent(new SettingsGroupEvent(type, this));
    }

    /**
     * Fires a SettingsEvent
     */
    private void fireSettingsEvent(final SettingsGroupEvent evt) {
        if (evt == null) {
            throw new NullPointerException("SettingsEvent is null");
        }
        final SettingsGroupListener[] listeners = getSettingsGroupListeners();
        if (listeners != null) {
            Runnable command = () -> {
                for (SettingsGroupListener l : listeners) {
                    l.settingsGroupChanged(evt);
                }
            };
            SettingsGroupManager.instance().execute(command);
        }
    }
}
