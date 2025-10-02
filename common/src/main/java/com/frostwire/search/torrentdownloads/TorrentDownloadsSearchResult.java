/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.torrentdownloads;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.DateParser;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * @author alejandroarturom
 */
public final class TorrentDownloadsSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    TorrentDownloadsSearchResult(String domainName, String detailsUrl, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.filename = parseFileName(matcher.group("filename"), FilenameUtils.getBaseName(detailsUrl));
        this.size = parseSize(matcher.group("filesize") + " " + matcher.group("unit"));
        this.creationTime = parseCreationTime(matcher.group("time"));
        this.seeds = parseSeeds(matcher.group("seeds"));
        this.torrentUrl = matcher.group("magnet");
        this.infoHash = UrlUtils.extractInfoHash(torrentUrl);
        this.displayName = HtmlManipulator.replaceHtmlEntities(FilenameUtils.getBaseName(filename));
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
        return "TorrentDownloads";
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
        return DateParser.parseSimpleDate(dateString);
    }
}
