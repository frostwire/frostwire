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

package com.frostwire.platform;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Platforms {
    private static final AtomicReference<Platform> platform = new AtomicReference<>();

    private Platforms() {
    }

    public static Platform get() {
        Platform p = platform.get();
        if (p == null) {
            throw new IllegalStateException("Platform can't be null");
        }
        return p;
    }

    public static void set(Platform p) {
        if (p == null) {
            throw new IllegalArgumentException("Platform can't be set to null");
        }
        platform.compareAndSet(null, p);
    }

    /**
     * Shortcut to current platform file system.
     *
     */
    public static FileSystem fileSystem() {
        return get().fileSystem();
    }

    /**
     * Shortcut to current platform application settings.
     *
     */
    public static AppSettings appSettings() {
        return get().appSettings();
    }

    /**
     * Shortcut to the current platform file system data method.
     *
     */
    public static File data() {
        return get().systemPaths().data();
    }

    /**
     * Shortcut to the current platform file system torrents method.
     *
     */
    public static File torrents() {
        return get().systemPaths().torrents();
    }

    /**
     * Shortcut to the current platform file system temp method.
     *
     */
    public static File temp() {
        return get().systemPaths().temp();
    }
}
