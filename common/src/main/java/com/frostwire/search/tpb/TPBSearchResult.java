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

package com.frostwire.search.tpb;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class TPBSearchResult extends AbstractTorrentSearchResult {

    private final static long[] BYTE_MULTIPLIERS = new long[] { 1, 2 << 9, 2 << 19, 2 << 29, 2 << 39, 2 << 49 };

    private static final Map<String, Integer> UNIT_TO_BYTE_MULTIPLIERS_MAP;
    private static final Pattern COMMON_DATE_PATTERN;
    private static final Pattern OLDER_DATE_PATTERN;
    private static final Pattern DATE_TIME_PATTERN;

    static {
        UNIT_TO_BYTE_MULTIPLIERS_MAP = new HashMap<String, Integer>();
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("B", 0);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("KiB", 1);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("MiB", 2);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("GiB", 3);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("TiB", 4);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("PiB", 5);

        COMMON_DATE_PATTERN = Pattern.compile("([\\d]{2})-([\\d]{2})");
        OLDER_DATE_PATTERN = Pattern.compile("([\\d]{2})-([\\d]{2})&nbsp;([\\d]{4})");
        DATE_TIME_PATTERN = Pattern.compile("([\\d]{2})-([\\d]{2})&nbsp;(\\d\\d:\\d\\d)");
    }

    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final String domainName;
    private final long size;
    private final long creationTime;
    private final int seeds;


    public TPBSearchResult(String domainName, SearchMatcher matcher) {
        /*
         * Matcher groups cheatsheet
         * 1 -> Category (useless)
         * 2 -> Torrent Details Page
         * 3 -> Title/Name
         * 4 -> .torrent URL
         * 5 -> infoHash
         * 6 -> MM-DD&nbsp;YYYY or Today&nbsp;HH:MM or Y-day&nbsp;HH:MM 
         * 7 -> SIZE&nbsp;(B|KiB|MiBGiB)
         * 8 -> seeds
         */
        this.detailsUrl = matcher.group(2);
        this.domainName = domainName;
        String temp = HtmlManipulator.replaceHtmlEntities(matcher.group(3));
        temp = HtmlManipulator.replaceHtmlEntities(temp); // because of input
        this.filename = buildFilename(temp);
        this.displayName = FilenameUtils.getBaseName(filename);
        this.torrentUrl = matcher.group(4); //let's assign the magnet to this for now.
        this.infoHash = torrentUrl.substring(20, 60);
        this.creationTime = parseCreationTime(matcher.group(5));
        this.size = parseSize(matcher.group(6));
        this.seeds = parseSeeds(matcher.group(7));
    }

    @Override
    public String getFilename() {
        return filename;
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
        return "TPB";
    }

    @Override
    public String getHash() {
        return infoHash;
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
    public String getDetailsUrl() {
        return "http://" + domainName + detailsUrl;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    private long parseSize(String group) {
        String[] size = group.split("&nbsp;");
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

    private long parseCreationTime(String group) {

        //Today or for whatever minutes ago
        if (group.contains("Today") || group.contains("<b>")) {
            return System.currentTimeMillis();
        } else if (group.contains("Y-day")) {
            return System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        }

        Matcher OLDER_DATE_PATTERN_MATCHER = OLDER_DATE_PATTERN.matcher(group);
        Matcher COMMON_DATE_PATTERN_MATCHER = COMMON_DATE_PATTERN.matcher(group);
        Matcher DATE_TIME_PATTERN_MATCHER = DATE_TIME_PATTERN.matcher(group);

        Matcher RIGHT_MATCHER = (OLDER_DATE_PATTERN_MATCHER.matches()) ? OLDER_DATE_PATTERN_MATCHER : COMMON_DATE_PATTERN_MATCHER;

        if (!RIGHT_MATCHER.matches() && DATE_TIME_PATTERN_MATCHER.matches()) {
            RIGHT_MATCHER = DATE_TIME_PATTERN_MATCHER;
        }

        int month = Integer.parseInt(RIGHT_MATCHER.group(1));
        int date = Integer.parseInt(RIGHT_MATCHER.group(2));
        int year = 0;

        if (OLDER_DATE_PATTERN_MATCHER.matches() && OLDER_DATE_PATTERN_MATCHER.groupCount() == 3) {
            year = Integer.parseInt(RIGHT_MATCHER.group(3));
        } else if (COMMON_DATE_PATTERN_MATCHER.matches() || DATE_TIME_PATTERN_MATCHER.matches()) {
            year = Calendar.getInstance().get(Calendar.YEAR);
        }

        Calendar instance = Calendar.getInstance();
        instance.clear();
        instance.set(year, month, date);
        return instance.getTimeInMillis();
    }

    private String buildFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|\\[\\]]+", "_") + ".torrent";
    }
}