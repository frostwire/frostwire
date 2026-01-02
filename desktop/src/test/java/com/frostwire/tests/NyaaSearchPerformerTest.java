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
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.nyaa.NyaaSearchPattern;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * V2 Architecture: Uses NyaaSearchPattern with SearchPerformerFactory
 */
public class NyaaSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(NyaaSearchPerformerTest.class);
    @Test
    public void nyaaSearchPerformerTest() {
        String TEST_SEARCH_TERM = "free";

        // V2: Use SearchPerformerFactory with NyaaSearchPattern
        ISearchPerformer performer = SearchPerformerFactory.createSearchPerformer(
                1,
                TEST_SEARCH_TERM,
                new NyaaSearchPattern(),
                null,  // No crawling for Nyaa
                5000
        );

        NyaaSearchListener listener = new NyaaSearchListener();
        performer.setListener(listener);
        try {
            LOG.info("Starting Nyaa search for: " + TEST_SEARCH_TERM);
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

    static class NyaaSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.size() == 0) {
                failedTests.add("no search results");
                return;
            }
            for (SearchResult result : results) {
                CompositeFileSearchResult sr = (CompositeFileSearchResult) result;
                LOG.info("NyaaSearchListener.onResults:");
                LOG.info("\t DisplayName: " + sr.getDisplayName());
                LOG.info("\t Hash: " + (sr.getTorrentHash().isPresent() ? sr.getTorrentHash().get() : "NONE"));
                LOG.info("\t Size: " + sr.getSize());

                if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                    failedTests.add("getDisplayName() null or empty");
                }
                if (StringUtils.isNullOrEmpty(sr.getSource())) {
                    failedTests.add("getSource() null or empty");
                }
                if (StringUtils.isNullOrEmpty(sr.getDetailsUrl())) {
                    failedTests.add("getDetailsUrl() null or empty");
                }
                if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                    failedTests.add("getFilename() null or empty");
                }
                if (!sr.getTorrentHash().isPresent()) {
                    failedTests.add("getTorrentHash() not present");
                }
                if (!sr.getTorrentUrl().isPresent()) {
                    failedTests.add("getTorrentUrl() not present");
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

        String getFailedMessages() {
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
