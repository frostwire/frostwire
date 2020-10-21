/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Himanshu Sharma (HimanshuSharma789)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
 *
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
import com.frostwire.search.idope.IdopeSearchPerformer;
import com.frostwire.search.idope.IdopeSearchResult;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IdopeSearchPerformerTest {
    @Test
    public void iDopeTest() {
        System.out.println("IdopeSearchPerformerTests::iDopeTest() invoked");
        String TEST_SEARCH_TERM = UrlUtils.encode("foo");
        IdopeSearchPerformer idope = new IdopeSearchPerformer(1, TEST_SEARCH_TERM, 5000);

        // We need this because assertX failing inside a callback does not make THIS test fail
        // This callback object will keep track of what failed or not and then we'll ask it if it failed.
        IdopeSearchListener searchListener = new IdopeSearchListener();
        idope.setListener(searchListener);
        try {
            idope.perform();
            assertEquals(searchListener.failedTests.size(), 0, searchListener.getFailedMessages());
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Aborting test.");
            fail(t.getMessage());
        }
        if (searchListener.failedTests.size() > 0) {
            fail(searchListener.getFailedMessages());
        }
    }

    private static class IdopeSearchListener implements SearchListener {
        List<String> failedTests = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.size() == 0) {
                fail("IdopeSearchPerformerTest: no search results");
                return;
            }
            for (SearchResult result : results) {
                IdopeSearchResult sr = (IdopeSearchResult) result;
                System.out.println("IdopeSearchPerformer.SearchListener.onResults:");
                System.out.println("\t DisplayName: " + sr.getDisplayName());
                if (StringUtils.isNullOrEmpty(sr.getDisplayName())) {
                    failedTests.add("DisplayName was null or empty");
                }
                System.out.println("\t Source: " + sr.getSource());
                if (StringUtils.isNullOrEmpty(sr.getSource())) {
                    failedTests.add("Source was null or empty");
                }
                System.out.println("\t DetailsUrl: " + sr.getDetailsUrl());
                if (StringUtils.isNullOrEmpty(sr.getDetailsUrl())) {
                    failedTests.add("DetailsUrl was null or empty");
                }

                System.out.println("\t Filename: " + sr.getFilename());
                if (StringUtils.isNullOrEmpty(sr.getFilename())) {
                    failedTests.add("Filename was null or empty");
                }
                System.out.println("\t Hash: " + sr.getHash());
                if (StringUtils.isNullOrEmpty(sr.getHash())) {
                    failedTests.add("Hash was null or empty");
                }
                System.out.println("\t TorrentUrl: " + sr.getTorrentUrl());
                if (StringUtils.isNullOrEmpty(sr.getTorrentUrl())) {
                    failedTests.add("TorrentUrl was null or empty");
                }
                System.out.println("\t Seeds: " + sr.getSeeds());
                System.out.println("\t Size: " + sr.getSize());

                if (failedTests.size() > 0) {
                    return;
                }
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            failedTests.add("IdopeSearchPerformerTest: " + error.message());
        }

        @Override
        public void onStopped(long token) {
        }

        public String getFailedMessages() {
            if (failedTests.size() == 0) {
                return "";
            }
            StringBuffer buffer = new StringBuffer();
            for (String errorMessage : failedTests) {
                buffer.append(errorMessage);
                buffer.append("\n");
            }
            return buffer.toString();
        }
    }
}