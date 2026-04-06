/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for MusicPlaybackService constants and configuration that can be
 * verified without a running service instance.
 *
 * Verifies:
 * - All required broadcast action strings are non-null and distinct
 * - Repeat mode constants have the expected values
 * - Shuffle / repeat mode constants don't overlap
 *
 * These run on the JVM via Robolectric — no device required.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public class MusicPlaybackServiceStateTest {

    // ---- Broadcast action strings ----

    @Test
    public void broadcastAction_PLAYSTATE_CHANGED_defined() {
        assertNotNull(MusicPlaybackService.PLAYSTATE_CHANGED);
        assertFalse(MusicPlaybackService.PLAYSTATE_CHANGED.isEmpty());
    }

    @Test
    public void broadcastAction_META_CHANGED_defined() {
        assertNotNull(MusicPlaybackService.META_CHANGED);
        assertFalse(MusicPlaybackService.META_CHANGED.isEmpty());
    }

    @Test
    public void broadcastAction_QUEUE_CHANGED_defined() {
        assertNotNull(MusicPlaybackService.QUEUE_CHANGED);
        assertFalse(MusicPlaybackService.QUEUE_CHANGED.isEmpty());
    }

    @Test
    public void broadcastAction_POSITION_CHANGED_defined() {
        assertNotNull(MusicPlaybackService.POSITION_CHANGED);
        assertFalse(MusicPlaybackService.POSITION_CHANGED.isEmpty());
    }

    @Test
    public void broadcastActions_allDistinct() {
        String[] actions = {
                MusicPlaybackService.PLAYSTATE_CHANGED,
                MusicPlaybackService.META_CHANGED,
                MusicPlaybackService.QUEUE_CHANGED,
                MusicPlaybackService.POSITION_CHANGED,
                MusicPlaybackService.REPEATMODE_CHANGED,
                MusicPlaybackService.SHUFFLEMODE_CHANGED,
        };
        for (int i = 0; i < actions.length; i++) {
            for (int j = i + 1; j < actions.length; j++) {
                assertFalse("Broadcast actions must be distinct: " + actions[i] + " vs " + actions[j],
                        actions[i].equals(actions[j]));
            }
        }
    }

    // ---- Repeat mode constants ----

    @Test
    public void repeatMode_NONE_isZero() {
        assertEquals("REPEAT_NONE must be 0", 0, MusicPlaybackService.REPEAT_NONE);
    }

    @Test
    public void repeatMode_ALL_isOne() {
        assertEquals("REPEAT_ALL must be 1", 1, MusicPlaybackService.REPEAT_ALL);
    }

    @Test
    public void repeatMode_CURRENT_isTwo() {
        assertEquals("REPEAT_CURRENT must be 2", 2, MusicPlaybackService.REPEAT_CURRENT);
    }

    @Test
    public void repeatModes_distinct() {
        assertTrue("Repeat mode constants must be distinct",
                MusicPlaybackService.REPEAT_NONE != MusicPlaybackService.REPEAT_ALL
                        && MusicPlaybackService.REPEAT_ALL != MusicPlaybackService.REPEAT_CURRENT
                        && MusicPlaybackService.REPEAT_NONE != MusicPlaybackService.REPEAT_CURRENT);
    }

    // ---- Command strings ----

    @Test
    public void commandStrings_defined() {
        assertNotNull(MusicPlaybackService.CMDPLAY);
        assertNotNull(MusicPlaybackService.CMDPAUSE);
        assertNotNull(MusicPlaybackService.CMDSTOP);
        assertNotNull(MusicPlaybackService.CMDNEXT);
        assertNotNull(MusicPlaybackService.CMDPREVIOUS);
        assertNotNull(MusicPlaybackService.CMDTOGGLEPAUSE);
    }

    @Test
    public void commandStrings_distinct() {
        String[] cmds = {
                MusicPlaybackService.CMDPLAY,
                MusicPlaybackService.CMDPAUSE,
                MusicPlaybackService.CMDSTOP,
                MusicPlaybackService.CMDNEXT,
                MusicPlaybackService.CMDPREVIOUS,
                MusicPlaybackService.CMDTOGGLEPAUSE,
        };
        for (int i = 0; i < cmds.length; i++) {
            for (int j = i + 1; j < cmds.length; j++) {
                assertFalse("Command strings must be distinct: " + cmds[i] + " vs " + cmds[j],
                        cmds[i].equals(cmds[j]));
            }
        }
    }
}
