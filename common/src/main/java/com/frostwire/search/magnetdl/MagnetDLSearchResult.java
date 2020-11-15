/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.search.magnetdl;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.UrlUtils;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author gubatron
 */
public final class MagnetDLSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final double size;
    private final long creationTime;
    private final int seeds;

    MagnetDLSearchResult(String detailsUrl,
                             String magnet,
                             String fileSize,
                             String unit,
                             String age,
                             String seeds,
                             String title) {
        this.detailsUrl = detailsUrl;
        this.torrentUrl = magnet + "&" + UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS;
        this.infoHash = magnet.substring(20,60);
        this.filename = parseFileName(title);
        this.size = parseSize(fileSize + " " + unit);
        this.creationTime = parseAgeString(age);
        this.seeds = parseSeeds(seeds);
        this.displayName = title;
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
        return "MagnetDL";
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

    private long parseAgeString(String dateString) {
        long now = System.currentTimeMillis();
        try {
            if (dateString.contains("year")) {
                int years = Integer.parseInt(dateString.substring(0, dateString.indexOf(' ')));
                long yearInMillis = 365L * 24L * 60L * 60L * 1000L;
                return now - years * yearInMillis;
            }
            if (dateString.contains("month")) {
                int months = Integer.parseInt(dateString.substring(0, dateString.indexOf(' ')));
                long monthInMillis = 31L * 24L * 60L * 60L * 1000L;
                return now - months * monthInMillis;
            }
            if (dateString.contains("day")) {
                int days = Integer.parseInt(dateString.substring(0, dateString.indexOf(' ')));
                long dayInMillis = 24L * 60L * 60L * 1000L;
                return now - days * dayInMillis;
            }
            if (dateString.contains("hour")) {
                int hours = Integer.parseInt(dateString.substring(0, dateString.indexOf(' ')));
                long hourInMillis = 60L * 60L * 1000L;
                return now - hours * hourInMillis;
            }
            if (dateString.contains("Yesterday")) {
                return now - 24L * 60L * 60L * 1000L; // one day in milliseconds
            }
            // this format seems to be not used anymore
            SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            now = myFormat.parse(dateString.trim()).getTime();
        } catch (Throwable t) {
        }
        return now;
    }
}
