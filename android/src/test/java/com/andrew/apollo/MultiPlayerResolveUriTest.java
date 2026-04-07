/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import org.junit.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the URI resolution logic used in MusicPlaybackService.MultiPlayer.resolveUri().
 *
 * Replicates the pure string/URI logic without loading MusicPlaybackService
 * (which drags in ExoPlayer and hangs under class instrumentation).
 *
 * Pure JVM — no Robolectric, no Android framework.
 */
public class MultiPlayerResolveUriTest {

    /**
     * Mirrors MusicPlaybackService.MultiPlayer.resolveUri() logic:
     * - scheme-bearing URIs (content://, http://, https://, file://) pass through
     * - bare file paths get prefixed with file://
     * - URL-encoded paths get decoded first
     */
    private static String resolveUri(String path) {
        if (path == null) return null;
        if (path.startsWith("content://") || path.startsWith("http://")
                || path.startsWith("https://") || path.startsWith("file://")) {
            return path;
        }
        // URL-decode if needed
        String decoded = path;
        if (path.contains("%")) {
            try {
                decoded = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {
                decoded = path;
            }
        }
        return "file://" + decoded;
    }

    @Test
    public void contentUri_passedThrough() {
        String path = "content://media/external/audio/media/42";
        assertEquals(path, resolveUri(path));
    }

    @Test
    public void httpUri_passedThrough() {
        String path = "http://example.com/audio/track.mp3";
        assertEquals(path, resolveUri(path));
    }

    @Test
    public void httpsUri_passedThrough() {
        String path = "https://example.com/audio/track.mp3";
        assertEquals(path, resolveUri(path));
    }

    @Test
    public void fileUri_passedThrough() {
        String path = "file:///storage/emulated/0/Music/track.mp3";
        assertEquals(path, resolveUri(path));
    }

    @Test
    public void bareFilePath_becomesFileUri() {
        String path = "/storage/emulated/0/Music/track.mp3";
        String result = resolveUri(path);
        assertTrue("bare path must get file:// prefix", result.startsWith("file://"));
        assertTrue("file URI must contain original path", result.contains("track.mp3"));
    }

    @Test
    public void urlEncodedFilePath_decodedToFileUri() {
        String path = "/storage/emulated/0/Music/My%20Track.mp3";
        String result = resolveUri(path);
        assertTrue("result must be a file URI", result.startsWith("file://"));
        assertTrue("result must contain decoded filename", result.contains("My Track.mp3"));
    }

    @Test
    public void urlEncodedSpecialChars_decoded() {
        String path = "/storage/emulated/0/Music/Caf%C3%A9%20Mix.mp3";
        String result = resolveUri(path);
        assertTrue(result.startsWith("file://"));
        assertTrue("non-ASCII chars must be decoded", result.contains("Café Mix.mp3"));
    }

    @Test
    public void nullPath_returnsNull() {
        assertNull(resolveUri(null));
    }
}
