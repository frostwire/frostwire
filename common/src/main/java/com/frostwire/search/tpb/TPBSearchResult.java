/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.tpb;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import org.apache.commons.io.FilenameUtils;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 */
public class TPBSearchResult extends AbstractTorrentSearchResult {
    private static final Map<String, String> UNIT_MAPPER;
    private static final Pattern COMMON_DATE_PATTERN;
    private static final Pattern OLDER_DATE_PATTERN;
    private static final Pattern DATE_TIME_PATTERN;

    static {
        UNIT_MAPPER = new HashMap<>();
        UNIT_MAPPER.put("B", "B");
        UNIT_MAPPER.put("KiB", "KB");
        UNIT_MAPPER.put("MiB", "MB");
        UNIT_MAPPER.put("GiB", "GB");
        UNIT_MAPPER.put("TiB", "TB");
        UNIT_MAPPER.put("PiB", "PB");
        COMMON_DATE_PATTERN = Pattern.compile("([\\d]{2})-([\\d]{2})");
        OLDER_DATE_PATTERN = Pattern.compile("([\\d]{2})-([\\d]{2})&nbsp;([\\d]{4})");
        DATE_TIME_PATTERN = Pattern.compile("([\\d]{2})-([\\d]{2})&nbsp;(\\d\\d:\\d\\d)");
    }

    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    TPBSearchResult(SearchMatcher matcher) {
        /*
        (?is)<td class=\"vertTh\">.*?<a href=\"[^\"]*?\" title=\"More from this category\">(.*?)</a>.*?</td>.*?<a href=\"([^\"]*?)\" class=\"detLink\" title=\"Details for ([^\"]*?)\">.*?</a>.*?</div>.*?<a href=\"magnet(.*?)\" title=\"Download this torrent using magnet\">.*?Uploaded ([^,]*?), Size (.*?), ULed by.*?<td align="right">(.*?)</td>.*?<td align="right">(.*?)</td>.*?</tr>
         * Matcher groups cheatsheet
         * 1 -> Category (useless)
         * 2 -> Torrent Details Page
         * 3 -> Title/Name
         * 4 -> .torrent URL
         * 5 -> infoHash
         * 6 -> MM-DD&nbsp;YYYY or Today&nbsp;HH:MM or Y-day&nbsp;HH:MM
         * 7 -> SIZE&nbsp;(B|KiB|MiBGiB)
         * 8 -> seeds
         * 9 -> leechers (unused, probably there for matching purposes)
         */
        this.detailsUrl = matcher.group("detailsUrl");
        String temp = HtmlManipulator.replaceHtmlEntities(matcher.group("filename"));
        temp = HtmlManipulator.replaceHtmlEntities(temp); // because of input
        this.filename = buildFilename(temp);
        this.displayName = FilenameUtils.getBaseName(filename);
        this.torrentUrl = "magnet" + matcher.group("magnet");

        this.infoHash = torrentUrl.substring(20, 60).toLowerCase();
        this.creationTime = parseCreationTime(matcher.group("creationTime"));
        this.size = parseSize(matcher.group("size"));
        this.seeds = parseSeeds(matcher.group("seeds"));
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
        return detailsUrl;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    protected long parseSize(String group) {
        String[] size = group.split("&nbsp;");
        String amount = size[0].trim();
        String unit = UNIT_MAPPER.get(size[1].trim());
        return calculateSize(amount, unit);
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
