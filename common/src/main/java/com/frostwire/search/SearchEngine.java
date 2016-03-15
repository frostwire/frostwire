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

import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchEngine {

    private final String name;
    private final String settingKey;
    private final boolean mobile;

    private boolean remoteEnabled;

    SearchEngine(String name, String settingKey, boolean mobile) {
        this.name = name;
        this.settingKey = settingKey;
        this.mobile = mobile;
        this.remoteEnabled = true;
    }

    SearchEngine(String name, String settingKey) {
        this(name, settingKey, true);
    }

    public final String name() {
        return name;
    }

    public final boolean localEnabled() {
        if (!Platforms.appSettings().bool(settingKey)) {
            return false;
        }

        return mobile || Platforms.get().networkType() != Platform.NetworkType.MOBILE;
    }

    public final boolean remoteEnabled() {
        return remoteEnabled;
    }

    public final void remoteEnabled(boolean value) {
        remoteEnabled = value;
    }

    public boolean enabled() {
        return localEnabled() && remoteEnabled();
    }

    public abstract SearchPerformer newPerformer(long token, String keywords);
}
