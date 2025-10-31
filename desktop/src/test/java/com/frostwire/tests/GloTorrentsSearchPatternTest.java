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
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.glotorrents.GloTorrentsSearchPattern;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * GloTorrents V2 Search Pattern Test
 * Performs real searches against gtso.cc to validate pattern matching
 *
 * gradle test --tests "com.frostwire.tests.GloTorrentsSearchPatternTest.gloTorrentsSearchTest"
 */
public final class GloTorrentsSearchPatternTest {
    private static final Logger LOG = Logger.getLogger(GloTorrentsSearchPatternTest.class);

    @Test
    public void gloTorrentsSearchTest() {
        String searchTerm = "ubuntu";

        // Create V2 SearchPerformer using SearchPerformerFactory
        com.frostwire.search.SearchPerformer performer =
            SearchPerformerFactory.createSearchPerformer(
                1,
                searchTerm,
                new GloTorrentsSearchPattern(),
                null,  // No crawling strategy needed
                5000   // 5 second timeout
            );

        GloTorrentsSearchListener listener = new GloTorrentsSearchListener();
        performer.setListener(listener);

        try {
            LOG.info("GloTorrentsSearchPatternTest: Starting search for '" + searchTerm + "'");
            performer.perform();
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

        LOG.info("GloTorrentsSearchPatternTest: PASSED");
    }

    static class GloTorrentsSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.size() == 0) {
                failedTests.add("No search results returned");
                return;
            }

            LOG.info("GloTorrentsSearchListener.onResults: Got " + results.size() + " results");

            for (SearchResult result : results) {
                com.frostwire.search.FileSearchResult sr = (com.frostwire.search.FileSearchResult) result;
                LOG.info("GloTorrentsSearchListener.onResults:");
                LOG.info("\t DisplayName: " + sr.getDisplayName());
                LOG.info("\t Size: " + sr.getSize());
                LOG.info("\t Source: " + sr.getSource());

                if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                    failedTests.add("getDisplayName is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getSource())) {
                    failedTests.add("getSource is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getDetailsUrl())) {
                    failedTests.add("getDetailsUrl is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                    failedTests.add("getFilename is null or empty");
                }

                if (!sr.isTorrent()) {
                    failedTests.add("Result should be a torrent");
                }

                if (sr.isPreliminary()) {
                    failedTests.add("Result should NOT be preliminary (complete data available)");
                }

                if (failedTests.size() > 0) {
                    return;
                }
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            failedTests.add("Search error: " + error.message());
        }

        @Override
        public void onStopped(long token) {
        }

        public String getFailedMessages() {
            if (failedTests.size() == 0) {
                return "";
            }
            StringBuilder buffer = new StringBuilder();
            for (String msg : failedTests) {
                buffer.append(msg);
                buffer.append("\n");
            }
            return buffer.toString();
        }
    }
}
