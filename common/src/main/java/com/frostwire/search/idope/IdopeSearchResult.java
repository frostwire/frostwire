/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Himanshu Sharma (HimanshuSharma789)
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

package com.frostwire.search.idope;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.UrlUtils;

public final class IdopeSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    IdopeSearchResult(String detailsUrl,
                      String infoHash,
                      String filename,
                      String fileSizeMagnitude,
                      String fileSizeUnit,
                      String ageString,
                      int seeds,
                      String trackers) {
        this.detailsUrl = detailsUrl;
        this.infoHash = infoHash;
        this.displayName = HtmlManipulator.replaceHtmlEntities(filename.trim());
        this.filename = filename + ".torrent";
        this.size = parseSize(fileSizeMagnitude + " " + fileSizeUnit);
        this.creationTime = parseCreationTime(ageString);
        this.seeds = seeds;
        this.torrentUrl = UrlUtils.buildMagnetUrl(infoHash, filename, trackers);
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
        return "Idope";
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

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            String[] ds = dateString.split(" ");
            ds[1] = ds[1].toLowerCase();
            if (ds[1].contains("hour")) {
                try {
                    int hours = Integer.parseInt(ds[0]);
                    return result - (hours * 60 * 60 * 1000L);
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("year")) {
                try {
                    int years = Integer.parseInt(ds[0]);
                    return result - (years * 365L * 24L * 60L * 60L * 1000L); // a year in milliseconds
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("month")) {
                try {
                    int months = Integer.parseInt(ds[0]);
                    return result - (months * 31L * 24L * 60L * 60L * 1000L); // a month in milliseconds
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("week")) {
                try {
                    int weeks = Integer.parseInt(ds[0]);
                    return result - (weeks * 7L * 24L * 60L * 60L * 1000L); // a week in milliseconds
                } catch (Exception ignored) {
                }
            }
            if (ds[1].contains("day")) {
                try {
                    int days = Integer.parseInt(ds[0]);
                    return result - (days * 24L * 60L * 60L * 1000L); // a day in milliseconds
                } catch (Exception ignored) {
                }
            }
            return result;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return result;
    }
}
