/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.light.platform;

import com.frostwire.light.ConfigurationManager;
import com.frostwire.platform.AppSettings;

public class LightSettings implements AppSettings {
    private final ConfigurationManager configurationManager;

    LightSettings(final ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Override
    public String string(String key) {
        return configurationManager.getString(key);
    }

    @Override
    public void string(String key, String value) {
        configurationManager.setString(key, value);
    }

    @Override
    public int int32(String key) {
        return configurationManager.getInt(key);
    }

    @Override
    public void int32(String key, int value) {
        configurationManager.setInt(key, value);
    }

    @Override
    public long int64(String key) {
        return configurationManager.getLong(key);
    }

    @Override
    public void int64(String key, long value) {
        configurationManager.setLong(key, value);
    }

    @Override
    public boolean bool(String key) {
        return configurationManager.getBoolean(key);
    }

    @Override
    public void bool(String key, boolean value) {
        configurationManager.setBoolean(key, value);
    }
}
