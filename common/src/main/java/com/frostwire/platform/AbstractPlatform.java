/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.platform;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractPlatform implements Platform {
    private final FileSystem fileSystem;
    private final SystemPaths systemPaths;
    private final AppSettings appSettings;

    public AbstractPlatform(FileSystem fileSystem, SystemPaths systemPaths, AppSettings appSettings) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("FileSystem can't be null");
        }
        if (systemPaths == null) {
            throw new IllegalArgumentException("SystemPaths can't be null");
        }
        if (appSettings == null) {
            throw new IllegalArgumentException("AppSettings can't be null");
        }
        this.fileSystem = fileSystem;
        this.systemPaths = systemPaths;
        this.appSettings = appSettings;
    }

    @Override
    public FileSystem fileSystem() {
        return fileSystem;
    }

    @Override
    public SystemPaths systemPaths() {
        return systemPaths;
    }

    @Override
    public AppSettings appSettings() {
        return appSettings;
    }
}
