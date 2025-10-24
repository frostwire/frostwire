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
import com.frostwire.search.magnetdl.MagnetDLSearchPerformer;
import com.frostwire.search.magnetdl.MagnetDLSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;
import org.limewire.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author gubatron
 */
public class MagnetDLTest {

    private static final Logger LOG = Logger.getLogger(MagnetDLTest.class);

    @Test
    public void main() {
        String TEST_SEARCH_TERM = UrlUtils.encode("creative commons");
        MagnetDLSearchPerformer magnetDLSearchPerformer = new MagnetDLSearchPerformer(1, TEST_SEARCH_TERM, 5000);
        magnetDLSearchPerformer.setMinSeeds(0); // just for testing
        MagnetDLSearchListener magnetDLSearchListener = new MagnetDLSearchListener();
        magnetDLSearchPerformer.setListener(magnetDLSearchListener);
        try {
            magnetDLSearchPerformer.perform();
        } catch (Throwable t) {
            t.printStackTrace();
            LOG.info("Aborting test.");
            fail(t.getMessage());
            return;
        }
        if (magnetDLSearchListener.failedTests.size() > 0) {
            fail(magnetDLSearchListener.getFailedTestsMessages());
        }
        LOG.info("-done-");
    }

    static class MagnetDLSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();
        int totalResults = 0;

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results != null) {
                totalResults = results.size();
            }
            for (SearchResult result : results) {
                MagnetDLSearchResult sr = (MagnetDLSearchResult) result;
                LOG.info("MagnetDLSearchResult.SearchListener.onResults:");
                LOG.info("\t Size: " + sr.getSize());

                if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                    failedTests.add("getDisplayName was null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getSource())) {
                    failedTests.add("getSource was null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getDetailsUrl())) {
                    failedTests.add("getDetailsUrl was null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                    failedTests.add("getFilename was null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getHash())) {
                    failedTests.add("getHash was null or empty");
                } else {
                    LOG.info("\t Hash: " + sr.getHash());
                }

                if (StringUtils.isNullOrEmpty(sr.getTorrentUrl())) {
                    failedTests.add("getHash was null or empty");
                }

                if (failedTests.size() > 0 && results.size() < 10) {
                    return;
                }
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            if (totalResults == 0) {
                failedTests.add(error.message());
            }
        }

        @Override
        public void onStopped(long token) {
        }

        String getFailedTestsMessages() {
            if (failedTests.size() == 0) {
                return "";
            }
            StringBuilder buffer = new StringBuilder();
            for (String msg : failedTests) {
                buffer.append(msg).append("\n");
            }
            return buffer.toString();
        }
    }
}
