
/*
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.search.nyaa;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentCrawlableSearchResult;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NyaaSearchResult implements TorrentCrawlableSearchResult {

    private final String detailsUrl;
    private final String displayName;
    private final String hash;
    private final long creationTime;
    private final String torrentUrl;
    private final String fileName;
    private final int seeds;
    private final long fileSize;

    private static final Map<String, Integer> UNIT_TO_BYTES;

    static {
        UNIT_TO_BYTES = new HashMap<>();
        UNIT_TO_BYTES.put("bytes", 1);
        UNIT_TO_BYTES.put("B", 1);
        UNIT_TO_BYTES.put("KiB", 1024);
        UNIT_TO_BYTES.put("MiB", 1024 * 1024);
        UNIT_TO_BYTES.put("GiB", 1024 * 1024 * 1024);
        UNIT_TO_BYTES.put("TiB", 1024 * 1024 * 1024 * 1024);
        UNIT_TO_BYTES.put("PiB", 1024 * 1024 * 1024 * 1024 * 1024);
    }

    public NyaaSearchResult(String detailsUrl_, SearchMatcher matcher) {
        detailsUrl = detailsUrl_;
        displayName = null;
        hash = null;
        creationTime = 0;
        fileName = null;
        torrentUrl = null;
        seeds = 0;
        fileSize = 0;
    }

    @Override
    public boolean isComplete() {
        return true;
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
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getSource() {
        return "Nyaa";
    }

    @Override
    public License getLicense() {
        return Licenses.UNKNOWN;
    }

    @Override
    public String getThumbnailUrl() {
        return null;
    }

    @Override
    public String getTorrentUrl() {
        return torrentUrl;
    }

    @Override
    public String getReferrerUrl() {
        return null;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public String getFilename() {
        return fileName;
    }

    @Override
    public long getSize() {
        return fileSize;
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            //dateString = dateString.replaceAll("(st|nd|rd|th)", "");
            SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            result = myFormat.parse(dateString).getTime();
        } catch (Throwable ignored) {
        }
        return result;
    }
}
