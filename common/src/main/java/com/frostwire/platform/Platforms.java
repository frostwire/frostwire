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

package com.frostwire.platform;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Platforms {
    private static Platform platform;

    private Platforms() {
    }

    public static Platform get() {
        if (platform == null) {
            throw new IllegalStateException("Platform can't be null");
        }
        return platform;
    }

    public static void set(Platform p) {
        if (p == null) {
            throw new IllegalArgumentException("Platform can't be set to null");
        }
        platform = p;
    }

    /**
     * Shortcut to current platform file system.
     *
     * @return
     */
    public static FileSystem fileSystem() {
        return get().fileSystem();
    }

    /**
     * Shortcut to current platform application settings.
     *
     * @return
     */
    public static AppSettings appSettings() {
        return get().appSettings();
    }

    /**
     * Shortcut to current platform file system data method.
     *
     * @return
     */
    public static File data() {
        return get().systemPaths().data();
    }

    /**
     * Shortcut to current platform file system torrents method.
     *
     * @return
     */
    public static File torrents() {
        return get().systemPaths().torrents();
    }

    /**
     * Shortcut to current platform file system temp method.
     *
     * @return
     */
    public static File temp() {
        return get().systemPaths().temp();
    }
}
