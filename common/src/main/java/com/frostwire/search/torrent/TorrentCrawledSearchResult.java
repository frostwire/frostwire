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

import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.search.AbstractCrawledSearchResult;
import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorrentCrawledSearchResult extends AbstractCrawledSearchResult<TorrentCrawlableSearchResult> implements TorrentItemSearchResult {

    private final TorrentInfo ti;
    private final int fileIndex;
    private final String filePath;
    private final String displayName;
    private final String filename;
    private final long size;

    public TorrentCrawledSearchResult(TorrentCrawlableSearchResult sr, TorrentInfo ti, int fileIndex, String filePath, long fileSize) {
        super(sr);
        this.ti = ti;
        this.fileIndex = fileIndex;
        this.filePath = filePath;
        this.filename = FilenameUtils.getName(this.filePath);
        this.size = fileSize;
        this.displayName = FilenameUtils.getBaseName(this.filename);
    }

    public TorrentInfo getTorrentInfo() {
        return ti;
    }

    public int getFileIndex() {
        return fileIndex;
    }

    @Override
    public String getFilePath() {
        return filePath;
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
}
