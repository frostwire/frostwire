/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Himanshu Sharma (HimanshuSharma789)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search.one337x;

import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.DateParser;

import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 * @author HimanshuSharma789
 */
public final class One337xSearchResult extends AbstractTorrentSearchResult {

    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;
    private final String magnetUrl;

    public One337xSearchResult(String detailsUrl, String displayName, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.displayName = displayName;
        this.size = parseSize(matcher.group("size"));
        this.creationTime = parseCreationTime(matcher.group("creationDate"));
        this.seeds = parseSeeds(matcher.group("seeds"));
        this.magnetUrl = matcher.group("magnet");
        this.filename = buildFileName(detailsUrl);
        this.infoHash = PerformersHelper.parseInfoHash(magnetUrl); // already comes in lowercase
    }

    private static String buildFileName(String detailsUrl) {
        return FilenameUtils.getBaseName(detailsUrl) + ".torrent";
    }

    private static int parseSeeds(String group) {
        try {
            return Integer.parseInt(group);
        } catch (Throwable e) {
            return 0;
        }
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getTorrentUrl() {
        return magnetUrl;
    }

    @Override
    public String getSource() {
        return "1337x";
    }

    @Override
    public String getHash() {
        return infoHash;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    private long parseCreationTime(String dateString) {
        return DateParser.parseRelativeAge(dateString);
    }
}
