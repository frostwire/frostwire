/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search.nyaa;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentCrawlableSearchResult;
import org.apache.commons.io.FilenameUtils;

import java.util.HashMap;
import java.util.Map;

public class NyaaSearchResult implements TorrentCrawlableSearchResult {
    private static final Map<String, Long> UNIT_TO_BYTES;

    static {
        UNIT_TO_BYTES = new HashMap<>();
        UNIT_TO_BYTES.put("bytes", 1L);
        UNIT_TO_BYTES.put("B", 1L);
        UNIT_TO_BYTES.put("KiB", 1024L);
        UNIT_TO_BYTES.put("MiB", 1024L * 1024L);
        UNIT_TO_BYTES.put("GiB", 1024L * 1024L * 1024L);
        UNIT_TO_BYTES.put("TiB", 1024L * 1024L * 1024L * 1024L);
        UNIT_TO_BYTES.put("PiB", 1024 * 1024 * 1024 * 1024L * 1024L);
    }

    private final String detailsUrl;
    private final String thumbnailUrl;
    private final String displayName;
    private final String hash;
    private final long creationTime;
    private final String torrentUrl;
    private final String fileName;
    private final int seeds;
    private final long fileSize;

    NyaaSearchResult(String urlPrefix, SearchMatcher matcher) {
        detailsUrl = urlPrefix + matcher.group("detailsurl");
        thumbnailUrl = urlPrefix + matcher.group("thumbnailurl");
        displayName = matcher.group("displayname");
        hash = parseHash(matcher.group("magneturl"));
        creationTime = Long.parseLong(matcher.group("timestamp"));
        String extension = FilenameUtils.getExtension(displayName);
        fileName = displayName + "." + ((extension.isEmpty()) ? "torrent" : extension);
        torrentUrl = urlPrefix + matcher.group("torrenturl");
        seeds = Integer.parseInt(matcher.group("seeds"));
        fileSize = parseSize(matcher.group("filesize"));
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
        return thumbnailUrl;
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

    private String parseHash(String magneturl) {
        if (magneturl.startsWith("magnet:?xt=urn:btih:")) {
            return magneturl.substring(20, 52);
        }
        return "";
    }

    private long parseSize(String size) {
        String[] sizearr = size.trim().split(" ");
        String amount = sizearr[0].trim();
        String unit = sizearr[1].trim();
        return calculateSize(amount, unit);
    }

    private long calculateSize(String amount, String unit) {
        if (amount == null || unit == null) {
            return -1;
        }
        Long unitMultiplier = UNIT_TO_BYTES.get(unit);
        if (unitMultiplier == null) {
            unitMultiplier = UNIT_TO_BYTES.get("bytes");
        }
        //fractional size
        if (amount.indexOf(".") > 0) {
            float floatAmount = Float.parseFloat(amount);
            return (long) (floatAmount * unitMultiplier);
        }
        //integer based size
        else {
            int intAmount = Integer.parseInt(amount);
            return intAmount * unitMultiplier;
        }
    }
}
