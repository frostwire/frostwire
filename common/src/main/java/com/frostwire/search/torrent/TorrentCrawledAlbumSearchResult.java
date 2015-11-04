/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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
