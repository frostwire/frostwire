/*
 *     Created by Angel Leon (@gubatron)
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

import com.frostwire.search.SearchListener;

import java.util.List;

/**
 * Strategy interface for crawling into search results to get more detailed information.
 * Implementations handle caching, threading, and result parsing for crawled data.
 *
 * @author gubatron
 */
public interface CrawlingStrategy {
    /**
     * Crawls into the given results to extract additional information.
     * Updates results in-place by setting crawled children via FileSearchResult.setCrawlableChildren().
     *
     * @param results the initial search results to crawl into
     * @param listener the search listener to notify when crawling is complete
     * @param token the search token for identifying the search session
     */
    void crawlResults(List<FileSearchResult> results, SearchListener listener, long token);
}
