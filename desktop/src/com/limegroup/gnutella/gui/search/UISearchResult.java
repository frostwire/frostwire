/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.SearchResult;

import javax.swing.*;

/**
 * A single SearchResult. These are used to create
 * {@link SearchResultDataLine}s to show search results.
 */
public interface UISearchResult {
    /**
     * @return the file name
     */
    String getFilename();

    /**
     * Gets the size of this SearchResult.
     */
    double getSize();

    /**
     * @return milliseconds since January 01, 1970 the artifact of t
     */
    long getCreationTime();

    String getSource();

    /**
     * Returns the extension of this result.
     */
    String getExtension();

    void download(boolean partial);

    JPopupMenu createMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp);

    String getHash();

    int getSeeds();

    SearchEngine getSearchEngine();

    SearchResult getSearchResult();

    void showSearchResultWebPage(boolean now);

    String getDetailsUrl();

    String getDisplayName();

    String getQuery();

    void play();
}