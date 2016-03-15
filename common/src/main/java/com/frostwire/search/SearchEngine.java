/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 
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

package com.frostwire.search;

import com.frostwire.platform.Platforms;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchEngine {

    private final String settingKey;

    private boolean remoteEnable;

    SearchEngine(String settingKey) {
        this.settingKey = settingKey;
        this.remoteEnable = true;
    }

    public boolean localEnable() {
        return Platforms.appSettings().bool(settingKey);
    }

    public boolean remoteEnable() {
        return remoteEnable;
    }

    public void remoteEnable(boolean value) {
        remoteEnable = value;
    }

    public boolean enable() {
        return localEnable() && remoteEnable();
    }
}
