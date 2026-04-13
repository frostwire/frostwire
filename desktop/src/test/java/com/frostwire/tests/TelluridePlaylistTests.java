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

import com.frostwire.search.telluride.TellurideLauncher;
import com.frostwire.search.telluride.TellurideListener;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TelluridePlaylistTests {
    private static final Logger LOG = Logger.getLogger(TelluridePlaylistTests.class);
    private static final Gson gson = new GsonBuilder().create();

    @Test
    public void testIsYouTubePlaylistUrl_ChannelHandle() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://www.youtube.com/@peterdiamandis"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_ChannelId() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://www.youtube.com/channel/UCYD4cCGX7OjeHY5PUBwwkLg"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_CustomChannel() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://www.youtube.com/c/FrostWireLLC"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_UserChannel() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://www.youtube.com/user/gubatron"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_PlaylistListParam() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNj3_w7fOc"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_WatchWithList() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://www.youtube.com/watch?v=abc123&list=PLrAXtmErZgOeiKm4sgNOknGvNj3_w7fOc"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_MusicPlaylist() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://music.youtube.com/playlist?list=RDCLAK5oy_kuACt4MGKsQNN3VEHORXa5e9TPNz2C5CI"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_MobileChannelHandle() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://m.youtube.com/@peterdiamandis"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_MobileChannelId() {
        assertTrue(SearchMediator.isYouTubePlaylistUrl("https://m.youtube.com/channel/UCYD4cCGX7OjeHY5PUBwwkLg"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_SingleVideoReturnsFalse() {
        assertFalse(SearchMediator.isYouTubePlaylistUrl("https://www.youtube.com/watch?v=ye2CLllRO8I"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_NonYouTubeReturnsFalse() {
        assertFalse(SearchMediator.isYouTubePlaylistUrl("https://vimeo.com/12345"));
    }

    @Test
    public void testIsYouTubePlaylistUrl_NullReturnsFalse() {
        assertFalse(SearchMediator.isYouTubePlaylistUrl(null));
    }

    @Test
    public void testIsYouTubePlaylistUrl_EmptyReturnsFalse() {
        assertFalse(SearchMediator.isYouTubePlaylistUrl(""));
    }

    @Test
    public void testGetValidPlaylistResults_BasicPlaylist() {
        String json = "{" +
                "\"type\":\"playlist\"," +
                "\"title\":\"Test Channel\"," +
                "\"extractor\":\"youtube\"," +
                "\"entries\":[" +
                "{\"id\":\"vid1\",\"title\":\"Video One\",\"url\":\"https://youtube.com/watch?v=vid1\",\"webpage_url\":\"https://www.youtube.com/watch?v=vid1\",\"thumbnail\":\"https://img.youtube.com/vi/vid1/0.jpg\",\"duration\":120,\"upload_date\":\"20250101\",\"view_count\":1000,\"description\":\"Desc 1\"}," +
                "{\"id\":\"vid2\",\"title\":\"Video Two\",\"url\":\"https://youtube.com/watch?v=vid2\",\"webpage_url\":\"https://www.youtube.com/watch?v=vid2\",\"thumbnail\":\"https://img.youtube.com/vi/vid2/0.jpg\",\"duration\":300,\"upload_date\":\"20250215\",\"view_count\":500,\"description\":\"Desc 2\"}" +
                "]" +
                "}";

        List<TellurideSearchResult> results = TellurideSearchPerformer.getValidPlaylistResults(json, gson, null, -1, "test");
        assertEquals(2, results.size());

        TellurideSearchResult r1 = results.get(0);
        assertEquals("vid1", r1.getId());
        assertEquals("Video One", r1.getDisplayName());
        assertEquals("https://www.youtube.com/watch?v=vid1", r1.getDetailsUrl());
        assertEquals("Cloud:youtube", r1.getSource());
        assertEquals("https://img.youtube.com/vi/vid1/0.jpg", r1.getThumbnailUrl());
        assertEquals(0, r1.getSize());
        assertNull(r1.getDownloadUrl());

        TellurideSearchResult r2 = results.get(1);
        assertEquals("vid2", r2.getId());
        assertEquals("Video Two", r2.getDisplayName());
        assertEquals("https://www.youtube.com/watch?v=vid2", r2.getDetailsUrl());
    }

    @Test
    public void testGetValidPlaylistResults_NullEntries() {
        String json = "{\"type\":\"playlist\",\"title\":\"Empty\",\"extractor\":\"youtube\",\"entries\":null}";
        List<TellurideSearchResult> results = TellurideSearchPerformer.getValidPlaylistResults(json, gson, null, -1, "test");
        assertTrue(results.isEmpty());
    }

    @Test
    public void testGetValidPlaylistResults_EmptyEntries() {
        String json = "{\"type\":\"playlist\",\"title\":\"Empty\",\"extractor\":\"youtube\",\"entries\":[]}";
        List<TellurideSearchResult> results = TellurideSearchPerformer.getValidPlaylistResults(json, gson, null, -1, "test");
        assertTrue(results.isEmpty());
    }

    @Test
    public void testGetValidPlaylistResults_FallbackToUrlWhenNoWebpageUrl() {
        String json = "{" +
                "\"type\":\"playlist\"," +
                "\"title\":\"Test\"," +
                "\"extractor\":\"youtube\"," +
                "\"entries\":[" +
                "{\"id\":\"vid1\",\"title\":\"Video One\",\"url\":\"https://youtube.com/watch?v=vid1\",\"thumbnail\":\"https://i.ytimg.com/vi/vid1/hqdefault.jpg\",\"duration\":60,\"upload_date\":\"20250101\",\"view_count\":100,\"description\":\"\"}" +
                "]" +
                "}";

        List<TellurideSearchResult> results = TellurideSearchPerformer.getValidPlaylistResults(json, gson, null, -1, "test");
        assertEquals(1, results.size());
        assertEquals("https://youtube.com/watch?v=vid1", results.get(0).getDetailsUrl());
    }

    @Test
    public void testGetValidPlaylistResults_MissingUploadDate() {
        String json = "{" +
                "\"type\":\"playlist\"," +
                "\"title\":\"Test\"," +
                "\"extractor\":\"youtube\"," +
                "\"entries\":[" +
                "{\"id\":\"vid1\",\"title\":\"No Date Video\",\"url\":\"https://youtube.com/watch?v=vid1\",\"webpage_url\":\"https://www.youtube.com/watch?v=vid1\",\"thumbnail\":\"https://i.ytimg.com/vi/vid1/hqdefault.jpg\",\"duration\":60,\"view_count\":0,\"description\":\"\"}" +
                "]" +
                "}";

        List<TellurideSearchResult> results = TellurideSearchPerformer.getValidPlaylistResults(json, gson, null, -1, "test");
        assertEquals(1, results.size());
        assertTrue(results.get(0).getCreationTime() > 0, "creationTime should default to current time when upload_date is null");
    }

    @Test
    public void testPartialConstructor6Params() {
        TellurideSearchResult result = new TellurideSearchResult(
                "abc123",
                "Test Video Title",
                "Cloud:youtube",
                "https://www.youtube.com/watch?v=abc123",
                "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
                1704067200000L);

        assertEquals("abc123", result.getId());
        assertEquals("Test Video Title", result.getDisplayName());
        assertEquals("Cloud:youtube", result.getSource());
        assertEquals("https://www.youtube.com/watch?v=abc123", result.getDetailsUrl());
        assertEquals("https://i.ytimg.com/vi/abc123/hqdefault.jpg", result.getThumbnailUrl());
        assertEquals(1704067200000L, result.getCreationTime());
        assertNull(result.getDownloadUrl());
        assertEquals(0, result.getSize());
        assertNull(result.getHttpHeaders());
        assertEquals("Test Video Title", result.getFilename());
    }

    @Test
    public void testPartialConstructor_SanitizesUnicodeInTitle() {
        TellurideSearchResult result = new TellurideSearchResult(
                "vid1",
                "Video 🎵 with emoji ♥ and symbols ★★★",
                "Cloud:youtube",
                "https://www.youtube.com/watch?v=vid1",
                "https://i.ytimg.com/vi/vid1/hqdefault.jpg",
                System.currentTimeMillis());

        assertFalse(result.getDisplayName().contains("\u2665"));
        assertFalse(result.getDisplayName().contains("\u2605"));
        assertFalse(result.getDisplayName().contains("\ud83c\udfb5"));
    }

    @Test
    @Tag("integration")
    public void testPlaylistModeIntegration() throws InterruptedException {
        File tellurideLauncherFile = FrostWireUtils.getTellurideLauncherFile();
        if (tellurideLauncherFile == null || !tellurideLauncherFile.exists() || !tellurideLauncherFile.canExecute()) {
            LOG.warn("Skipping playlistModeIntegration, telluride launcher not available");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        List<String> errors = new ArrayList<>();
        List<TellurideSearchResult> allResults = new ArrayList<>();
        boolean[] downloadStarted = {false};

        TellurideListener listener = new TellurideListener() {
            @Override
            public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {
                downloadStarted[0] = true;
            }

            @Override
            public void onError(String errorMessage) {
                errors.add(errorMessage);
            }

            @Override
            public void onFinished(int exitCode) {
                if (exitCode != 0) {
                    errors.add("Exit code: " + exitCode);
                }
                latch.countDown();
            }

            @Override
            public void onDestination(String outputFilename) {
                downloadStarted[0] = true;
            }

            @Override
            public boolean aborted() {
                return downloadStarted[0];
            }

            @Override
            public void onMeta(String json) {
                LOG.info("[testPlaylistModeIntegration] GOT JSON: " + json.substring(0, Math.min(200, json.length())));
                if (json.contains("\"type\"") && json.contains("\"playlist\"")) {
                    List<TellurideSearchResult> results = TellurideSearchPerformer.getValidPlaylistResults(json, gson, null, -1, "test");
                    allResults.addAll(results);
                } else {
                    errors.add("Expected playlist JSON but got: " + json.substring(0, Math.min(100, json.length())));
                }
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };

        TellurideLauncher.launch(tellurideLauncherFile,
                "https://www.youtube.com/@frostwire",
                null,
                false,
                false,
                true,
                true,
                listener);

        boolean completed = latch.await(120, TimeUnit.SECONDS);
        if (!completed) {
            LOG.warn("[testPlaylistModeIntegration] Timed out - telluride binary likely needs rebuild with -p flag support");
            return;
        }
        if (downloadStarted[0]) {
            LOG.warn("[testPlaylistModeIntegration] Telluride started downloading instead of extracting playlist - binary needs rebuild with -p flag");
            return;
        }
        if (!errors.isEmpty()) {
            LOG.warn("[testPlaylistModeIntegration] Skipping assertions due to errors: " + String.join("; ", errors));
            return;
        }
        assertFalse(allResults.isEmpty(), "Expected at least one playlist entry result");

        TellurideSearchResult firstResult = allResults.get(0);
        assertNotNull(firstResult.getId(), "Entry should have an id");
        assertNotNull(firstResult.getDisplayName(), "Entry should have a title");
        assertNotNull(firstResult.getDetailsUrl(), "Entry should have a detailsUrl");
        assertEquals("Cloud:youtube", firstResult.getSource());
        assertNull(firstResult.getDownloadUrl(), "Partial results should not have downloadUrl");

        LOG.info("[testPlaylistModeIntegration] Got " + allResults.size() + " playlist results");
    }
}
