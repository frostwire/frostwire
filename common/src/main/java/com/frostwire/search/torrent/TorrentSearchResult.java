/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

import com.frostwire.search.FileSearchResult;

/**
 * @author gubatron
 * @author aldenml
 */
public interface TorrentSearchResult extends FileSearchResult {
    /** Creation time, in milliseconds */
    long getCreationTime();

    /**
     * Returns the torrent uri, could be a magnet uri.
     * <p/>
     * Should be renamed to getTorrentUri
     *
     * @return
     */
    String getTorrentUrl();

    /**
     * Returns a URL to be used as an HTTP "Referer" (sic) header
     * when requesting the .torrent file.
     *
     * @return
     */
    String getReferrerUrl();

    int getSeeds();

    /**
     * Returns the info hash of the torrent.
     * <p/>
     * Should be renamed to getInfoHash
     *
     * @return
     */
    String getHash();
}
