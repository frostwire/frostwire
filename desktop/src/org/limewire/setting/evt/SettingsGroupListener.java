package org.limewire.setting.evt;

import org.limewire.setting.SettingsGroup;


/**
 * A listener for {@link SettingsGroup}
 */
public interface SettingsGroupListener {
    
    /**
     * Invoked when a {@link SettingsGroup} instance changed its state
     */
    public void settingsGroupChanged(SettingsGroupEvent evt);
}
