/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.search;

import com.frostwire.licenses.License;

/**
 * @author gubatron
 * @author aldenml
 */
public interface SearchResult {
    String getDisplayName();

    String getDetailsUrl();

    long getCreationTime();

    String getSource();

    License getLicense();

    String getThumbnailUrl();

    /**
     * Indicates if this is a preliminary/partial search result that requires a secondary search
     * or additional step before the actual download can begin.
     *
     * Examples: YouTube videos (need format selection), torrents (need file selection),
     * crawlable results (need to fetch content list).
     *
     * When true, the UI should show a "+" icon instead of a download icon, and clicking
     * it should trigger a secondary search rather than starting a direct download.
     *
     * @return true if this is a preliminary result requiring additional interaction, false for direct downloads
     */
    default boolean isPreliminary() {
        return false;
    }
}
