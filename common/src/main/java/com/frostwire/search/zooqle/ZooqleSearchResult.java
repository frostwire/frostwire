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

package com.frostwire.search.zooqle;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author aldenml
 * @author gubatron
 */
public final class ZooqleSearchResult extends AbstractTorrentSearchResult {

    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    private static final String FILE_SIZE_REGEX = "title=\"File size\"></i>(?<size>[\\d\\.\\,]*) (?<sizeUnit>.{2}?)<span class=\"small pad-l2\">";
    private static Pattern FILE_SIZE_PATTERN = null;

    ZooqleSearchResult(String detailsUrl, String urlPrefix, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.filename = matcher.group("filename") + ".torrent";
        this.displayName = matcher.group("filename");
        this.seeds = Integer.valueOf(matcher.group("seeds").trim());
        if (matcher.group("torrent") != null) {
            this.torrentUrl = urlPrefix + "/download/" + matcher.group("torrent") + ".torrent";
        } else {
            this.torrentUrl = null;
        }
        this.infoHash = matcher.group("infohash");
        this.size = calculateSize(matcher.group("sizedata"));
        this.creationTime = parseCreationTime(matcher.group("year") + " " + matcher.group("month") + " " + matcher.group("day"));
    }

    private long calculateSize(String sizedata) {
        if (sizedata.contains("unknown")) {
            return -1;
        }
        if (FILE_SIZE_PATTERN == null) {
            FILE_SIZE_PATTERN = Pattern.compile(FILE_SIZE_REGEX);
        }
        Matcher matcher = FILE_SIZE_PATTERN.matcher(sizedata);
        if (matcher.find()) {
            return calculateSize(matcher.group("size"), matcher.group("sizeUnit"));
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        return String.format("{\n\tdetailsUrl: %s,\n\t" +
                "filename: %s,\n\t" +
                "displayName: %s,\n\t" +
                "seeds: %d,\n\t" +
                "torrentUrl: %s,\n\t" +
                "infoHash: %s,\n\t" +
                "size: %d,\n\t" +
                "creationTime: %d\n}",
                detailsUrl,
                filename,
                displayName,
                seeds,
                torrentUrl,
                infoHash,
                size,
                creationTime);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getSource() {
        return "Zooqle";
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
    public String getTorrentUrl() {
        return torrentUrl;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getHash() {
        return infoHash;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            SimpleDateFormat myFormat = new SimpleDateFormat("yyyy MMM d", Locale.US);
            result = myFormat.parse(dateString).getTime();
        } catch (Throwable ignored) {
        }
        return result;
    }
}
