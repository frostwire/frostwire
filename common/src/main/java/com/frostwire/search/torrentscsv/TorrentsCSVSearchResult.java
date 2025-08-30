/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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