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

import static com.frostwire.search.PerformersHelper.parseInfoHash;

/**
 * @author aldenml
 * @author gubatron
 */
public final class ZooqleSearchResult extends AbstractTorrentSearchResult {
    private static final String FILE_SIZE_REGEX = "title=\"File size\"></i>(?<size>[\\d\\.\\,]*) (?<sizeUnit>.{2}?)";
    private static final Pattern FILE_SIZE_PATTERN = Pattern.compile(FILE_SIZE_REGEX);
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;

    ZooqleSearchResult(String detailsUrl, String urlPrefix, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.filename = matcher.group("filename") + ".torrent";
        this.displayName = matcher.group("filename");
        this.seeds = Integer.valueOf(matcher.group("seeds").trim());
        String magnetUrl = "magnet:?xt=urn:btih:" + matcher.group("magnet");
        //if (matcher.group("torrent") != null) {
        //    this.torrentUrl = urlPrefix + "/download/" + matcher.group("torrent") + ".torrent";
        //} else {
        this.torrentUrl = magnetUrl;
        //}
        this.infoHash = parseInfoHash(magnetUrl);
        this.size = calculateSize(matcher.group("sizedata"));
        this.creationTime = parseCreationTime(matcher.group("year") + " " + matcher.group("month") + " " + matcher.group("day"));
    }

    private double calculateSize(String sizedata) {
        if (sizedata.contains("Filesize yet unknown")) {
            return -1;
        }
        Matcher fileSizeMatcher = FILE_SIZE_PATTERN.matcher(sizedata);
        if (fileSizeMatcher.find()) {
            return calculateSize(fileSizeMatcher.group("size"), fileSizeMatcher.group("sizeUnit"));
        } else {
            return -1;
        }
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
    public double getSize() {
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
