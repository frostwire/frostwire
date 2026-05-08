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
 * Structural regression test: notification taps must use a trampoline activity
 * which creates MainActivity below AudioPlayerActivity so Back returns home.
 */
public class MusicNotificationSessionActivityStructureTest {

    private static final String SOURCE_FILE = "apollo/src/com/andrew/apollo/MusicPlaybackService.java";
    private static final String NOTIFICATION_PROVIDER_SOURCE_FILE = "apollo/src/com/andrew/apollo/ApolloMediaNotificationProvider.java";
    private static final String TRAMPOLINE_SOURCE_FILE = "apollo/src/com/andrew/apollo/ui/activities/AudioPlayerNotificationActivity.java";

    @Test
    public void mediaSessionActivity_usesExplicitMainThenPlayerBackStack() throws IOException {
        String source = new String(Files.readAllBytes(resolveSourcePath()));

        assertTrue("Music notification session activity must point to the trampoline activity",
                source.contains("AudioPlayerNotificationActivity.createPendingIntent(this)"));
    }

    @Test
    public void notificationProvider_setsContentIntentDirectlyToTrampoline() throws IOException {
        String source = new String(Files.readAllBytes(resolveSourcePath(NOTIFICATION_PROVIDER_SOURCE_FILE)));

        assertTrue("Notification builder must not rely on a stale mediaSession.getSessionActivity() value",
                source.contains("setContentIntent(AudioPlayerNotificationActivity.createPendingIntent(service))"));
    }

    @Test
    public void notificationTrampoline_buildsMainThenPlayerStack() throws IOException {
        String source = new String(Files.readAllBytes(resolveSourcePath(TRAMPOLINE_SOURCE_FILE)));

        assertTrue("Notification trampoline must build a task stack",
                source.contains("TaskStackBuilder.create(this)"));
        assertTrue("Notification trampoline must put MainActivity under the player",
                source.contains("new Intent(this, MainActivity.class)"));
        assertTrue("Notification trampoline must open AudioPlayerActivity on top",
                source.contains("new Intent(this, AudioPlayerActivity.class)"));
        assertTrue("Notification trampoline must own creation of its PendingIntent",
                source.contains("PendingIntent.getActivity"));
        assertTrue("Notification trampoline must finish itself after launching the player stack",
                source.contains("finish();"));
    }

    private static Path resolveSourcePath() {
        return resolveSourcePath(SOURCE_FILE);
    }

    private static Path resolveSourcePath(String sourceFile) {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        return projectRoot.resolve(sourceFile);
    }
}
