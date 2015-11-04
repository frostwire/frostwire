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

package com.frostwire.search.btjunkie;

import com.frostwire.licences.License;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;

/**
 *
 * @author gubatron
 * @author aldenml
 *
 */
public class BtjunkieSearchResult extends AbstractTorrentSearchResult {

    private final String domainName;
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    public BtjunkieSearchResult(String domainName,
                                String detailsUrl,
                                String filename,
                                String displayName,
                                String torrentUrl,
                                String infoHash,
                                long size,
                                long creationTime,
                                int seeds) {
        this.domainName = domainName;
        this.detailsUrl = detailsUrl;
        this.filename = filename;
        this.displayName = displayName;
        this.torrentUrl = torrentUrl;
        this.infoHash = infoHash;
        this.size = size;
        this.creationTime = creationTime;
        this.seeds = seeds;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getTorrentUrl() {
        return torrentUrl;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getHash() {
        return infoHash;
    }

    @Override
    public String getSource() {
        return "BTJunkie";
    }

    @Override
    public License getLicense() {
        return License.UNKNOWN;
    }

    @Override
    public String getThumbnailUrl() {
        return null;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return size;
    }
}
