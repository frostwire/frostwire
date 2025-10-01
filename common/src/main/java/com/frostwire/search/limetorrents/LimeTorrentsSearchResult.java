/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), alejandroarturom
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

package com.frostwire.search.limetorrents;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.DateParser;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.UrlUtils;

/**
 * Created by alejandroarturom on 08/26/2016
 * Modified by gubatron on 01/20/2020
 */
public final class LimeTorrentsSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    LimeTorrentsSearchResult(String detailsUrl,
                             String infoHash,
                             String filename,
                             String fileSize,
                             String unit,
                             String age,
                             String seeds,
                             String title) {
        this.detailsUrl = detailsUrl;
        this.infoHash = (infoHash == null) ? null : infoHash.toLowerCase();
        this.filename = parseFileName(filename);
        this.size = parseSize(fileSize + " " + unit);
        this.creationTime = parseAgeString(age);
        this.seeds = parseSeeds(seeds);
        this.torrentUrl = UrlUtils.buildMagnetUrl(infoHash, filename, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
        this.displayName = title;
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
    public String getSource() {
        return "LimeTorrents";
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

    @Override
    public String getTorrentUrl() {
        return torrentUrl;
    }

    private String parseFileName(String decodedFileName) {
        return HtmlManipulator.replaceHtmlEntities(decodedFileName.trim()) + ".torrent";
    }

    private int parseSeeds(String group) {
        try {
            return Integer.parseInt(group);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseAgeString(String dateString) {
        return DateParser.parseRelativeAge(dateString);
    }
}
