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
 *
 */
public class YifySearchResult extends AbstractTorrentSearchResult {

    private final static long[] BYTE_MULTIPLIERS = new long[] { 1, 2 << 9, 2 << 19, 2 << 29, 2 << 39, 2 << 49 };

    private static final Map<String, Integer> UNIT_TO_BYTE_MULTIPLIERS_MAP;
    
    private static final Pattern sizePattern;

    static {
        UNIT_TO_BYTE_MULTIPLIERS_MAP = new HashMap<String, Integer>();
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

    public YifySearchResult(String domainName, String detailsUrl, SearchMatcher matcher) {
        // matcher groups: 1 -> cover (url may contains date)
        //                 2 -> display name
        //                 3 -> size
        //                 4 -> language
        //                 5 -> peers
        //                 6 -> seeds
        //                 7 -> magnet    
        this.thumbnailUrl = matcher.group(1);
        this.detailsUrl = detailsUrl;
        this.filename = parseFileName(detailsUrl);
        this.size = parseSize(matcher.group(3));
        this.creationTime = System.currentTimeMillis();
        this.seeds = Integer.parseInt(matcher.group(6));

        //a magnet
        this.torrentUrl = matcher.group(7);
        this.displayName = matcher.group(2) + " ("+ matcher.group(4) +")";
        this.infoHash = PerformersHelper.parseInfoHash(torrentUrl);
    }

    private String parseFileName(String detailsUrl) {
        String[] split = detailsUrl.split("/");
        return FilenameUtils.getBaseName(split[split.length-1]) + ".torrent";
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

    private long parseSize(String group) {
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
                result = (long) (intAmount * multiplier);
            }
        }
        return result;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}