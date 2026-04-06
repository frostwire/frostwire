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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNull;

/**
 * Tests for MusicUtils service WeakReference management.
 *
 * Verifies that:
 * - musicPlaybackServiceRef starts as null
 * - getService() returns null when ref is null
 * - getService() returns null after ref is cleared (simulating GC)
 *
 * These run on the JVM via Robolectric — no device required.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public class MusicUtilsServiceRefTest {

    /**
     * The static WeakReference field must start null (no service bound yet).
     * Verified via reflection since it's private.
     */
    @Test
    public void serviceRef_startsNull() throws Exception {
        Field refField = com.andrew.apollo.utils.MusicUtils.class
                .getDeclaredField("musicPlaybackServiceRef");
        refField.setAccessible(true);
        Object ref = refField.get(null);
        assertNull("musicPlaybackServiceRef must be null before any service connection", ref);
    }

    /**
     * getService() private helper must return null when the ref is null.
     */
    @Test
    public void getService_returnsNull_whenRefIsNull() throws Exception {
        Field refField = com.andrew.apollo.utils.MusicUtils.class
                .getDeclaredField("musicPlaybackServiceRef");
        refField.setAccessible(true);
        refField.set(null, null); // ensure null

        Method getService = com.andrew.apollo.utils.MusicUtils.class
                .getDeclaredMethod("getService");
        getService.setAccessible(true);
        Object result = getService.invoke(null);
        assertNull("getService() must return null when ref is null", result);
    }

    /**
     * getMusicPlaybackService() public method must return null when no service is bound.
     */
    @Test
    public void getMusicPlaybackService_returnsNull_whenUnbound() throws Exception {
        Field refField = com.andrew.apollo.utils.MusicUtils.class
                .getDeclaredField("musicPlaybackServiceRef");
        refField.setAccessible(true);
        refField.set(null, null); // ensure null

        MusicPlaybackService result = com.andrew.apollo.utils.MusicUtils.getMusicPlaybackService();
        assertNull("getMusicPlaybackService() must return null when service is not bound", result);
    }
}
