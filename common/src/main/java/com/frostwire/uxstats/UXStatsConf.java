/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 
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

package com.frostwire.uxstats;

import java.util.UUID;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class UXStatsConf {

    private final String url;
    private final String guid;
    private final String os;
    private final String fwversion;
    private final String fwbuild;
    private final int period;
    private final int minEntries;
    private final int maxEntries;

    public UXStatsConf(String url, String os, String fwversion, String fwbuild, int period, int minEntries, int maxEntries) {
        this.url = url;
        this.guid = UUID.randomUUID().toString();
        this.os = os;
        this.fwversion = fwversion;
        this.fwbuild = fwbuild;
        this.period = period;
        this.minEntries = minEntries;
        this.maxEntries = maxEntries;
    }

    public String getUrl() {
        return url;
    }

    public String getGuid() {
        return guid;
    }

    public String getOS() {
        return os;
    }

    public String getFwversion() {
        return fwversion;
    }

    public String getFwbuild() {
        return fwbuild;
    }

    public int getPeriod() {
        return period;
    }

    public int getMinEntries() {
        return minEntries;
    }

    public int getMaxEntries() {
        return maxEntries;
    }
}
