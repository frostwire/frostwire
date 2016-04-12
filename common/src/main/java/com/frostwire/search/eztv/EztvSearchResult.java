/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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
import com.frostwire.util.StringUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
class EztvSearchResult extends AbstractTorrentSearchResult {

    private final static long[] BYTE_MULTIPLIERS = new long[]{1, 2 << 9, 2 << 19, 2 << 29, 2 << 39, 2 << 49};

    private static final Map<String, Integer> UNIT_TO_BYTE_MULTIPLIERS_MAP;

    static {
        UNIT_TO_BYTE_MULTIPLIERS_MAP = new HashMap<String, Integer>();
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("B", 0);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("KB", 1);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("MB", 2);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("GB", 3);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("TB", 4);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("PB", 5);
    }

    private String filename;
    private String displayName;
    private String detailsUrl;
    private String torrentUrl;
    private String infoHash;
    private long size;
    private long creationTime;
    private int seeds;

    EztvSearchResult(String detailsUrl, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        String dispName = null;
        if (matcher.group("displayname") != null) {
            dispName = matcher.group("displayname");
        } else if (matcher.group("displayname2") != null) {
            dispName = matcher.group("displayname2");
        } else if (matcher.group("displaynamefallback") != null) {
            dispName = matcher.group("displaynamefallback");
        }
        this.displayName = HtmlManipulator.replaceHtmlEntities(dispName).trim();

        if (matcher.group("torrenturl") != null) {
            this.torrentUrl = matcher.group("torrenturl");
        } else if (matcher.group("magneturl") != null) {
            this.torrentUrl = matcher.group("magneturl");
        }

        this.filename = parseFileName(FilenameUtils.getName(torrentUrl));

        this.infoHash = null;
        try {
            if (matcher.group("infohash") != null) {
                this.infoHash = matcher.group("infohash");
            } else if (torrentUrl.startsWith("magnet:?xt=urn:btih:")) {
                this.infoHash = torrentUrl.substring(20, 52);
            }
        } catch (Throwable ignored) {}

        this.seeds = -1;
        this.creationTime = parseCreationTime(matcher.group("creationtime"));
        this.size = parseSize(matcher.group("filesize"));
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

    private String parseFileName(String urlEncodedFileName) {
        String decodedFileName = null;
        try {
            if (!StringUtils.isNullOrEmpty(urlEncodedFileName)) {
                decodedFileName = URLDecoder.decode(urlEncodedFileName, "UTF-8");
            }
        } catch (UnsupportedEncodingException ignored) {
        }
        return decodedFileName;
    }

    private long parseSize(String group) {
        String[] size = group.trim().split(" ");
        String amount = size[0].trim();
        String unit = size[1].trim();

        long multiplier = BYTE_MULTIPLIERS[UNIT_TO_BYTE_MULTIPLIERS_MAP.get(unit)];

        //fractional size
        if (amount.indexOf(".") > 0) {
            float floatAmount = Float.parseFloat(amount);
            return (long) (floatAmount * multiplier);
        }
        //integer based size
        else {
            int intAmount = Integer.parseInt(amount);
            return (long) (intAmount * multiplier);
        }
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            dateString = dateString.replaceAll("(st|nd|rd|th)", "");
            SimpleDateFormat myFormat = new SimpleDateFormat("d MMM yyyy", Locale.US);
            result = myFormat.parse(dateString).getTime();
        } catch (Throwable ignored) {
        }
        return result;
    }
}
