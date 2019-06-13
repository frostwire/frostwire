/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.eztv;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.StringUtils;
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
public final class EztvSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;

    public EztvSearchResult(String detailsUrl, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        String dispName = null;
        if (matcher.group("displayname") != null) {
            dispName = matcher.group("displayname");
        } else if (matcher.group("displayname2") != null) {
            dispName = matcher.group("displayname2");
        } else if (matcher.group("displaynamefallback") != null) {
            dispName = matcher.group("displaynamefallback");
        }
        this.displayName = HtmlManipulator.replaceHtmlEntities(dispName).trim();
        this.torrentUrl = buildTorrentUrl(matcher);
        this.filename = parseFileName(FilenameUtils.getName(torrentUrl));
        this.infoHash = parseInfoHash(matcher, torrentUrl);
        this.seeds = parseSeeds(matcher.group("seeds"));
        this.creationTime = parseCreationTime(matcher.group("creationtime"));
        this.size = parseSize(matcher.group("filesize"));
    }

    private static String parseInfoHash(SearchMatcher matcher, String torrentUrl) {
        try {
            if (matcher.group("infohash") != null) {
                return matcher.group("infohash");
            } else if (torrentUrl.startsWith("magnet:?xt=urn:btih:")) {
                return torrentUrl.substring(20, 52);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String buildTorrentUrl(SearchMatcher matcher) {
        String url = null;
        if (matcher.group("torrenturl") != null) {
            url = matcher.group("torrenturl");
            url = url.replaceAll(" ", "%20");
        } else if (matcher.group("magneturl") != null) {
            url = matcher.group("magneturl");
        }
        return url;
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
    public String getSource() {
        return "Eztv";
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

    private String parseFileName(String urlEncodedFileName) {
        String decodedFileName = null;
        if (!StringUtils.isNullOrEmpty(urlEncodedFileName)) {
            try {
                decodedFileName = URLDecoder.decode(urlEncodedFileName, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return decodedFileName;
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            dateString = dateString.replaceAll("(st|nd|rd|th)", "");
            SimpleDateFormat myFormat = new SimpleDateFormat("d MMM yyyy", Locale.US);
            result = myFormat.parse(dateString).getTime();
        } catch (Throwable ignored) {
        }
        return result;
    }
}
