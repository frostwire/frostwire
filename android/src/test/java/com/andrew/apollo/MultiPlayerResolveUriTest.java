/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link MusicUriUtils#resolveUriString(String)}, which backs
 * {@code MusicPlaybackService.MultiPlayer.resolveUri()}.
 *
 * Pure JVM — no Robolectric, no Android framework, no ExoPlayer static init.
 */
public class MultiPlayerResolveUriTest {

    @Test
    public void contentUri_passedThrough() {
        String path = "content://media/external/audio/media/42";
        assertEquals(path, MusicUriUtils.resolveUriString(path));
    }

    @Test
    public void httpUri_passedThrough() {
        String path = "http://example.com/audio/track.mp3";
        assertEquals(path, MusicUriUtils.resolveUriString(path));
    }

    @Test
    public void httpsUri_passedThrough() {
        String path = "https://example.com/audio/track.mp3";
        assertEquals(path, MusicUriUtils.resolveUriString(path));
    }

    @Test
    public void fileUri_passedThrough() {
        String path = "file:///storage/emulated/0/Music/track.mp3";
        assertEquals(path, MusicUriUtils.resolveUriString(path));
    }

    @Test
    public void bareFilePath_becomesFileUri() {
        String path = "/storage/emulated/0/Music/track.mp3";
        String result = MusicUriUtils.resolveUriString(path);
        assertTrue("bare path must get file:// prefix", result.startsWith("file://"));
        assertTrue("file URI must contain original path", result.contains("track.mp3"));
    }

    @Test
    public void urlEncodedFilePath_decodedToFileUri() {
        String path = "/storage/emulated/0/Music/My%20Track.mp3";
        String result = MusicUriUtils.resolveUriString(path);
        assertTrue("result must be a file URI", result.startsWith("file://"));
        assertTrue("result must contain decoded filename", result.contains("My Track.mp3"));
    }

    @Test
    public void urlEncodedSpecialChars_decoded() {
        String path = "/storage/emulated/0/Music/Caf%C3%A9%20Mix.mp3";
        String result = MusicUriUtils.resolveUriString(path);
        assertTrue(result.startsWith("file://"));
        assertTrue("non-ASCII chars must be decoded", result.contains("Café Mix.mp3"));
    }

    @Test
    public void nullPath_returnsNull() {
        assertNull(MusicUriUtils.resolveUriString(null));
    }
}

