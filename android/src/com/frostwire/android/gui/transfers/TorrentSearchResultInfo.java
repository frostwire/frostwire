/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
