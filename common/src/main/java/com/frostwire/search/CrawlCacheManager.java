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

package com.frostwire.search;

import com.frostwire.util.Logger;

/**
 * Manager for crawl result caching and magnet downloading.
 * Extracted from CrawlPagedWebSearchPerformer to centralize configuration.
 *
 * This class manages:
 * - Crawl result caching (improves performance)
 * - Magnet downloader registration (needed for torrent results)
 */
public final class CrawlCacheManager {
    private static final Logger LOG = Logger.getLogger(CrawlCacheManager.class);
    private static CrawlCache cache = null;
    private static MagnetDownloader magnetDownloader = null;

    private CrawlCacheManager() {
        // utility class
    }

    /**
     * Sets the cache implementation for crawl results.
     *
     * @param crawlCache the cache implementation to use
     */
    public static void setCache(CrawlCache crawlCache) {
        CrawlCacheManager.cache = crawlCache;
    }

    /**
     * Gets the current crawl cache.
     *
     * @return the current cache implementation, or null if not set
     */
    public static CrawlCache getCache() {
        return cache;
    }

    /**
     * Sets the magnet downloader for handling magnet links.
     *
     * @param downloader the magnet downloader implementation
     */
    public static void setMagnetDownloader(MagnetDownloader downloader) {
        CrawlCacheManager.magnetDownloader = downloader;
    }

    /**
     * Gets the current magnet downloader.
     *
     * @return the current magnet downloader, or null if not set
     */
    public static MagnetDownloader getMagnetDownloader() {
        return magnetDownloader;
    }

    /**
     * Clears all cached crawl results.
     */
    public static void clearCache() {
        if (cache != null) {
            try {
                cache.clear();
            } catch (Exception e) {
                LOG.warn("Error clearing crawl cache: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the number of entries in the cache.
     *
     * @return the number of cached entries, or 0 if cache is not set
     */
    public static long getCacheNumEntries() {
        if (cache != null) {
            try {
                return cache.numEntries();
            } catch (Exception e) {
                LOG.warn("Error getting cache size: " + e.getMessage());
                return 0;
            }
        }
        return 0;
    }
}
