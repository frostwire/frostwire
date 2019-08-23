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

package com.frostwire.search.torlock;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;

import org.apache.commons.io.FilenameUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorLockSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;

    public TorLockSearchResult(String domainName, String detailsUrl, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.infoHash = matcher.group("infohash");
        this.filename = parseFileName(matcher.group("filename"), FilenameUtils.getBaseName(detailsUrl)).replaceAll("<font color=\".*?\">|</font>", "");
        this.size = parseSize(matcher.group("filesize"));
        this.creationTime = parseCreationTime(matcher.group("time"));
        this.seeds = parseSeeds(matcher.group("seeds"));
        this.torrentUrl = UrlUtils.buildMagnetUrl(infoHash, filename, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
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
        return "TorLock";
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

    private String parseFileName(String urlEncodedFileName, String fallbackName) {
        String decodedFileName = fallbackName;
        if (!StringUtils.isNullOrEmpty(urlEncodedFileName)) {
            try {
                decodedFileName = URLDecoder.decode(urlEncodedFileName, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            decodedFileName.replace("&amp;", "and");
        }
        return decodedFileName + ".torrent";
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
            SimpleDateFormat myFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            result = myFormat.parse(dateString).getTime();
        } catch (Throwable t) {
        }
        return result;
    }
}
