/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
