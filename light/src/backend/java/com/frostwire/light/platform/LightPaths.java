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
import com.frostwire.light.Constants;
import com.frostwire.platform.SystemPaths;

import java.io.File;

public class LightPaths implements SystemPaths {

    private final ConfigurationManager configurationManager;

    LightPaths(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Override
    public File data() {
        return configurationManager.getFile(Constants.PREF_KEY_STORAGE_PATH);
    }

    @Override
    public File torrents() {
        return configurationManager.getFile(Constants.PREF_KEY_TORRENTS_PATH);
    }

    @Override
    public File temp() {
        return configurationManager.getFile(Constants.PREF_KEY_TEMP_PATH);
    }

    @Override
    public File libtorrent() {
        return configurationManager.getFile(Constants.PREF_KEY_LIBTORRENT_PATH);
    }

    @Override
    public File update() {
        return configurationManager.getFile(Constants.PREF_KEY_UPDATES_PATH);
    }
}
