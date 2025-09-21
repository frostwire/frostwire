/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.search.magnetdl;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.UrlUtils;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author gubatron
 */
public final class MagnetDLSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    MagnetDLSearchResult(String detailsUrl,
                             String magnet,
                             String fileSize,
                             String unit,
                             String age,
                             String seeds,
                             String title) {
        this.detailsUrl = detailsUrl;
        this.torrentUrl = magnet + "&" + UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS;
        this.infoHash = magnet.substring(20,60);
        this.filename = parseFileName(title);
        this.size = parseSize(fileSize + " " + unit);
        this.creationTime = parseAgeString(age);
        this.seeds = parseSeeds(seeds);
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
        return "MagnetDL";
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
        long now = System.currentTimeMillis();
        try {
            if (dateString.contains("year")) {
                int years = Integer.parseInt(dateString.substring(0, dateString.indexOf(' ')));
                long yearInMillis = 365L * 24L * 60L * 60L * 1000L;
                return now - years * yearInMillis;
            }
            if (dateString.contains("month")) {
                int months = Integer.parseInt(dateString.substring(0, dateString.indexOf(' ')));
                long monthInMillis = 31L * 24L * 60L * 60L * 1000L;
                return now - months * monthInMillis;
            }
            if (dateString.contains("day")) {
                int days = Integer.parseInt(dateString.substring(0, dateString.indexOf(' ')));
                long dayInMillis = 24L * 60L * 60L * 1000L;
                return now - days * dayInMillis;
            }
            if (dateString.contains("hour")) {
                int hours = Integer.parseInt(dateString.substring(0, dateString.indexOf(' ')));
                long hourInMillis = 60L * 60L * 1000L;
                return now - hours * hourInMillis;
            }
            if (dateString.contains("Yesterday")) {
                return now - 24L * 60L * 60L * 1000L; // one day in milliseconds
            }
            // this format seems to be not used anymore
            SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            now = myFormat.parse(dateString.trim()).getTime();
        } catch (Throwable t) {
        }
        return now;
    }
}
