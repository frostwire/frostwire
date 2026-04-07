/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for MusicPlaybackService constants and configuration.
 *
 * Accesses only public static final String/int constants via reflection —
 * does NOT instantiate or load MusicPlaybackService fully, avoiding the
 * ExoPlayer/media3 class instrumentation that hangs Robolectric.
 *
 * Pure JVM — no Robolectric, no Android framework.
 */
public class MusicPlaybackServiceStateTest {

    // ---- Broadcast action strings ----

    @Test
    public void broadcastAction_PLAYSTATE_CHANGED_defined() throws Exception {
        String val = getStringConstant("PLAYSTATE_CHANGED");
        assertNotNull(val);
        assertFalse(val.isEmpty());
    }

    @Test
    public void broadcastAction_META_CHANGED_defined() throws Exception {
        String val = getStringConstant("META_CHANGED");
        assertNotNull(val);
        assertFalse(val.isEmpty());
    }

    @Test
    public void broadcastAction_QUEUE_CHANGED_defined() throws Exception {
        String val = getStringConstant("QUEUE_CHANGED");
        assertNotNull(val);
        assertFalse(val.isEmpty());
    }

    @Test
    public void broadcastAction_POSITION_CHANGED_defined() throws Exception {
        String val = getStringConstant("POSITION_CHANGED");
        assertNotNull(val);
        assertFalse(val.isEmpty());
    }

    @Test
    public void broadcastActions_allDistinct() throws Exception {
        String[] names = {
                "PLAYSTATE_CHANGED", "META_CHANGED", "QUEUE_CHANGED",
                "POSITION_CHANGED", "REPEATMODE_CHANGED", "SHUFFLEMODE_CHANGED"
        };
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            String val = getStringConstant(name);
            assertNotNull("Constant " + name + " must not be null", val);
            assertTrue("Broadcast action '" + val + "' is duplicated", seen.add(val));
        }
    }

    // ---- Repeat mode constants ----

    @Test
    public void repeatMode_NONE_isZero() throws Exception {
        assertEquals(0, getIntConstant("REPEAT_NONE"));
    }

    @Test
    public void repeatMode_CURRENT_isOne() throws Exception {
        assertEquals(1, getIntConstant("REPEAT_CURRENT"));
    }

    @Test
    public void repeatMode_ALL_isTwo() throws Exception {
        assertEquals(2, getIntConstant("REPEAT_ALL"));
    }

    @Test
    public void repeatModes_distinct() throws Exception {
        int none = getIntConstant("REPEAT_NONE");
        int all = getIntConstant("REPEAT_ALL");
        int current = getIntConstant("REPEAT_CURRENT");
        assertTrue(none != all && all != current && none != current);
    }

    // ---- Command strings ----

    @Test
    public void commandStrings_definedAndDistinct() throws Exception {
        String[] names = {
                "CMDPLAY", "CMDPAUSE", "CMDSTOP",
                "CMDNEXT", "CMDPREVIOUS", "CMDTOGGLEPAUSE"
        };
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            String val = getStringConstant(name);
            assertNotNull("Command string " + name + " must not be null", val);
            assertTrue("Command string '" + val + "' is duplicated", seen.add(val));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers — access constants via reflection without triggering <clinit>
    // -----------------------------------------------------------------------

    private static String getStringConstant(String fieldName) throws Exception {
        Field f = MusicPlaybackService.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        assertTrue("Field " + fieldName + " must be static", Modifier.isStatic(f.getModifiers()));
        return (String) f.get(null);
    }

    private static int getIntConstant(String fieldName) throws Exception {
        Field f = MusicPlaybackService.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        assertTrue("Field " + fieldName + " must be static", Modifier.isStatic(f.getModifiers()));
        return f.getInt(null);
    }
}
