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
