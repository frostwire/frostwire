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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 */
public final class YifySearchResult extends AbstractTorrentSearchResult {

    private final static long[] BYTE_MULTIPLIERS = new long[]{1, 2 << 9, 2 << 19, 2 << 29, 2 << 39, 2 << 49};

    private static final Map<String, Integer> UNIT_TO_BYTE_MULTIPLIERS_MAP;

    private static final Pattern sizePattern;

    static {
        UNIT_TO_BYTE_MULTIPLIERS_MAP = new HashMap<>();
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("B", 0);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("K", 1);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("M", 2);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("G", 3);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("T", 4);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("P", 5);
        sizePattern = Pattern.compile("([\\d+\\.]+)([BKMGTP])");
    }

    private final String thumbnailUrl;
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    YifySearchResult(String detailsUrl, SearchMatcher matcher) {
        this.thumbnailUrl = matcher.group("cover");
        this.detailsUrl = detailsUrl;
        this.filename = parseFileName(detailsUrl);
        this.size = parseSize(matcher.group("size"));
        this.creationTime = System.currentTimeMillis();
        this.seeds = Integer.parseInt(matcher.group("seeds"));
        this.torrentUrl = matcher.group("magnet");
        this.displayName = matcher.group("displayName") + " (" + matcher.group("language") + ")";
        this.infoHash = PerformersHelper.parseInfoHash(torrentUrl);
    }

    @Override
    public String toString() {
        return String.format("{\n\tthumbnailUrl: '%s',\n\tdetailsUrl: '%s',\n\tfilename: '%s',\n\tsize: %d," +
                        "\n\tcreationTime: %d,\n\tseeds: %d,\n\ttorrentUrl: '%s',\n\tdisplayName: '%s',\n\tinfoHash: '%s'\n}",
                thumbnailUrl,
                detailsUrl,
                filename,
                size,
                creationTime,
                seeds,
                torrentUrl,
                displayName,
                infoHash);
    }

    private String parseFileName(String detailsUrl) {
        String[] split = detailsUrl.split("/");
        return FilenameUtils.getBaseName(split[split.length - 1]) + ".torrent";
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
    public String getTorrentUrl() {
        return torrentUrl;
    }

    protected long parseSize(String group) {
        long result = 0;
        Matcher matcher = sizePattern.matcher(group);
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

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}
