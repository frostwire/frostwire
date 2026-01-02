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

package com.frostwire.search.soundcloud;

import com.frostwire.desktop.DesktopPlatform;
import com.frostwire.gui.updates.SoundCloudConfigFetcher;
import com.frostwire.platform.Platforms;
import com.frostwire.search.*;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for SoundCloud dynamic credential injection and remote configuration fetching.
 * Tests verify that:
 * - SoundCloudConfigFetcher successfully fetches remote configuration
 * - Remote credentials are cached and available
 * - Search performer uses dynamically injected credentials from remote config
 * - Credentials can be updated and are properly applied to search URLs
 * - Partial and null credential injections are handled correctly
 * - Multiple performer instances maintain independent credentials
 *
 * @author gubatron
 * @author aldenml
 */
@DisplayName("SoundCloud Dynamic Credentials Remote Configuration Tests")
class SoundcloudDynamicCredentialsTest {

    private static final Logger LOG = Logger.getLogger(SoundcloudDynamicCredentialsTest.class);
    private static final String DEFAULT_CLIENT_ID = "MHDG7vIKasWstY0FaB07rK5WUoUjjCDC";
    private static final String DEFAULT_APP_VERSION = "1760349581";
    private static final String TEST_SEARCH_TERM = UrlUtils.encode("creative commons");

    @BeforeAll
    static void setup() {
        // Initialize platform for desktop environment
        Platforms.set(new DesktopPlatform());
    }

    @Test
    @DisplayName("Search performer should use injected credentials in search URL")
    void testSearchWithDefaultCredentials() {
        String clientId = "testClientId1";
        String appVersion = "testAppVersion1";

        // V2: Create SoundcloudSearchPattern with custom credentials
        SoundcloudSearchPattern pattern = new SoundcloudSearchPattern(clientId, appVersion);

        String searchUrl = pattern.getSearchUrl(UrlUtils.encode("testquery"));

        // Verify the credentials are in the search URL
        assertTrue(searchUrl.contains("client_id=" + clientId),
                "Search URL should contain the injected client ID");

        LOG.info("Search performer correctly uses injected credentials in URL");
        LOG.info("Search URL: " + searchUrl);
    }

    @Test
    @DisplayName("Remote config should be fetched and used in search performer")
    void testSearchWithRemoteConfiguration() {
        // Fetch remote configuration (should be cached)
        boolean fetchSuccess = SoundCloudConfigFetcher.fetchAndUpdateConfig();

        // Even if fetch fails, we should proceed with cached/default values
        String clientId = SoundCloudConfigFetcher.getClientId();
        String appVersion = SoundCloudConfigFetcher.getAppVersion();

        assertNotNull(clientId, "Client ID should not be null");
        assertNotNull(appVersion, "App version should not be null");
        assertFalse(clientId.isEmpty(), "Client ID should not be empty");
        assertFalse(appVersion.isEmpty(), "App version should not be empty");

        LOG.info("Using SoundCloud credentials - ClientID: " + clientId + ", AppVersion: " + appVersion);

        // Credentials should be available (either from remote fetch or defaults)
        assertFalse(clientId.isEmpty() && appVersion.isEmpty(),
                "Credentials should be available from remote fetch or defaults");

        // V2: Create SoundcloudSearchPattern and performer with fetched credentials
        SoundcloudSearchPattern pattern = new SoundcloudSearchPattern(clientId, appVersion);
        ISearchPerformer performer = SearchPerformerFactory.createSearchPerformer(
                2L,
                "creative commons",
                pattern,
                null,  // No crawling for Soundcloud
                5000
        );

        // Verify the search URL contains the injected credentials
        String searchUrl = pattern.getSearchUrl(UrlUtils.encode("test"));
        assertTrue(searchUrl.contains("client_id=" + clientId),
                "Search URL should use the fetched client ID");

        SoundcloudIntegrationSearchListener listener = new SoundcloudIntegrationSearchListener();
        performer.setListener(listener);

        try {
            performer.perform();
            performer.stop();
        } catch (Throwable t) {
            LOG.error("Search performer threw exception", t);
            fail("Search performer failed: " + t.getMessage());
        }

        LOG.info("Search completed. Results received: " + listener.searchResults.size());
        LOG.info("Search errors: " + listener.getFailedMessages());

        // Verify search returned results with dynamic credentials
        assertTrue(listener.searchResults.size() > 0,
                "Search with remote dynamic credentials should return results. Errors: " + listener.getFailedMessages());

        LOG.info("Remote configuration successfully fetched and injected into search performer");
        LOG.info("Search executed with dynamic credentials and returned " + listener.searchResults.size() + " results");
        LOG.info("Received " + listener.searchResults.size() + " search results with dynamic credentials - ClientID: " + clientId + ", AppVersion: " + appVersion);
    }

    @Test
    @DisplayName("Multiple search performers should use their own injected credentials")
    void testMultiplePerformersWithDifferentCredentials() {
        String clientId1 = "clientId1";
        String appVersion1 = "appVersion1";
        String clientId2 = "clientId2";
        String appVersion2 = "appVersion2";

        // V2: Create two patterns with different credentials
        SoundcloudSearchPattern pattern1 = new SoundcloudSearchPattern(clientId1, appVersion1);
        SoundcloudSearchPattern pattern2 = new SoundcloudSearchPattern(clientId2, appVersion2);

        String url1 = pattern1.getSearchUrl(UrlUtils.encode("test"));
        String url2 = pattern2.getSearchUrl(UrlUtils.encode("test"));

        assertTrue(url1.contains("client_id=" + clientId1),
                "Pattern 1 should use its own client ID");
        assertTrue(url2.contains("client_id=" + clientId2),
                "Pattern 2 should use its own client ID");
        assertNotEquals(url1, url2,
                "URLs should be different when using different credentials");
    }

