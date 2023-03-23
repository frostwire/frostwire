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

package com.frostwire.search.archiveorg;

import com.frostwire.search.torrent.TorrentSearchResult;

/**
 * @author gubatron
 * @author aldenml
 */
public class ArchiveorgTorrentSearchResult extends ArchiveorgCrawledSearchResult implements TorrentSearchResult {
    private final long size;

    public ArchiveorgTorrentSearchResult(ArchiveorgSearchResult sr, ArchiveorgFile file, long size) {
        super(sr, file);
        this.size = size;
    }

    @Override
    public String getTorrentUrl() {
        return getDownloadUrl();
    }

    @Override
    public String getReferrerUrl() {
        return getDetailsUrl();
    }

    @Override
    public int getSeeds() {
        return 3;
    }

    @Override
    public String getHash() {
        return null;
    }

    @Override
    public long getSize() {
        return size;
    }
}
