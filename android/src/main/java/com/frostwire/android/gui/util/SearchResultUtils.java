/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.android.gui.util;

import com.frostwire.search.SearchResult;

/**
 * Utility methods for working with SearchResult objects.
 */
public final class SearchResultUtils {

    private SearchResultUtils() {
        // Utility class, not instantiable
    }

    /**
     * Checks if a search result is from YouTube (Telluride Preliminary Search Result).
     * YouTube results have details URLs containing www.youtube.com.
     * These results need format/quality selection via the Telluride dialog.
     *
     * Example URL: https://www.youtube.com/watch?v=videoId
     *
     * @param sr The SearchResult to check
     * @return true if this is a YouTube search result, false otherwise
     */
    public static boolean isYouTubeSearchResult(SearchResult sr) {
        if (sr == null) {
            return false;
        }
        String detailsUrl = sr.getDetailsUrl();
        return detailsUrl != null && detailsUrl.contains("youtube.com");
    }
}
