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
import com.frostwire.search.nyaa.NyaaSearchPerformer;
import com.frostwire.search.nyaa.NyaaSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;
import org.limewire.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

public class NyaaSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(NyaaSearchPerformerTest.class);
    @Test
    public void nyaaSearchPerformerTest() {
        String TEST_SEARCH_TERM = UrlUtils.encode("free");
        NyaaSearchPerformer nyaa = new NyaaSearchPerformer("nyaa.si", 1, TEST_SEARCH_TERM, 5000);
        NyaaSearchListener listener = new NyaaSearchListener();
        nyaa.setListener(listener);
        try {
            nyaa.perform();
        } catch (Throwable t) {
            t.printStackTrace();
            if (!nyaa.isDDOSProtectionActive()) {
                LOG.info("Aborting test.");
                fail(t.getMessage());
            }
            return;
        }
        if (listener.failedTests.size() > 0 && !nyaa.isDDOSProtectionActive()) {
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
                NyaaSearchResult sr = (NyaaSearchResult) result;
                LOG.info("NyaaSearchPerformer.SearchListener.onResults:");
                LOG.info("\t Hash: " + sr.getHash());
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
                if (StringUtils.isNullOrEmpty(sr.getHash())) {
                    failedTests.add("getHash() null or empty");
                }
                if (StringUtils.isNullOrEmpty(sr.getTorrentUrl())) {
                    failedTests.add("getTorrentUrl() null or empty");
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
            StringBuffer buffer = new StringBuffer();
            for (String msg : failedTests) {
                buffer.append(msg);
                buffer.append("\n");
            }
            return buffer.toString();
        }
    }
}