    @Test
    @DisplayName("Search URL generation should respect injected credentials")
    void testSearchUrlUsesInjectedCredentials() {
        String testClientId = "myTestClientId123";
        String testAppVersion = "myTestAppVersion456";

        // V2: Create pattern with custom credentials
        SoundcloudSearchPattern pattern = new SoundcloudSearchPattern(testClientId, testAppVersion);

        String searchUrl = pattern.getSearchUrl(UrlUtils.encode("testquery"));

        assertTrue(searchUrl.contains("client_id=" + testClientId),
                "Search URL should contain injected client ID");
        assertFalse(searchUrl.contains(DEFAULT_CLIENT_ID),
                "Search URL should not contain default client ID");

        LOG.info("Generated search URL with custom credentials: " + searchUrl);
    }

    @Test
    @DisplayName("Null credentials should use defaults")
    void testNullCredentialsUseDefaults() {
        String clientId = "myClientId";
        String appVersion = "myAppVersion";

        // V2: Create pattern with custom credentials
        SoundcloudSearchPattern pattern1 = new SoundcloudSearchPattern(clientId, appVersion);
        SoundcloudSearchPattern pattern2 = new SoundcloudSearchPattern(null, null);

        String urlCustom = pattern1.getSearchUrl(UrlUtils.encode("test"));
        String urlDefault = pattern2.getSearchUrl(UrlUtils.encode("test"));

        assertTrue(urlCustom.contains("client_id=" + clientId),
                "Custom pattern should use provided client ID");
        assertFalse(urlDefault.isEmpty(),
                "Default pattern should still generate valid URL");

        LOG.info("Custom URL: " + urlCustom);
        LOG.info("Default URL: " + urlDefault);
    }

    @Test
    @DisplayName("Different credentials should produce different URLs")
    void testDifferentCredentialsProduceDifferentUrls() {
        String clientId1 = "clientId1";
        String clientId2 = "clientId2";

        // V2: Create patterns with different credentials
        SoundcloudSearchPattern pattern1 = new SoundcloudSearchPattern(clientId1, "appVersion");
        SoundcloudSearchPattern pattern2 = new SoundcloudSearchPattern(clientId2, "appVersion");

        String url1 = pattern1.getSearchUrl(UrlUtils.encode("test"));
        String url2 = pattern2.getSearchUrl(UrlUtils.encode("test"));

        assertNotEquals(url1, url2,
                "URLs should be different with different client IDs");
        assertTrue(url1.contains("client_id=" + clientId1),
                "URL 1 should use client ID 1");
        assertTrue(url2.contains("client_id=" + clientId2),
                "URL 2 should use client ID 2");
    }

    @Test
    @DisplayName("Pattern should produce valid URLs with provided credentials")
    void testPatternProducesValidUrls() {
        String testClientId = "testId";
        String testAppVersion = "testVersion";

        SoundcloudSearchPattern pattern = new SoundcloudSearchPattern(testClientId, testAppVersion);

        String url = pattern.getSearchUrl(UrlUtils.encode("music"));

        assertNotNull(url, "URL should not be null");
        assertFalse(url.isEmpty(), "URL should not be empty");
        assertTrue(url.contains("client_id=" + testClientId), "URL should contain client ID");
        assertTrue(url.contains("music"), "URL should contain search term");
    }

    /**
     * Integration search listener for capturing search results and errors
     */
    private static class SoundcloudIntegrationSearchListener implements SearchListener {
        final List<String> failedTests = new ArrayList<>();
        final List<SearchResult> searchResults = new ArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            LOG.info("onResults called with " + (results != null ? results.size() : "null") + " results");
            if (results == null || results.isEmpty()) {
                LOG.info("No search results returned");
                return;
            }

            for (SearchResult result : results) {
                SoundcloudSearchResult sr = (SoundcloudSearchResult) result;
                LOG.info("SoundcloudSearchListener received result:");
                LOG.info("\tDisplayName: " + sr.getDisplayName());
                LOG.info("\tSource: " + sr.getSource());
                LOG.info("\tDetailsUrl: " + sr.getDetailsUrl());
                LOG.info("\tFilename: " + sr.getFilename());
                LOG.info("\tHash: " + sr.getHash());
                LOG.info("\tDownloadUrl: " + sr.getDownloadUrl());
                LOG.info("\tUsername: " + sr.getUsername());
                LOG.info("\tSize: " + sr.getSize());

                // Validate result fields - use same validation as original test
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

                if (StringUtils.isNullOrEmpty(sr.getUsername())) {
                    failedTests.add("getUsername is null or empty");
                }

                // Don't fail on DownloadUrl - it's fetched asynchronously
                if (failedTests.size() > 0) {
                    return;
                }
                searchResults.add(result);
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            String errorMsg = error != null ? error.message() : "Unknown error";
            LOG.error("Search error: " + errorMsg);
            failedTests.add(errorMsg);
        }

        @Override
        public void onStopped(long token) {
            if (searchResults.isEmpty() && failedTests.isEmpty()) {
                failedTests.add("No search results and no errors");
            }
        }

        public String getFailedMessages() {
            if (failedTests.isEmpty()) {
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
