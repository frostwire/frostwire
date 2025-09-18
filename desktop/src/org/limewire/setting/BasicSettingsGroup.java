/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.limewire.setting;

import org.limewire.setting.evt.SettingsGroupEvent.EventType;

import java.io.File;
import java.util.Properties;

/**
 * Gives basic features including get, reload and save for a
 * {@link SettingsFactory}.
 */
public class BasicSettingsGroup extends AbstractSettingsGroup {
    /**
     * Constant for the <tt>SettingsFactory</tt> that subclasses can use
     * to create new settings which will be stored in the properties file.
     */
    private final SettingsFactory FACTORY;

    /**
     * Basic constructor that creates the FACTORY and PROPS_FILE.
     */
    protected BasicSettingsGroup(File settingsFile, String header) {
        FACTORY = new SettingsFactory(settingsFile, header);
        SettingsGroupManager.instance().addSettingsGroup(this);
    }

    /**
     * Returns the <tt>Properties</tt> instance that stores all settings.
     *
     * @return the <tt>Properties</tt> instance for storing settings
     */
    @SuppressWarnings("unused")
    public Properties getProperties() {
        return FACTORY.getProperties();
    }

    /**
     * Returns the <tt>SettingsFactory</tt> instance that stores the properties.
     */
    public SettingsFactory getFactory() {
        return FACTORY;
    }

    /**
     * reload settings from both the property and configuration files
     */
    public void reload() {
        FACTORY.reload();
        fireSettingsEvent(EventType.RELOAD);
    }

    /**
     * Save property settings to the property file
     */
    public boolean save() {
        if (getShouldSave()) {
            FACTORY.save();
            fireSettingsEvent(EventType.SAVE);
            return true;
        }
        return false;
    }

    /**
     * Revert all settings to their default value
     */
    public boolean revertToDefault() {
        if (FACTORY.revertToDefault()) {
            fireSettingsEvent(EventType.REVERT_TO_DEFAULT);
            return true;
        }
        return false;
    }

    /**
     * Used to find any setting based on the key in the appropriate props file
     */
    @SuppressWarnings("unused")
    public Setting getSetting(String key) {
        synchronized (FACTORY) {
            for (Setting currSetting : FACTORY) {
                if (currSetting.getKey().equals(key))
                    return currSetting;
            }
        }
        return null; //unable the find the setting we are looking for
    }

    public String toString() {
        return FACTORY.toString();
    }
}
