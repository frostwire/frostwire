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

package com.frostwire.tests;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.idope.IdopeSearchPattern;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Idope V2 Search Pattern Test
 * Performs real searches against idope.pics to validate pattern matching
 *
 * gradle test --tests "com.frostwire.tests.IdopeSearchPatternTest.idopeSearchTest"
 */
public final class IdopeSearchPatternTest {
    private static final Logger LOG = Logger.getLogger(IdopeSearchPatternTest.class);

    @Test
    public void idopeSearchTest() {
        String searchTerm = "ubuntu";

        ISearchPerformer performer =
            SearchPerformerFactory.createSearchPerformer(
                1,
                searchTerm,
                new IdopeSearchPattern(),
                null,
                5000
            );

        IdopeSearchListener listener = new IdopeSearchListener();
        performer.setListener(listener);

        try {
            LOG.info("IdopeSearchPatternTest: Starting search for '" + searchTerm + "'");
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

        LOG.info("IdopeSearchPatternTest: PASSED");
    }

    static class IdopeSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.size() == 0) {
                LOG.warn("idope API returned no results — API may be down or changed");
                return;
            }

            LOG.info("IdopeSearchListener.onResults: Got " + results.size() + " results");

            int validCount = 0;
            for (SearchResult result : results) {
                CompositeFileSearchResult sr = (CompositeFileSearchResult) result;
                LOG.info("IdopeSearchListener.onResults:");
                LOG.info("\t DisplayName: " + sr.getDisplayName());
                LOG.info("\t Size: " + sr.getSize());
                LOG.info("\t Source: " + sr.getSource());
                LOG.info("\t Seeds: " + (sr.getSeeds().isPresent() ? sr.getSeeds().get() : "0"));

                if (sr.getDisplayName().equals("No results returned")) {
                    LOG.info("\t Skipping idope placeholder result");
                    continue;
                }

                validCount++;

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
                    failedTests.add("Result should NOT be preliminary (complete data from API)");
                }

                if (sr.getSize() <= 0) {
                    failedTests.add("Size should be greater than 0");
                }

                if (failedTests.size() > 0) {
                    return;
                }
            }

            if (validCount == 0) {
                LOG.warn("idope API returned only placeholder results — API may be down or changed");
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
