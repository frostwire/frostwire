package org.limewire.setting.evt;

import org.limewire.setting.AbstractSetting;

/**
 * A listener for {@link AbstractSetting}s
 */
public interface SettingListener {
    
    /**
     * Invoked when a {@link AbstractSetting} changed its state
     */
    public void settingChanged(SettingEvent evt);
}
