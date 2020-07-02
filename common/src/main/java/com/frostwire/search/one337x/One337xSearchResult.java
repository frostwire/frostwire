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

package com.frostwire.search.one337x;

import android.util.Log;

import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;

import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public final class One337xSearchResult extends AbstractTorrentSearchResult {

//    private final String thumbnailUrl;
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;
    private final String magnetUrl;

    public One337xSearchResult(String detailsUrl, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.displayName = buildDisplayName(matcher);
        this.size = parseSize(matcher.group("size"));
//        this.thumbnailUrl = buildThumbnailUrl(matcher.group("cover"));
        this.creationTime = parseCreationTime(matcher.group("creationDate"));
        this.seeds = parseSeeds(matcher.group("seeds"));
        this.magnetUrl = matcher.group("magnet");
        this.filename = buildFileName(detailsUrl);
        this.infoHash = PerformersHelper.parseInfoHash(magnetUrl); // already comes in lowercase
    }

    private static String buildDisplayName(SearchMatcher matcher) {
        String displayName = matcher.group("displayName");
        String lang = matcher.group("language");

        if (lang != null) {
            displayName += " (" + lang + ")";
        }
        return displayName;
    }

    /*private static String buildThumbnailUrl(String str) {
        if (str == null) {
            return null;
        }
        return str.startsWith("//") ? "https:" + str : null;
    }*/

    private static String buildFileName(String detailsUrl) {
        return FilenameUtils.getBaseName(detailsUrl) + ".torrent";
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
        return "1337x";
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

    /*@Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }*/

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            String[] ds = dateString.split(" ");
            if (ds[1].contains("hour")) {
                try {
                    int hours = Integer.parseInt(ds[0]);
                    return result - (hours * 60 * 60 * 1000L);
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("year")) {
                try {
                    int years = Integer.parseInt(ds[0]);
                    return result - (years * 365L * 24L * 60L * 60L * 1000L); // a year in milliseconds
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("month")) {
                try {
                    int months = Integer.parseInt(ds[0]);
                    return result - (months * 31L * 24L * 60L * 60L * 1000L); // a month in milliseconds
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("minute")) {
                try {
                    int minutes = Integer.parseInt(ds[0]);
                    return result - (minutes * 60L * 1000L); // a month in milliseconds
                } catch (Exception ignored) {
                }
            }
            return result;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return result;
    }
}
