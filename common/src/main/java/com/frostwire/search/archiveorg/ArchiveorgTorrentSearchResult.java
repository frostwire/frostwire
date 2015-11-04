/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.search.archiveorg;

import com.frostwire.search.torrent.TorrentSearchResult;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class ArchiveorgTorrentSearchResult extends ArchiveorgCrawledSearchResult implements TorrentSearchResult {

    private final long size;

    public ArchiveorgTorrentSearchResult(ArchiveorgSearchResult sr, ArchiveorgFile file, long size) {
        super(sr, file);
        this.size = size;
    }

    @Override
    public String getTorrentUrl() {
        return getDownloadUrl();
    }

    @Override
    public String getReferrerUrl() {
        return getDetailsUrl();
    }

    @Override
    public int getSeeds() {
        return 3;
    }

    @Override
    public String getHash() {
        return null;
    }

    @Override
    public long getSize() {
        return size;
    }
}
