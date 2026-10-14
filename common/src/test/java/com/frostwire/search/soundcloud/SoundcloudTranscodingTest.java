/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.soundcloud;

import com.frostwire.util.JsonUtils;
import com.frostwire.util.http.HttpClient;
import com.frostwire.search.FileSearchResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundcloudTranscodingTest {
    @Test
    void searchPatternRetainsDownloadableHlsAndRejectsRestrictedTracks() {
        SoundcloudItem allowed = fixture();
        SoundcloudItem restricted = fixture();
        restricted.downloadable = false;
        SoundcloudItem snippet = fixture();
        for (SoundcloudTranscodings transcoding : snippet.media.transcodings) {
            transcoding.snipped = true;
        }
        String response = "{\"collection\":" + JsonUtils.toJson(new SoundcloudItem[] {allowed, restricted, snippet}) + "}";
        List<FileSearchResult> results = new SoundcloudSearchPattern("client", "version").parseResults(response);
        assertEquals(1, results.size());
        SoundcloudSearchResult result = (SoundcloudSearchResult) results.get(0);
        assertTrue(result.isHls());
        assertEquals("sanitized-downloadable-track-soundcloud.m4a", result.getFilename());
        assertFalse(result.fetchedDownloadUrl());
    }

    @Test
    void liveSanitizedAacFixtureProducesM4aWithoutResolving() {
        SoundcloudItem item = fixture();
        assertTrue(item.isValidSearchResult());
        assertFalse(item.hasProgressiveFormat());
        assertNull(item.getProgressiveFormatJSONFetcherURL());
        assertEquals("sanitized-authorization", item.track_authorization);
        assertFalse(item.getSupportedTranscoding().snipped);
        SoundcloudSearchResult result = new SoundcloudSearchResult(item, "client", "version");
        assertTrue(result.isHls());
        assertEquals("sanitized-downloadable-track-soundcloud.m4a", result.getFilename());
        assertFalse(result.fetchedDownloadUrl());
    }

    @Test
    void progressiveWinsRegardlessOfOrderAndRemainsMp3() {
        SoundcloudItem item = fixture();
        SoundcloudTranscodings hls = item.getSupportedTranscoding();
        SoundcloudTranscodings progressive = transcoding("progressive", "audio/mpeg", "https://example.test/progressive");
        for (SoundcloudTranscodings[] entries : new SoundcloudTranscodings[][] {
                {hls, progressive}, {progressive, hls}}) {
            item.media.transcodings = entries;
            assertSame(progressive, item.getSupportedTranscoding());
            assertTrue(item.isValidSearchResult());
            assertTrue(item.hasProgressiveFormat());
            assertEquals(progressive.url, item.getProgressiveFormatJSONFetcherURL());
            SoundcloudSearchResult result = new SoundcloudSearchResult(item, "client", "version");
            assertFalse(result.isHls());
            assertEquals("sanitized-downloadable-track-soundcloud.mp3", result.getFilename());
        }
    }

    @Test
    void legacyProgressiveWithoutMimeTypeIsStillSupported() {
        SoundcloudItem item = fixture();
        SoundcloudTranscodings progressive = transcoding("progressive", null, "https://example.test/progressive");
        item.media.transcodings = new SoundcloudTranscodings[] {null, new SoundcloudTranscodings(), progressive};
        assertTrue(item.isValidSearchResult());
        assertSame(progressive, item.getSupportedTranscoding());
        assertEquals(progressive.url, item.getProgressiveFormatJSONFetcherURL());
    }

    @Test
    void downloadableGateAppliesToSearchPastedUrlsAndResolution() {
        SoundcloudItem item = fixture();
        for (String protocol : new String[] {"hls", "progressive"}) {
            item.media.transcodings = new SoundcloudTranscodings[] {
                    transcoding(protocol, "audio/mp4", "https://example.test/resolver")};
            item.downloadable = false;
            assertFalse(item.isValidSearchResult());
            assertFalse(item.isValidSearchResult(true));
            ResolverStub resolver = new ResolverStub();
            SoundcloudSearchResult result = result(item, resolver, new AtomicBoolean(false));
            assertNull(result.getDownloadUrl());
            assertEquals(0, resolver.calls);
            assertFalse(result.isHls());
        }
    }

    @Test
    void snippetsAreNeverSelected() {
        SoundcloudItem item = fixture();
        SoundcloudTranscodings hls = item.getSupportedTranscoding();
        SoundcloudTranscodings progressive = transcoding("progressive", "audio/mpeg", "https://example.test/progressive");
        progressive.snipped = true;
        item.media.transcodings = new SoundcloudTranscodings[] {progressive, hls};
        assertSame(hls, item.getSupportedTranscoding());
        assertFalse(item.hasProgressiveFormat());
        hls.snipped = true;
        assertNull(item.getSupportedTranscoding());
        assertFalse(item.isValidSearchResult(true));
    }

    @Test
    void nullAndMalformedMetadataFailClosed() {
        SoundcloudItem item = fixture();
        item.media = null;
        assertNoFormat(item);
        item.media = new SoundcloudMedia();
        assertNoFormat(item);
        item.media.transcodings = new SoundcloudTranscodings[0];
        assertNoFormat(item);
        item.media.transcodings = new SoundcloudTranscodings[] {null, new SoundcloudTranscodings()};
        assertNoFormat(item);
        for (String protocol : new String[] {null, "hls", "progressive", "dash"}) {
            for (String url : new String[] {null, "", "   "}) {
                item.media.transcodings = new SoundcloudTranscodings[] {transcoding(protocol, "audio/mp4", url)};
                assertNoFormat(item);
            }
        }
        for (String mime : new String[] {null, "", "audio/mp4-invalid", "audio/mpegurl", "audio/mpeg", "video/mp4", "application/vnd.apple.mpegurl"}) {
            item.media.transcodings = new SoundcloudTranscodings[] {transcoding("hls", mime, "https://example.test/resolver")};
            assertNoFormat(item);
        }
        item.media.transcodings = new SoundcloudTranscodings[] {transcoding(null, "audio/mp4", "https://example.test/resolver")};
        assertNoFormat(item);
    }

    @Test
    void resolverEncodesCredentialsAuthorizationAndPreservesExistingQuery() {
        SoundcloudItem item = fixture();
        item.getSupportedTranscoding().url = "https://example.test/resolver?existing=1";
        item.track_authorization = "fake token+/=&?";
        ResolverStub resolver = new ResolverStub();
        SoundcloudSearchResult result = new SoundcloudSearchResult(item, "client +&", "version/?", resolver.client(), () -> false);
        assertEquals(ResolverStub.RESOLVED_URL, result.getDownloadUrl());
        assertEquals("https://example.test/resolver?existing=1&client_id=client%20%2B%26&app_version=version%2F%3F&track_authorization=fake%20token%2B%2F%3D%26%3F", resolver.url);
        assertEquals(1, resolver.calls);
        assertTrue(result.isHls());
        assertTrue(result.fetchedDownloadUrl());
    }

    @Test
    void progressiveResolverOmitsMissingAuthorizationAndCachesForUiStreaming() {
        SoundcloudItem item = fixture();
        item.track_authorization = null;
        item.media.transcodings = new SoundcloudTranscodings[] {
                transcoding("progressive", "audio/mpeg", "https://example.test/progressive")};
        AtomicBoolean uiThread = new AtomicBoolean(false);
        ResolverStub resolver = new ResolverStub();
        SoundcloudSearchResult result = result(item, resolver, uiThread);
        assertFalse(result.fetchedDownloadUrl());
        assertEquals(ResolverStub.RESOLVED_URL, result.getStreamUrl());
        assertEquals("https://example.test/progressive?client_id=client&app_version=version", resolver.url);
        uiThread.set(true);
        assertEquals(ResolverStub.RESOLVED_URL, result.getDownloadUrl());
        assertEquals(ResolverStub.RESOLVED_URL, result.getStreamUrl());
        assertTrue(result.fetchedDownloadUrl());
        assertEquals(1, resolver.calls);
        assertFalse(result.isHls());
    }

    @Test
    void unresolvedUrlNeverMakesNetworkRequestOnUiThread() {
        ResolverStub resolver = new ResolverStub();
        SoundcloudSearchResult result = result(fixture(), resolver, new AtomicBoolean(true));
        assertThrows(RuntimeException.class, result::getDownloadUrl);
        assertThrows(RuntimeException.class, result::getStreamUrl);
        assertEquals(0, resolver.calls);
        assertFalse(result.fetchedDownloadUrl());
    }

    @Test
    void failedOrMalformedResolverResponsesAreNotCachedAndCanRetry() {
        ResolverStub resolver = new ResolverStub();
        SoundcloudSearchResult result = result(fixture(), resolver, new AtomicBoolean(false));
        resolver.fail = true;
        assertNull(result.getDownloadUrl());
        assertFalse(result.fetchedDownloadUrl());
        resolver.fail = false;
        for (String body : new String[] {null, "", "not json", "null", "{}", "{\"url\":null}", "{\"url\":\"   \"}"}) {
            resolver.body = body;
            assertNull(result.getDownloadUrl());
            assertFalse(result.fetchedDownloadUrl());
        }
        resolver.body = "{\"url\":\"" + ResolverStub.RESOLVED_URL + "\"}";
        assertEquals(ResolverStub.RESOLVED_URL, result.getDownloadUrl());
        assertTrue(result.fetchedDownloadUrl());
    }

    private static void assertNoFormat(SoundcloudItem item) {
        assertFalse(item.isValidSearchResult());
        assertFalse(item.hasProgressiveFormat());
        assertNull(item.getSupportedTranscoding());
        assertNull(item.getProgressiveFormatJSONFetcherURL());
    }

    private static SoundcloudSearchResult result(SoundcloudItem item, ResolverStub resolver, AtomicBoolean uiThread) {
        return new SoundcloudSearchResult(item, "client", "version", resolver.client(), uiThread::get);
    }

    private static SoundcloudTranscodings transcoding(String protocol, String mime, String url) {
        SoundcloudTranscodings transcoding = new SoundcloudTranscodings();
        transcoding.url = url;
        transcoding.format = new SoundcloudFormat();
        transcoding.format.protocol = protocol;
        transcoding.format.mime_type = mime;
        return transcoding;
    }

    private static SoundcloudItem fixture() {
        // Captured from q=free download on 2026-10-14; identity, URLs and JWT replaced.
        try (InputStream input = SoundcloudTranscodingTest.class.getResourceAsStream("downloadable-hls-track.json")) {
            assertNotNull(input);
            return JsonUtils.toObject(new String(input.readAllBytes(), StandardCharsets.UTF_8), SoundcloudItem.class);
        } catch (IOException e) {
            throw new AssertionError("Unable to read SoundCloud fixture", e);
        }
    }

    private static final class ResolverStub {
        static final String RESOLVED_URL = "https://example.test/audio/playlist.m3u8?signature=fake";
        String body = "{\"url\":\"" + RESOLVED_URL + "\"}";
        String url;
        int calls;
        boolean fail;

        HttpClient client() {
            return (HttpClient) Proxy.newProxyInstance(HttpClient.class.getClassLoader(), new Class<?>[] {HttpClient.class},
                    (proxy, method, args) -> {
                        if (!"get".equals(method.getName()) || args.length != 1) {
                            throw new AssertionError("Resolver must only GET its JSON endpoint, not audio");
                        }
                        calls++;
                        url = (String) args[0];
                        if (fail) {
                            throw new IOException("Simulated resolver failure");
                        }
                        return body;
                    });
        }
    }
}
