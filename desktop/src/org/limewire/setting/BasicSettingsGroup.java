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
