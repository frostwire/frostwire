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