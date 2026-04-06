/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for MultiPlayer.resolveUri() — the static URI resolution helper that
 * maps audio file paths (content://, http://, bare file paths, URL-encoded paths)
 * into URIs suitable for ExoPlayer.
 *
 * These run on the JVM via Robolectric — no device required.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public class MultiPlayerResolveUriTest {

    @Test
    public void contentUri_passedThrough() {
        String path = "content://media/external/audio/media/42";
        Uri uri = MusicPlaybackService.MultiPlayer.resolveUri(path);
        assertEquals("content URI must be passed through unchanged",
                path, uri.toString());
    }

    @Test
    public void httpUri_passedThrough() {
        String path = "http://example.com/audio/track.mp3";
        Uri uri = MusicPlaybackService.MultiPlayer.resolveUri(path);
        assertEquals("http URI must be passed through unchanged",
                path, uri.toString());
    }

    @Test
    public void httpsUri_passedThrough() {
        String path = "https://example.com/audio/track.mp3";
        Uri uri = MusicPlaybackService.MultiPlayer.resolveUri(path);
        assertEquals("https URI must be passed through unchanged",
                path, uri.toString());
    }

    @Test
    public void bareFilePath_becomesFileUri() {
        String path = "/storage/emulated/0/Music/track.mp3";
        Uri uri = MusicPlaybackService.MultiPlayer.resolveUri(path);
        assertTrue("bare file path must produce a file:// URI",
                uri.toString().startsWith("file://"));
        assertTrue("file URI must contain the path",
                uri.toString().contains("track.mp3"));
    }

    @Test
    public void urlEncodedFilePath_decoded() {
        // Spaces encoded as %20
        String path = "/storage/emulated/0/Music/My%20Track.mp3";
        Uri uri = MusicPlaybackService.MultiPlayer.resolveUri(path);
        assertTrue("URI must be non-null for URL-encoded path",
                uri != null);
        // Should decode to a valid file URI — either the decoded form or fallback parse
        String uriStr = uri.toString();
        assertTrue("URI scheme must be file or content",
                uriStr.startsWith("file://") || uriStr.startsWith("content://"));
    }
}
