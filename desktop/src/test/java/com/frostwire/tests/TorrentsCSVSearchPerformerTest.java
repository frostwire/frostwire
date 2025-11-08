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
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.torrentscsv.TorrentsCSVSearchPattern;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * V2 Architecture: Uses TorrentsCSVSearchPattern with SearchPerformerFactory
 * gradle test --tests "com.frostwire.tests.TorrentsCSVSearchPerformerTest.torrentsCSVSearchTest"
 */
public class TorrentsCSVSearchPerformerTest {
    private final static Logger LOG = Logger.getLogger(TorrentsCSVSearchPerformerTest.class);
    
    @Test
    public void torrentsCSVSearchTest() {
        String TEST_SEARCH_TERM = "ubuntu";

        // V2: Use SearchPerformerFactory with TorrentsCSVSearchPattern
        ISearchPerformer performer = SearchPerformerFactory.createSearchPerformer(
                1,
                TEST_SEARCH_TERM,
                new TorrentsCSVSearchPattern(),
                null,  // No crawling for TorrentsCSV
                10000
        );

        TorrentsCSVSearchListener listener = new TorrentsCSVSearchListener();
        performer.setListener(listener);
        try {
            LOG.info("Starting TorrentsCSV search for: " + TEST_SEARCH_TERM);
            performer.perform();
        } catch (Throwable t) {
            t.printStackTrace();
            LOG.info("Aborting test.");
            fail(t.getMessage());
            return;
        }
        if (listener.failedTests.size() > 0) {
            fail(listener.getFailedMessages());
        }
    }

    static class TorrentsCSVSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.size() == 0) {
                failedTests.add("No search results");
                return;
            }
            for (SearchResult result : results) {
                CompositeFileSearchResult sr = (CompositeFileSearchResult) result;
                LOG.info("TorrentsCSVSearchListener.onResults:");
                LOG.info("\t Hash: " + (sr.getTorrentHash().isPresent() ? sr.getTorrentHash().get() : "NONE"));
                LOG.info("\t Size: " + sr.getSize());
                LOG.info("\t Display Name: " + sr.getDisplayName());
                LOG.info("\t Seeds: " + (sr.getSeeds().isPresent() ? sr.getSeeds().get() : "0"));

                if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                    failedTests.add("getDisplayName is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getSource())) {
                    failedTests.add("getSource is null or empty");
                }

                // TorrentsCSV may or may not have detail pages
                if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                    failedTests.add("getFilename is null or empty");
                }

                if (!sr.getTorrentHash().isPresent()) {
                    failedTests.add("getTorrentHash is not present");
                }

                if (!sr.getTorrentUrl().isPresent()) {
                    failedTests.add("getTorrentUrl is not present");
                }

                if (failedTests.size() > 0) {
                    return;
                }
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            failedTests.add(error.message());
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