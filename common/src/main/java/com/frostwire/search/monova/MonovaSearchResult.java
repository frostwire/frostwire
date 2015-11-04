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

package com.frostwire.search.monova;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class MonovaSearchResult extends AbstractTorrentSearchResult {

    private final static long[] BYTE_MULTIPLIERS = new long[] { 1, 2 << 9, 2 << 19, 2 << 29, 2 << 39, 2 << 49 };

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

    public MonovaSearchResult(String detailsUrl, SearchMatcher matcher) {
        /*
         * Matcher groups cheatsheet
         * 1 -> .torrent URL
         * 2 -> infoHash
         * 3 -> seeds
         * 4 -> SIZE (B|KiB|MiBGiB)
         */
        this.detailsUrl = detailsUrl;
        this.filename = parseFileName(FilenameUtils.getName(matcher.group("filename")));
        this.displayName = parseDisplayName(HtmlManipulator.replaceHtmlEntities(FilenameUtils.getBaseName(filename)));
        this.infoHash = matcher.group("infohash");
        this.creationTime = parseCreationTime(matcher.group("creationtime"));
        this.size = parseSize(matcher.group("size"));
        this.seeds = parseSeeds(matcher.group("seeds"));

        // Monova can't handle direct download of torrents without some sort of cookie
        //the torcache url wont resolve into direct .torrent
        this.torrentUrl = "magnet:?xt=urn:btih:" + infoHash;
    }

    private String parseDisplayName(String fileName) {
        return fileName.replaceAll("_"," ");
    }

    private String parseFileName(String name) {
        String[] split = name.split("title\\=");
        if (split.length > 1) {
            name = split[1];
            if (name.endsWith("(")) {
                name = name.substring(0, -1).trim();
            }
        }
        return name + ".torrent";
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

    private int parseSeeds(String group) {
        try {
            return Integer.parseInt(group);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseCreationTime(String addedWhenString) {

        String[] arr = addedWhenString.trim().split(" ");
        int unit = Integer.parseInt(arr[0]);
        
        String period = arr[1];
        long periodMultiplierInDays = 0;
        
        if (period.startsWith("day")) {
            periodMultiplierInDays = 1;
        } else if (period.startsWith("month")) {
            periodMultiplierInDays = 30;
        } else if (period.startsWith("year")) {
            periodMultiplierInDays = 365;
        }
        
        long daysBackInTimeInMillis = unit * periodMultiplierInDays * 86400000l;
        long now = System.currentTimeMillis();
        long dateInThePast = now - daysBackInTimeInMillis;
        
        return dateInThePast;
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
        return "Monova";
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
}