/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Structural regression test: notification taps must create a fresh stack with
 * MainActivity below AudioPlayerActivity so Back returns to the home/search UI.
 */
public class MusicNotificationSessionActivityStructureTest {

    private static final String SOURCE_FILE = "apollo/src/com/andrew/apollo/MusicPlaybackService.java";

    @Test
    public void mediaSessionActivity_usesExplicitMainThenPlayerBackStack() throws IOException {
        String source = new String(Files.readAllBytes(resolveSourcePath()));

        assertTrue("Music notification session activity must use PendingIntent.getActivities()",
                source.contains("PendingIntent.getActivities"));
        assertTrue("Music notification stack must start with MainActivity",
                source.contains("new Intent(this, MainActivity.class)"));
        assertTrue("Music notification stack must open AudioPlayerActivity on top",
                source.contains("new Intent(this, AudioPlayerActivity.class)"));
        assertTrue("Music notification stack must clear old task state before creating a fresh stack",
                source.contains("Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK"));
        assertTrue("Music notification PendingIntent must place MainActivity below AudioPlayerActivity",
                source.contains("new Intent[]{mainIntent, playerIntent}"));
    }

    private static Path resolveSourcePath() {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        return projectRoot.resolve(SOURCE_FILE);
    }
}
