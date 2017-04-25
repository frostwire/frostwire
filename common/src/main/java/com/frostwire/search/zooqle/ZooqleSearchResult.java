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

    ZooqleSearchResult(String detailsUrl, String urlPrefix, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.filename = matcher.group("filename") + ".torrent";
        this.displayName = matcher.group("filename");
        this.seeds = Integer.valueOf(matcher.group("seeds").trim());
        this.torrentUrl = urlPrefix + "/download/" + matcher.group("torrent") + ".torrent";
        this.infoHash = matcher.group("infohash");
        this.size = calculateSize(matcher.group("size").replace(",", ""), matcher.group("sizeUnit"));
        this.creationTime = parseCreationTime(matcher.group("year") + " " + matcher.group("month") + " " + matcher.group("day"));
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
