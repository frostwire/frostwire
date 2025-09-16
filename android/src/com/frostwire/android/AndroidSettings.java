/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.frostwire.android;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.platform.AppSettings;

/**
 * @author gubatron
 * @author aldenml
 */
final class AndroidSettings implements AppSettings {

    @Override
    public String string(String key) {
        return ConfigurationManager.instance().getString(key);
    }

    @Override
    public void string(String key, String value) {
        ConfigurationManager.instance().setString(key, value);
    }

    @Override
    public int int32(String key) {
        return ConfigurationManager.instance().getInt(key);
    }

    @Override
    public void int32(String key, int value) {
        ConfigurationManager.instance().setInt(key, value);
    }

    @Override
    public long int64(String key) {
        return ConfigurationManager.instance().getLong(key);
    }

    @Override
    public void int64(String key, long value) {
        ConfigurationManager.instance().setLong(key, value);
    }

    @Override
    public boolean bool(String key) {
        return ConfigurationManager.instance().getBoolean(key);
    }

    @Override
    public void bool(String key, boolean value) {
        ConfigurationManager.instance().setBoolean(key, value);
    }
}
