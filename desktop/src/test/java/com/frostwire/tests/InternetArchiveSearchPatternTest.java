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

package com.frostwire.tests;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.internetarchive.InternetArchiveSearchPattern;
import com.frostwire.search.internetarchive.InternetArchiveCrawlingStrategy;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Internet Archive V2 Search Pattern Test
 * Performs real searches against archive.org to validate pattern and crawling
 *
 * gradle test --tests "com.frostwire.tests.InternetArchiveSearchPatternTest.internetarchiveSearchTest"
 */
public final class InternetArchiveSearchPatternTest {
    private static final Logger LOG = Logger.getLogger(InternetArchiveSearchPatternTest.class);

    @Test
    public void internetarchiveSearchTest() {
        String searchTerm = "ubuntu";
        String encodedKeywords = UrlUtils.encode(searchTerm);

        // Create V2 SearchPerformer with crawling strategy
        com.frostwire.search.SearchPerformer performer =
            SearchPerformerFactory.createSearchPerformer(
                1,
                searchTerm,
                new InternetArchiveSearchPattern(),
                new InternetArchiveCrawlingStrategy(),
                5000   // 5 second timeout per request
            );

        InternetArchiveSearchListener listener = new InternetArchiveSearchListener();
        performer.setListener(listener);

        try {
            LOG.info("InternetArchiveSearchPatternTest: Starting search for '" + searchTerm + "'");
            performer.perform();

            // Wait for crawling to complete (10 second timeout)
            if (!listener.waitForCrawling(10, TimeUnit.SECONDS)) {
                LOG.warn("InternetArchiveSearchPatternTest: Crawling timeout - some files may not be displayed");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            LOG.error("Search failed with exception", t);
            fail(t.getMessage());
            return;
        }

        if (listener.failedTests.size() > 0) {
            LOG.error("Search validation failed: " + listener.getFailedMessages());
            fail(listener.getFailedMessages());
        }

        LOG.info("InternetArchiveSearchPatternTest: PASSED");
    }

    static class InternetArchiveSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();
        private CountDownLatch crawlingLatch = new CountDownLatch(1);
        private boolean hasCrawledResults = false;

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.size() == 0) {
                failedTests.add("No search results returned");
                return;
            }

            LOG.info("InternetArchiveSearchListener.onResults: Got " + results.size() + " results");

            for (SearchResult result : results) {
                FileSearchResult sr = (FileSearchResult) result;
                LOG.info("InternetArchiveSearchListener.onResults:");
                LOG.info("\\t DisplayName: " + sr.getDisplayName());
                LOG.info("\\t Size: " + sr.getSize());
                LOG.info("\\t Source: " + sr.getSource());
                LOG.info("\\t isTorrent: " + sr.isTorrent());
                LOG.info("\\t isStreamable: " + sr.isStreamable());
                LOG.info("\\t isPreliminary: " + sr.isPreliminary());

                // First batch should be preliminary results from search
                if (sr.isPreliminary()) {
                    if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                        failedTests.add("Preliminary result: getDisplayName is null or empty");
                    }

                    if (StringUtils.isNullOrEmpty(sr.getSource())) {
                        failedTests.add("Preliminary result: getSource is null or empty");
                    }

                    if (StringUtils.isNullOrEmpty(sr.getDetailsUrl())) {
                        failedTests.add("Preliminary result: getDetailsUrl is null or empty");
                    }

                    if (!sr.isCrawlable()) {
                        failedTests.add("Preliminary result should be crawlable");
                    }
                } else {
                    // Second batch should be crawled file results
                    hasCrawledResults = true;

                    if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                        failedTests.add("Crawled result: getDisplayName is null or empty");
                    }

                    if (StringUtils.isNullOrEmpty(sr.getSource())) {
                        failedTests.add("Crawled result: getSource is null or empty");
                    }

                    if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                        failedTests.add("Crawled result: getFilename is null or empty");
                    }

                    // Crawled result should have one of: torrent, streamable, or size info
                    boolean hasType = sr.isTorrent() || sr.isStreamable() || sr.getSize() > 0;
                    if (!hasType) {
                        failedTests.add("Crawled result should be torrent, streamable, or have valid size");
                    }

                    if (sr.isPreliminary()) {
                        failedTests.add("Crawled result should NOT be preliminary");
                    }
                }

                if (failedTests.size() > 0) {
                    return;
                }
            }

            // Signal that crawling is complete
            if (hasCrawledResults) {
                crawlingLatch.countDown();
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            failedTests.add("Search error: " + error.message());
        }

        @Override
        public void onStopped(long token) {
            crawlingLatch.countDown();
        }

        public boolean waitForCrawling(long timeout, TimeUnit unit) throws InterruptedException {
            return crawlingLatch.await(timeout, unit);
        }

        public String getFailedMessages() {
            if (failedTests.size() == 0) {
                return "";
            }
            StringBuilder buffer = new StringBuilder();
            for (String msg : failedTests) {
                buffer.append(msg);
                buffer.append("\\n");
            }
            return buffer.toString();
        }
    }
}
