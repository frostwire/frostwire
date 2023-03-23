/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torrent;

import com.frostwire.search.AbstractCrawledSearchResult;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorrentCrawledAlbumSearchResult extends AbstractCrawledSearchResult<TorrentCrawlableSearchResult> implements TorrentSearchResult {
    private final String artist;
    private final String album;
    private final List<TorrentItemSearchResult> items;
    private final String displayName;
    private final long size;

    public TorrentCrawledAlbumSearchResult(TorrentCrawlableSearchResult sr, String artist, String album, List<TorrentItemSearchResult> items) {
        super(sr);
        this.artist = artist;
        this.album = album;
        this.items = items;
        this.displayName = buildDisplayName(artist, album);
        this.size = buildSize(items);
    }

    public String artist() {
        return artist;
    }

    public String album() {
        return album;
    }

    public List<TorrentItemSearchResult> items() {
        return items;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getFilename() {
        return parent.getFilename();
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public String getTorrentUrl() {
        return parent.getTorrentUrl();
    }

    @Override
    public String getReferrerUrl() {
        return parent.getReferrerUrl();
    }

    @Override
    public int getSeeds() {
        return parent.getSeeds();
    }

    @Override
    public String getHash() {
        return parent.getHash();
    }

    @Override
    public String getThumbnailUrl() {
        return parent.getThumbnailUrl();
    }

    @Override
    public String toString() {
        return "Torrent Album: " + displayName + ", files: " + items.size() + ", size: " + size;
    }

    private String buildDisplayName(String artist, String album) {
        String result = "";
        if (StringUtils.isNotBlank(album)) {
            result = album;
        } else {
            result = "Unknown album";
        }
        if (StringUtils.isNotBlank(artist)) {
            result = artist + " - " + album;
        }
        return result;
    }

    private long buildSize(List<TorrentItemSearchResult> items) {
        long r = 0;
        for (TorrentItemSearchResult sr : items) {
            r += sr.getSize();
        }
        return r;
    }
}
