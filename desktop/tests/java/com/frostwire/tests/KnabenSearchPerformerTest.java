/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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
import com.frostwire.search.knaben.KnabenSearchPerformer;
import com.frostwire.search.knaben.KnabenSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * gradle test --tests "com.frostwire.tests.KnabenSearchPerformerTest.knabenSearchTest"
 */
public class KnabenSearchPerformerTest {
    private final static Logger LOG = Logger.getLogger(KnabenSearchPerformerTest.class);
    
    @Test
    public void knabenSearchTest() {
        String TEST_SEARCH_TERM = UrlUtils.encode("ubuntu");
        KnabenSearchPerformer performer = new KnabenSearchPerformer(1, TEST_SEARCH_TERM, 10000);
        KnabenSearchListener listener = new KnabenSearchListener();
        performer.setListener(listener);
        try {
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

    static class KnabenSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.size() == 0) {
                failedTests.add("No search results");
                return;
            }
            for (SearchResult result : results) {
                KnabenSearchResult sr = (KnabenSearchResult) result;
                LOG.info("KnabenSearchPerformer.SearchListener.onResults:");
                LOG.info("\t Hash: " + sr.getHash());
                LOG.info("\t Size: " + sr.getSize());
                LOG.info("\t Display Name: " + sr.getDisplayName());
                LOG.info("\t Seeds: " + sr.getSeeds());
                LOG.info("\t Details URL: " + sr.getDetailsUrl());

                if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                    failedTests.add("getDisplayName is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getSource())) {
                    failedTests.add("getSource is null or empty");
                }

                // Knaben should have detail pages
                if (StringUtils.isNullOrEmpty(sr.getDetailsUrl())) {
                    failedTests.add("getDetailsUrl should not be null or empty for Knaben results");
                }

                if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                    failedTests.add("getFilename is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getHash())) {
                    failedTests.add("getHash is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getTorrentUrl())) {
                    failedTests.add("getTorrentUrl is null or empty");
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