package org.limewire.setting.evt;

import org.limewire.setting.SettingsGroupManager;

/**
 * A listener for {@link SettingsGroupManager}s
 */
public interface SettingsGroupManagerListener {
    
    /**
     * Invoked when a {@link SettingsGroupManager} changed its state
     */
    public void handleGroupManagerEvent(SettingsGroupManagerEvent evt);
}
