/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.frostwire.android.gui.transfers;

import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
class TorrentUrlInfo implements TorrentDownloadInfo {

    private final String url;
    private final String displayName;

    public TorrentUrlInfo(String url) {
        this(url, null);
    }

    public TorrentUrlInfo(String url, String displayName) {
        this.url = url;
        this.displayName = displayName;
    }

    @Override
    public String getTorrentUrl() {
        return url;
    }

    @Override
    public String getDetailsUrl() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return (displayName == null || displayName.isEmpty()) ? FilenameUtils.getName(url) : displayName;
    }

    @Override
    public double getSize() {
        return -1;
    }

    @Override
    public String getHash() {
        if (url != null && url.startsWith("magnet:?xt=urn:btih:")) {
            return url.substring(20,60).toLowerCase();
        }

        return null;
    }

    @Override
    public String makeMagnetUri() {
        return null;
    }


    @Override
    public String getRelativePath() {
        return null;
    }

    @Override
    public String getReferrerUrl() {
        return null;
    }
}
