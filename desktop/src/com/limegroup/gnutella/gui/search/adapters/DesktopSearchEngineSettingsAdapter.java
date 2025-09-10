/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.search.adapters;

import com.frostwire.search.SearchEngineSettingsAdapter;
import org.limewire.setting.BooleanSetting;

import java.util.Map;

/**
 * Desktop implementation of SearchEngineSettingsAdapter that uses
 * BooleanSetting objects for settings persistence.
 * 
 * @author gubatron
 * @author aldenml
 */
public class DesktopSearchEngineSettingsAdapter implements SearchEngineSettingsAdapter {
    
    private final Map<String, BooleanSetting> settingsMap;
    
    public DesktopSearchEngineSettingsAdapter(Map<String, BooleanSetting> settingsMap) {
        this.settingsMap = settingsMap;
    }
    
    @Override
    public boolean isEnabled(String preferenceKey) {
        BooleanSetting setting = settingsMap.get(preferenceKey);
        return setting != null ? setting.getValue() : false;
    }
    
    @Override
    public void setEnabled(String preferenceKey, boolean enabled) {
        BooleanSetting setting = settingsMap.get(preferenceKey);
        if (setting != null) {
            setting.setValue(enabled);
        }
    }
}