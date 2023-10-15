/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
