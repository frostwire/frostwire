/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.search.eztv;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;

/**
 * @author gubatron
 * @author aldenml
 */
public final class EztvSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;

    public EztvSearchResult(String domainName, SearchMatcher matcher) {
        this.detailsUrl = domainName + matcher.group("detailUrl");
        this.displayName = HtmlManipulator.replaceHtmlEntities(matcher.group("displayname")).trim();
        this.torrentUrl = "magnet" + matcher.group("magnet");
        this.filename = displayName + ".torrent";
        this.infoHash = parseInfoHash(matcher, torrentUrl);
        this.seeds = 500;
        this.creationTime = convertAgeStringToTimestamp(matcher.group("age"));
        this.size = parseSize(matcher.group("size"));
    }

    private static String parseInfoHash(SearchMatcher matcher, String torrentUrl) {
        try {
            if (matcher.group("infohash") != null) {
                return matcher.group("infohash");
            } else if (torrentUrl.startsWith("magnet:?xt=urn:btih:")) {
                return torrentUrl.substring(20, 60).toLowerCase();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int parseSeeds(String group) {
        try {
            return Integer.parseInt(group);
        } catch (Throwable e) {
            return 0;
        }
    }

    @Override
    public double getSize() {
        return size;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getSource() {
        return "Eztv";
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

    /**
     * @param age "2h 21m", "5d 3h", "2 weeks", "4 mo", "1 year", "2 years"
     * @return The timestamp in milliseconds, obtained by subtracting the age from System.currentTimeMillis()
     */
    private long convertAgeStringToTimestamp(String age) {
        long timestamp = System.currentTimeMillis();
        if (age.contains("year")) {
            String[] parts = age.split("year");
            int years = Integer.parseInt(parts[0].trim());
            timestamp -= years * 365 * 24 * 60 * 60 * 1000;
            return timestamp;
        }

        if (age.contains("mo")) {
            String[] parts = age.split("mo");
            int months = Integer.parseInt(parts[0].trim());
            timestamp -= months * 30 * 24 * 60 * 60 * 1000;
            return timestamp;
        }

        if (age.contains("week")) {
            String[] parts = age.split("week");
            int weeks = Integer.parseInt(parts[0].trim());
            timestamp -= weeks * 7 * 24 * 60 * 60 * 1000;
            return timestamp;
        }

        if (age.contains("d")) {
            String[] parts = age.split("d");
            int days = Integer.parseInt(parts[0].trim());
            timestamp -= days * 24 * 60 * 60 * 1000;
            return timestamp;
        }

        if (age.contains("h") && !age.contains("d")) {
            String[] parts = age.split("h");
            int hours = Integer.parseInt(parts[0].trim());
            timestamp -= hours * 60 * 60 * 1000;
            return timestamp;
        }
        return timestamp;
    }
}
