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

package com.frostwire.search;

import com.frostwire.licences.License;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractSearchResult implements SearchResult {

    private int uid = -1;

    @Override
    public License getLicense() {
        return License.UNKNOWN;
    }

    @Override
    public long getCreationTime() {
        return -1;
    }

    @Override
    public String toString() {
        return getDetailsUrl();
    }

    public int getDaysOld() {
        int daysOld = (int) ((System.currentTimeMillis() - getCreationTime()) / 86400000);
        if (daysOld < 0) {
            daysOld = 1;
        }
        return daysOld;
    }

    @Override
    public String getThumbnailUrl() {
        return null;
    }

    @Override
    public int uid() {
        if (uid == -1) {
            String key = getDisplayName() + getDetailsUrl() + getSource();
            uid = key.hashCode();
        }
        return uid;
    }
}
