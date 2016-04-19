/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.search.monova;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import org.apache.commons.io.FilenameUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class MonovaSearchResult extends AbstractTorrentSearchResult {
    private String filename;
    private String displayName;
    private String detailsUrl;
    private String torrentUrl;
    private String infoHash;
    private long size;
    private long creationTime;
    private int seeds;

    public MonovaSearchResult(String detailsUrl, SearchMatcher matcher) {
        /*
         * Matcher groups cheat sheet
         * 1 -> .torrent URL
         * 2 -> infoHash
         * 3 -> seeds
         * 4 -> SIZE (B|KiB|MiBGiB)
         */
        this.detailsUrl = detailsUrl;
        this.filename = parseFileName(FilenameUtils.getName(matcher.group("filename")));
        this.displayName = parseDisplayName(HtmlManipulator.replaceHtmlEntities(FilenameUtils.getBaseName(filename)));
        this.infoHash = matcher.group("infohash");
        this.creationTime = parseCreationTime(matcher.group("creationtime"));
        this.size = parseSize(matcher.group("size"));
        this.seeds = parseSeeds(matcher.group("seeds"));

        // Monova can't handle direct download of torrents without some sort of cookie
        //the torcache url wont resolve into direct .torrent
        this.torrentUrl = "magnet:?xt=urn:btih:" + infoHash;
    }

    private String parseDisplayName(String fileName) {
        return fileName.replaceAll("_"," ");
    }

    private String parseFileName(String name) {
        String[] split = name.split("title\\=");
        if (split.length > 1) {
            name = split[1];
            if (name.endsWith("(")) {
                name = name.substring(0, -1).trim();
            }
        }
        return name + ".torrent";
    }

    private int parseSeeds(String group) {
        try {
            return Integer.parseInt(group);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseCreationTime(String dateString) {
        //Apr 10, 2016 06:21:20
        SimpleDateFormat date = new SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.US);
        long result = System.currentTimeMillis();
        try {
            result = date.parse(dateString).getTime();
        } catch (ParseException e) {
        }
        return result;
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
        return "Monova";
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
}