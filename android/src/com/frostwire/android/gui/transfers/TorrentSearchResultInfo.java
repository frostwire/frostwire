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

import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
class TorrentSearchResultInfo implements TorrentDownloadInfo {

    private final TorrentSearchResult sr;
    private final String referrerUrl;

    public TorrentSearchResultInfo(TorrentSearchResult sr, String referrerUrl) {
        this.sr = sr;
        this.referrerUrl = referrerUrl;
    }

    public TorrentSearchResultInfo(TorrentSearchResult sr) {
        this(sr, sr.getReferrerUrl());
    }

    @Override
    public String getTorrentUrl() {
        return sr.getTorrentUrl();
    }

    @Override
    public String getDetailsUrl() {
        return sr.getDetailsUrl();
    }

    @Override
    public String getDisplayName() {
        return sr.getDisplayName();
    }

    @Override
    public double getSize() {
        return sr.getSize();
    }

    @Override
    public String getHash() {
        return sr.getHash();
    }

    @Override
    public String makeMagnetUri() {
        return null;
    }

    @Override
    public String getRelativePath() {
        if (sr instanceof TorrentCrawledSearchResult) {
            return ((TorrentCrawledSearchResult) sr).getFilePath();
        } else {
            return null;
        }
    }

    @Override
    public String getReferrerUrl() {
        return referrerUrl;
    }
}
