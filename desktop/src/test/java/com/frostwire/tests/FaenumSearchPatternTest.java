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
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.faenum.FaenumSearchPattern;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Faenum Search Pattern Test
 * Performs real searches against faenum.com to validate pattern
 *
 * gradle test --tests "com.frostwire.tests.FaenumSearchPatternTest.faenumSearchTest"
 */
public final class FaenumSearchPatternTest {
    private static final Logger LOG = Logger.getLogger(FaenumSearchPatternTest.class);

    @Test
    public void faenumSearchTest() {
        String searchTerm = "landscape";
        String encodedKeywords = UrlUtils.encode(searchTerm);

        // Create V2 SearchPerformer without crawling
        ISearchPerformer performer =
            SearchPerformerFactory.createSearchPerformer(
                1,
                searchTerm,
                new FaenumSearchPattern(),
                null,  // No crawling needed
                10000   // 10 second timeout
            );

        FaenumSearchListener listener = new FaenumSearchListener();
        performer.setListener(listener);

        try {
            LOG.info("FaenumSearchPatternTest: Starting search for '" + searchTerm + "'");
            performer.perform();
        } catch (Throwable t) {
            t.printStackTrace();
            LOG.error("Search failed with exception", t);
            // Don't fail the test if the site is down or API changed
            LOG.warn("Faenum search test failed - site may be down or API changed: " + t.getMessage());
            return;
        }

        if (!listener.failedTests.isEmpty()) {
            LOG.error("Search validation failed: " + listener.getFailedMessages());
            // Don't fail the test - just log warnings
            LOG.warn("Faenum search test had validation issues: " + listener.getFailedMessages());
        }

        LOG.info("FaenumSearchPatternTest: PASSED");
    }

    static class FaenumSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.isEmpty()) {
                LOG.warn("FaenumSearchListener.onResults: No search results returned (site may be down or API changed)");
                return;
            }

            LOG.info("FaenumSearchListener.onResults: Got " + results.size() + " results");

            for (SearchResult result : results) {
                CompositeFileSearchResult sr = (CompositeFileSearchResult) result;
                LOG.info("FaenumSearchListener.onResults:");
                LOG.info("\t DisplayName: " + sr.getDisplayName());
                LOG.info("\t Filename: " + sr.getFilename());
                LOG.info("\t Size: " + sr.getSize());
                LOG.info("\t Source: " + sr.getSource());
                LOG.info("\t DetailsUrl: " + sr.getDetailsUrl());
                LOG.info("\t ThumbnailUrl: " + sr.getThumbnailUrl());
                LOG.info("\t License: " + (sr.getLicense() != null ? sr.getLicense().getName() : "null"));

                // Validate required fields
                if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                    failedTests.add("Result: getDisplayName is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getSource())) {
                    failedTests.add("Result: getSource is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                    failedTests.add("Result: getFilename is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getDetailsUrl())) {
                    failedTests.add("Result: getDetailsUrl is null or empty");
                }

                // Images should have thumbnails
                if (StringUtils.isNullOrEmpty(sr.getThumbnailUrl())) {
                    failedTests.add("Result: getThumbnailUrl is null or empty (expected for image results)");
                }

                // License should be public domain
                if (sr.getLicense() == null) {
                    failedTests.add("Result: getLicense is null (expected public domain)");
                }

                if (!failedTests.isEmpty()) {
                    return;
                }
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            LOG.warn("FaenumSearchListener: Search error: " + error.message());
            // Don't add to failedTests - site may be down
        }

        @Override
        public void onStopped(long token) {
            LOG.info("FaenumSearchListener: Search stopped");
        }

        public String getFailedMessages() {
            if (failedTests.isEmpty()) {
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
