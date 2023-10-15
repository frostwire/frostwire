/*
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

import com.frostwire.desktop.DesktopPlatform;
import com.frostwire.platform.Platforms;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

public class SoundcloudSearchPerformerTest {
    private final static Logger LOG = Logger.getLogger(SoundcloudSearchPerformerTest.class);

    @Test
    public void testSoundcloudSearchPerformer() {
        Platforms.set(new DesktopPlatform());
        String TEST_SEARCH_TERM = UrlUtils.encode("free download");
        SoundcloudSearchPerformer searchPerformer = new SoundcloudSearchPerformer("api-v2.sndcdn.com", 1, TEST_SEARCH_TERM, 5000);
        SoundcloudSearchListener searchListener = new SoundcloudSearchListener();
        searchPerformer.setListener(searchListener);
        try {
            searchPerformer.perform();
            searchPerformer.stop();
        } catch (Throwable t) {
            t.printStackTrace();
            LOG.info("Aborting test.");
            fail(t.getMessage());
            return;
        }
        if (searchListener.failedTests.size() > 0) {
            fail(searchListener.getFailedMessages());
        }
    }

    private static class SoundcloudSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();
        final List<SearchResult> searchResults = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            if (results == null || results.size() == 0) {
                failedTests.add("No search results");
                return;
            }
            for (SearchResult result : results) {
                SoundcloudSearchResult sr = (SoundcloudSearchResult) result;
                LOG.info("SoundcloudSearchListener.SearchListener.onResults:");
                LOG.info("\t DisplayName: " + sr.getDisplayName());
                LOG.info("\t Source: " + sr.getSource());
                LOG.info("\t DetailsUrl: " + sr.getDetailsUrl());
                LOG.info("\t Filename: " + sr.getFilename());
                LOG.info("\t Hash: " + sr.getHash());
                LOG.info("\t DownloadUrl: " + sr.getDownloadUrl());
                LOG.info("\t Username: " + sr.getUsername());
                LOG.info("\t Size: " + sr.getSize());

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

                if (StringUtils.isNullOrEmpty(sr.getHash())) {
                    failedTests.add("getHash is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getDownloadUrl())) {
                    failedTests.add("getDownloadUrl is null or empty");
                }

                if (StringUtils.isNullOrEmpty(sr.getUsername())) {
                    failedTests.add("getUsername is null or empty");
                }

                if (failedTests.size() > 0) {
                    return;
                }
                searchResults.add(result);
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            failedTests.add(error.message());
        }

        @Override
        public void onStopped(long token) {
            if (searchResults.size() == 0) {
                failedTests.add("No search results");
            }
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
