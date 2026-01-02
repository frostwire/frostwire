/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import com.frostwire.desktop.DesktopPlatform;
import com.frostwire.gui.updates.SoundCloudConfigFetcher;
import com.frostwire.platform.Platforms;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.soundcloud.SoundcloudSearchPattern;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

public class SoundcloudSearchPatternTest {
    private final static Logger LOG = Logger.getLogger(SoundcloudSearchPatternTest.class);

    @Test
    public void testSoundcloudSearchPattern() {
        Platforms.set(new DesktopPlatform());

        // Fetch remote SoundCloud configuration
        LOG.info("Fetching remote SoundCloud configuration...");
        SoundCloudConfigFetcher.fetchAndUpdateConfig();
        String clientId = SoundCloudConfigFetcher.getClientId();
        String appVersion = SoundCloudConfigFetcher.getAppVersion();
        LOG.info("Remote SoundCloud credentials fetched - ClientID: " + clientId + ", AppVersion: " + appVersion);

        // V2: Using new flat architecture via SearchPerformerFactory
        String keywords = "free download";
        String encodedKeywords = UrlUtils.encode(keywords);

        SoundcloudSearchListener searchListener = new SoundcloudSearchListener();

        ISearchPerformer performer = SearchPerformerFactory.createSearchPerformer(
                1,
                keywords,
                new SoundcloudSearchPattern(clientId, appVersion),
                null,  // No crawling for Soundcloud
                5000
        );
        performer.setListener(searchListener);
        try {
            LOG.info("Starting V2 Soundcloud search...");
            performer.perform();
        } catch (Throwable t) {
            t.printStackTrace();
            LOG.error("Aborting test: " + t.getMessage());
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
                LOG.info("SoundcloudSearchPatternTest.onResults:");
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
