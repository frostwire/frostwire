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

package com.frostwire.desktop;

import com.frostwire.platform.AppSettings;

/**
 * @author gubatron
 * @author aldenml
 */
final class DesktopSettings implements AppSettings {
    @Override
    public String string(String key) {
        return null;
    }

    @Override
    public void string(String key, String value) {
    }

    @Override
    public int int32(String key) {
        return 0;
    }

    @Override
    public void int32(String key, int value) {
    }

    @Override
    public long int64(String key) {
        return 0;
    }

    @Override
    public void int64(String key, long value) {
    }

    @Override
    public boolean bool(String key) {
        return false;
    }

    @Override
    public void bool(String key, boolean value) {
    }
}
