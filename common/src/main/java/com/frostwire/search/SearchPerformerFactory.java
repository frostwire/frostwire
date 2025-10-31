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

package com.frostwire.search;

import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.idope.IdopeSearchPattern;
import com.frostwire.util.UrlUtils;

/**
 * Factory for creating ISearchPerformer instances with appropriate patterns and strategies.
 * Creates V2 SearchPerformer concrete instances that implement the ISearchPerformer interface.
 *
 * @author gubatron
 */
public class SearchPerformerFactory {
    private static final int DEFAULT_TIMEOUT = 30000;
    private static final TorrentCrawlingStrategy DEFAULT_TORRENT_CRAWLING = new TorrentCrawlingStrategy();

    /**
     * Creates a torrent search performer for idope.
     *
     * @param token the search token
     * @param keywords the search keywords
     * @param timeout the HTTP timeout
     * @return a configured ISearchPerformer
     */
    public static ISearchPerformer createIdopeTorrentSearch(long token, String keywords, int timeout) {
        return new SearchPerformer(
                token,
                keywords,
                UrlUtils.encode(keywords),
                new IdopeSearchPattern(),
                DEFAULT_TORRENT_CRAWLING,  // Idope returns magnet links, but we can still crawl for more details
                timeout
        );
    }

    /**
     * Creates a simple search performer without crawling.
     *
     * @param token the search token
     * @param keywords the search keywords
     * @param pattern the search pattern
     * @return a configured ISearchPerformer
     */
    public static ISearchPerformer createSimpleSearch(long token, String keywords, SearchPattern pattern) {
        return new SearchPerformer(
                token,
                keywords,
                UrlUtils.encode(keywords),
                pattern,
                null,  // No crawling
                DEFAULT_TIMEOUT
        );
    }

    /**
     * Creates a search performer with crawling capability.
     *
     * @param token the search token
     * @param keywords the search keywords
     * @param pattern the search pattern
     * @param crawlingStrategy the crawling strategy
     * @return a configured ISearchPerformer
     */
    public static ISearchPerformer createCrawlingSearch(long token, String keywords, SearchPattern pattern, CrawlingStrategy crawlingStrategy) {
        return new SearchPerformer(
                token,
                keywords,
                UrlUtils.encode(keywords),
                pattern,
                crawlingStrategy,
                DEFAULT_TIMEOUT
        );
    }

    /**
     * Creates a search performer with custom timeout.
     *
     * @param token the search token
     * @param keywords the search keywords
     * @param pattern the search pattern
     * @param crawlingStrategy the crawling strategy (null if no crawling)
     * @param timeout the HTTP timeout in milliseconds
     * @return a configured ISearchPerformer
     */
    public static ISearchPerformer createSearchPerformer(long token, String keywords, SearchPattern pattern, CrawlingStrategy crawlingStrategy, int timeout) {
        return new SearchPerformer(
                token,
                keywords,
                UrlUtils.encode(keywords),
                pattern,
                crawlingStrategy,
                timeout
        );
    }
}
