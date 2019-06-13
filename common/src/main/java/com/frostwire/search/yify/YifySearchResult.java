/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.yify;

import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import org.apache.commons.io.FilenameUtils;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 */
public final class YifySearchResult extends AbstractTorrentSearchResult {
    private static final long[] BYTE_MULTIPLIERS = new long[]{1, 2 << 9, 2 << 19, 2 << 29, 2L << 39, 2L << 49};
    private static final Map<String, Integer> UNIT_TO_BYTE_MULTIPLIERS_MAP;
    private static final Pattern SIZE_PATTERN;

    static {
        UNIT_TO_BYTE_MULTIPLIERS_MAP = new HashMap<>();
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("B", 0);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("K", 1);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("M", 2);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("G", 3);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("T", 4);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("P", 5);
        SIZE_PATTERN = Pattern.compile("([\\d.]+)([BKMGTP])");
    }

    private final String thumbnailUrl;
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;
    private final String magnetUrl;

    public YifySearchResult(String detailsUrl, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.displayName = buildDisplayName(matcher);
        this.thumbnailUrl = buildThumbnailUrl(matcher.group("cover"));
        this.size = buildSize(matcher.group("size"));
        this.creationTime = buildCreationTime(matcher.group("creationDate"));
        this.seeds = parseSeeds(matcher.group("seeds"));
        this.magnetUrl = matcher.group("magnet").replaceAll("&amp;", "&");
        this.filename = buildFileName(detailsUrl);
        this.infoHash = PerformersHelper.parseInfoHash(magnetUrl);
    }

    private static String buildDisplayName(SearchMatcher matcher) {
        String displayName = matcher.group("displayName");
        String lang = matcher.group("language");
        if (lang != null) {
            displayName += " (" + lang + ")";
        }
        return displayName;
    }

    private static String buildThumbnailUrl(String str) {
        if (str == null) {
            return null;
        }
        return str.startsWith("//") ? "https:" + str : "https://www.yify-torrent.org" + str;
    }

    private static long buildCreationTime(String str) {
        try {
            return new SimpleDateFormat("M/d/y").parse(str).getTime();
        } catch (Throwable e) {
            // not that important
            return System.currentTimeMillis();
        }
    }

    private static String buildFileName(String detailsUrl) {
        return FilenameUtils.getBaseName(detailsUrl) + ".torrent";
    }

    private static long buildSize(String str) {
        long result = 0;
        Matcher matcher = SIZE_PATTERN.matcher(str);
        if (matcher.find()) {
            String amount = matcher.group(1);
            String unit = matcher.group(2);
            long multiplier = BYTE_MULTIPLIERS[UNIT_TO_BYTE_MULTIPLIERS_MAP.get(unit)];
            //fractional size
            if (amount.indexOf(".") > 0) {
                float floatAmount = Float.parseFloat(amount);
                result = (long) (floatAmount * multiplier);
            }
            //integer based size
            else {
                int intAmount = Integer.parseInt(amount);
                result = intAmount * multiplier;
            }
        }
        return result;
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
    public String getTorrentUrl() {
        return magnetUrl;
    }

    @Override
    public String getSource() {
        return "Yify";
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
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}
