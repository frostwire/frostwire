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

package com.frostwire.gui.library;

import org.limewire.setting.FileSetting;

import java.io.File;

/**
 * Implementation of the {@link DirectoryHolder} interface backed by a file
 * setting.
 */
public class FileSettingDirectoryHolder extends AbstractDirectoryHolder {
    private final String name;
    private final String desc;
    private final FileSetting fs;

    private FileSettingDirectoryHolder(FileSetting fs, String name, String description) {
        this.name = name;
        this.fs = fs;
        this.desc = description;
    }

    FileSettingDirectoryHolder(FileSetting fs, String name) {
        this(fs, name, null);
    }

    /**
     * Returns the name of the directory if no name is set in the constructor.
     */
    public String getName() {
        return name != null ? name : getDirectory().getName();
    }

    /**
     * Returns the absolute path of directory if none is provided in the
     * constructor.
     */
    public String getDescription() {
        return desc != null ? desc : getDirectory().getAbsolutePath();
    }

    public File getDirectory() {
        return fs.getValue();
    }
}
