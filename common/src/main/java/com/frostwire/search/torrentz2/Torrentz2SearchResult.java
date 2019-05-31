/*
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torrentz2;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import org.apache.commons.io.FilenameUtils;

import java.text.SimpleDateFormat;
import java.util.Locale;

public final class Torrentz2SearchResult extends AbstractTorrentSearchResult {

    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;


    public Torrentz2SearchResult(String detailsUrl, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.infoHash = matcher.group("infohash");
        this.filename = parseFileName(matcher.group("filename"));
        this.size = parseSize(matcher.group("filesize") + " " + matcher.group("unit"));
        this.creationTime = parseCreationTime(matcher.group("time"));
        this.seeds = parseSeeds(matcher.group("seeds"));
        this.torrentUrl = "magnet:" + matcher.group("magnet_part");
        this.displayName = HtmlManipulator.replaceHtmlEntities(FilenameUtils.getBaseName(filename));
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
    public String getSource() {
        return "LimeTorrents";
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

    private String parseFileName(String decodedFileName) {
        return HtmlManipulator.replaceHtmlEntities(decodedFileName.trim()) + ".torrent";
    }

    private int parseSeeds(String group) {
        try {
            return Integer.parseInt(group);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            if (dateString.contains("1 Year+")) {
                return result - 365L * 24L * 60L * 60L * 1000L; // a year in milliseconds
            }

            if (dateString.contains("Last Month")) {
                return result - 31L * 24L * 60L * 60L * 1000L; // a month in milliseconds
            }

            if (dateString.contains("Yesterday")) {
                return result - 1L * 24L * 60L * 60L * 1000L; // one day in milliseconds
            }

            // this format seems to be not used anymore
            SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            result = myFormat.parse(dateString.trim()).getTime();
        } catch (Throwable t) {
        }
        return result;
    }
}
