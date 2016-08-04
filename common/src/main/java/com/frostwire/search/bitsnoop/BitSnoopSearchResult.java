/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

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

package com.frostwire.search.bitsnoop;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.StringUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public class BitSnoopSearchResult extends AbstractTorrentSearchResult {
    private String filename;
    private String displayName;
    private String detailsUrl;
    private String torrentUrl;
    private String infoHash;
    private long size;
    private long creationTime;
    private int seeds;

    public BitSnoopSearchResult(String detailsUrl, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        this.infoHash = matcher.group("infohash");
        this.filename = parseFileName(matcher.group("filename"), FilenameUtils.getBaseName(detailsUrl));
        this.torrentUrl = matcher.group("torrenturl").contains("torcache.net") ? matcher.group("magneturl") : matcher.group("torrenturl");
        this.size = parseSize(matcher.group("size"));
        this.seeds = parseSeeds(matcher.group("seeds"));
        this.creationTime = parseCreationTime(matcher.group("creationtime"));
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
        return "BitSnoop";
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
        try {
            if (!StringUtils.isNullOrEmpty(urlEncodedFileName)) {
                decodedFileName = URLDecoder.decode(urlEncodedFileName, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
        }
        return decodedFileName + ".torrent";
    }

    protected long parseSize(String group) {
        String[] size = group.trim().split(" ");
        String amount = size[0].trim();
        amount = amount.replaceAll(",", "");
        String unit = size[1].trim();
        return calculateSize(amount, unit);
    }

    private int parseSeeds(String group) {
        try {
            if (group.indexOf("0 / 0") == -1) {
                group = group.split("\"Seeders\">")[1];
                group = group.split("</span>")[0];
                group = group.replace(",", "");
            } else {
                group = "0";
            }
            return Integer.parseInt(group);
        } catch (Exception e) {
            return 0;
        }
    }

    //dateString looks like 25-OCT-13
    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            SimpleDateFormat myFormat = new SimpleDateFormat("dd-MMM-yy", Locale.US);
            result = myFormat.parse(dateString).getTime();
        } catch (Throwable t) {
        }
        return result;
    }

    @Override
    public String getReferrerUrl() {
        return null;//"https://torcache.net/";
    }
}
