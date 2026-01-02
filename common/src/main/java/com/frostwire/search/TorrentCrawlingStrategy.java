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

import com.frostwire.licenses.License;
import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchListener;
import com.frostwire.search.torrent.TorrentCrawlableSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy for crawling torrent files to extract file listings.
 * Caches .torrent files to avoid repeated downloads.
 *
 * @author gubatron
 */
public class TorrentCrawlingStrategy implements CrawlingStrategy {
    private static final Logger LOG = Logger.getLogger(TorrentCrawlingStrategy.class);
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_MAX_SIZE = 1000;

    private final HttpClient httpClient;
    private final int timeout;
    private final int maxCrawls;

    public TorrentCrawlingStrategy() {
        this(HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH), 30000, 100);
    }

    public TorrentCrawlingStrategy(HttpClient httpClient, int timeout, int maxCrawls) {
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.maxCrawls = maxCrawls;
    }

    @Override
    public void crawlResults(List<FileSearchResult> results, SearchListener listener, long token) {
        int crawlCount = 0;
        List<FileSearchResult> crawledResults = new ArrayList<>();

        for (FileSearchResult fsr : results) {
            if (crawlCount >= maxCrawls) {
                break;
            }

            if (fsr instanceof CompositeFileSearchResult) {
                CompositeFileSearchResult result = (CompositeFileSearchResult) fsr;
                if (result.isTorrent()) {
                    try {
                        String torrentUrl = result.getTorrentUrl().orElse(null);
                        if (torrentUrl != null) {
                            byte[] torrentData = fetchTorrentData(torrentUrl);
                            if (torrentData != null) {
                                // Extract files from torrent - result will be updated in-place
                                List<? extends com.frostwire.search.SearchResult> crawledSearchResults = PerformersHelper.crawlTorrentInfo(
                                        null,  // No performer reference needed in v2
                                        new LegacyTorrentCrawlableResult(result),
                                        torrentData
                                );

                                if (!crawledSearchResults.isEmpty()) {
                                    // Cast the results to FileSearchResult
                                    List<FileSearchResult> children = new ArrayList<>();
                                    for (com.frostwire.search.SearchResult sr : crawledSearchResults) {
                                        if (sr instanceof FileSearchResult) {
                                            children.add((FileSearchResult) sr);
                                        }
                                    }
                                    if (!children.isEmpty()) {
                                        result.setCrawlableChildren(children);
                                        crawledResults.add(result);
                                        crawlCount++;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("Error crawling torrent " + result.getFilename() + ": " + e.getMessage());
                    }
                }
            }
        }

        // Report results to listener
        if (listener != null) {
            listener.onResults(token, (List) results);
        }
    }

    private byte[] fetchTorrentData(String torrentUrl) {
        // Check cache first
        byte[] cachedData = CACHE.get(torrentUrl);
        if (cachedData != null) {
            return cachedData;
        }

        // Fetch from URL
        try {
            byte[] data = httpClient.getBytes(torrentUrl, timeout, "FrostWire/1.0", null);
            if (data != null && data.length > 0) {
                // Store in cache (with simple size management)
                if (CACHE.size() < CACHE_MAX_SIZE) {
                    CACHE.put(torrentUrl, data);
                }
            }
            return data;
        } catch (Exception e) {
            LOG.warn("Error fetching torrent: " + e.getMessage());
            return null;
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static int getCacheSize() {
        return CACHE.size();
    }

    /**
     * Adapter to make CompositeFileSearchResult compatible with PerformersHelper.crawlTorrentInfo()
     * which expects a TorrentCrawlableSearchResult interface.
     */
    private static class LegacyTorrentCrawlableResult implements TorrentCrawlableSearchResult {
        private final CompositeFileSearchResult result;

        public LegacyTorrentCrawlableResult(CompositeFileSearchResult result) {
            this.result = result;
        }

        @Override
        public String getDisplayName() {
            return result.getDisplayName();
        }

        @Override
        public String getDetailsUrl() {
            return result.getDetailsUrl();
        }

        @Override
        public long getCreationTime() {
            return result.getCreationTime();
        }

        @Override
        public String getSource() {
            return result.getSource();
        }

        @Override
        public License getLicense() {
            return result.getLicense();
        }

        @Override
        public String getThumbnailUrl() {
            return result.getThumbnailUrl();
        }

        @Override
        public String getFilename() {
            return result.getFilename();
        }

        @Override
        public long getSize() {
            return result.getSize();
        }

        @Override
        public String getTorrentUrl() {
            return result.getTorrentUrl().orElse(null);
        }

        @Override
        public String getReferrerUrl() {
            return result.getReferrerUrl().orElse(null);
        }

        @Override
        public int getSeeds() {
            return result.getSeeds().orElse(0);
        }

        @Override
        public String getHash() {
            return result.getTorrentHash().orElse(null);
        }

        @Override
        public boolean isComplete() {
            return !result.isCrawlable();
        }
    }
}
