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

package com.frostwire.gui.bittorrent;

/**
 * Enum representing different torrent format types that can be created.
 * 
 * @author gubatron
 * @author aldenml
 */
public enum TorrentType {
    /**
     * Hybrid torrent that supports both v1 and v2 protocols (default, recommended)
     */
    HYBRID("Hybrid (v1 + v2)", "Create hybrid torrents that support both BitTorrent v1 and v2 protocols"),
    
    /**
     * BitTorrent v1 only torrent (legacy format)
     */
    V1_ONLY("v1 Only", "Create torrents using only the legacy BitTorrent v1 protocol"),
    
    /**
     * BitTorrent v2 only torrent (modern format)
     */
    V2_ONLY("v2 Only", "Create torrents using only the modern BitTorrent v2 protocol");

    private final String displayName;
    private final String description;

    TorrentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}