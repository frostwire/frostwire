/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
