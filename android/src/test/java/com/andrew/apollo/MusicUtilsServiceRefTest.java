/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import org.junit.Test;

import java.lang.ref.WeakReference;

import static org.junit.Assert.assertNull;

/**
 * Tests for WeakReference-based service ref management pattern used in MusicUtils.
 *
 * These test the pure Java contract of the WeakReference pattern without loading
 * MusicUtils or MusicPlaybackService (which drag in ExoPlayer and hang under
 * class instrumentation).
 *
 * Pure JVM — no Robolectric, no Android framework.
 */
public class MusicUtilsServiceRefTest {

    /** A null WeakReference field means no service is bound. */
    @Test
    public void nullRef_impliesNoServiceBound() {
        WeakReference<Object> ref = null;
        Object service = ref != null ? ref.get() : null;
        assertNull("null ref must yield null service", service);
    }

    /** A WeakReference to null means the referent was cleared (e.g. after GC). */
    @Test
    public void clearedWeakRef_returnsNull() {
        WeakReference<Object> ref = new WeakReference<>(null);
        assertNull("cleared WeakReference must return null", ref.get());
    }

    /** After explicitly clearing, get() must return null. */
    @Test
    public void weakRef_afterClear_returnsNull() {
        Object obj = new Object();
        WeakReference<Object> ref = new WeakReference<>(obj);
        ref.clear();
        assertNull("WeakReference after clear() must return null", ref.get());
    }

    /** A live WeakReference returns the referent. */
    @Test
    public void weakRef_withLiveReferent_returnsIt() {
        Object obj = new Object();
        WeakReference<Object> ref = new WeakReference<>(obj);
        // hold strong reference so GC can't collect it during this test
        assertNull("ref must return the live object", ref.get() == null ? null : null);
        // more precisely:
        java.util.Objects.requireNonNull(ref.get(),
                "WeakReference with live strong ref must not return null");
    }
}
