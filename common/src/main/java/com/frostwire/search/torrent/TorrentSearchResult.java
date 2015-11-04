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

import com.frostwire.search.FileSearchResult;

/**
 * @author gubatron
 * @author aldenml
 */
public interface TorrentSearchResult extends FileSearchResult {

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
