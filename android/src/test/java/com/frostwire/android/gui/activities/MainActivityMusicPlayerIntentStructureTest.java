/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Structural regression test: if System UI falls back to the app music launcher,
 * MainActivity must forward that launch to AudioPlayerActivity.
 */
public class MainActivityMusicPlayerIntentStructureTest {

    private static final String SOURCE_FILE = "src/main/java/com/frostwire/android/gui/activities/MainActivity.java";

    @Test
    public void mainActivity_forwardsMusicPlayerLaunchIntentToAudioPlayer() throws IOException {
        String source = new String(Files.readAllBytes(resolveSourcePath()));

        assertTrue("MainActivity must detect ACTION_MUSIC_PLAYER launches",
                source.contains("ACTION_MUSIC_PLAYER.equals(intent.getAction())"));
        assertTrue("MainActivity must detect CATEGORY_APP_MUSIC launches",
                source.contains("intent.hasCategory(Intent.CATEGORY_APP_MUSIC)"));
        assertTrue("MainActivity must detect Oplus Seedling media-card launcher fallback",
                source.contains("isOplusSeedlingMediaIntent(intent)"));
        assertTrue("MainActivity must require the Oplus Seedling marker extra",
                source.contains("EXTRA_OPLUS_SEEDLING"));
        assertTrue("MainActivity must require a mediaId extra before redirecting launcher intents",
                source.contains("EXTRA_MEDIA_ID"));
        assertTrue("MainActivity must forward music player launches to AudioPlayerActivity",
                source.contains("startActivity(new Intent(this, AudioPlayerActivity.class))"));
    }

    private static Path resolveSourcePath() {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        return projectRoot.resolve(SOURCE_FILE);
    }
}
