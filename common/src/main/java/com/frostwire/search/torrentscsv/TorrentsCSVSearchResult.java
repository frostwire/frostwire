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

package com.frostwire.search.torrentscsv;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author gubatron
 */
public final class TorrentsCSVSearchResult extends AbstractTorrentSearchResult {
    private final String infoHash;
    private final String filename;
    private final String displayName;
    private final String magnetUrl;
    private final long size;
    private final long creationTime;
    private final int seeds;

    public TorrentsCSVSearchResult(String infoHash, String filename, 
                                   String displayName, String magnetUrl, long size, 
                                   String creationTimeStr, int seeds) {
        this.infoHash = infoHash;
        this.filename = filename;
        this.displayName = displayName;
        this.magnetUrl = magnetUrl;
        this.size = size;
        this.creationTime = parseCreationTime(creationTimeStr);
        this.seeds = seeds;
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
        return null;
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
    public long getSize() {
        return size;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getSource() {
        return "TorrentsCSV";
    }

    @Override
    public String getTorrentUrl() {
        return magnetUrl;
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            // Common date formats that might be used
            SimpleDateFormat[] formats = {
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                new SimpleDateFormat("yyyy-MM-dd", Locale.US),
                new SimpleDateFormat("dd/MM/yyyy", Locale.US),
                new SimpleDateFormat("MM/dd/yyyy", Locale.US),
                new SimpleDateFormat("yyyy/MM/dd", Locale.US)
            };
            
            for (SimpleDateFormat format : formats) {
                try {
                    result = format.parse(dateString).getTime();
                    break;
                } catch (Exception ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }
}