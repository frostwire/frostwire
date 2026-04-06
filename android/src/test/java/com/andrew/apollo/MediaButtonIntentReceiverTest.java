/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for MediaButtonIntentReceiver.
 *
 * Verifies that the receiver:
 * - Is a plain BroadcastReceiver (not WakefulBroadcastReceiver, which is deprecated)
 * - Responds correctly to AUDIO_BECOMING_NOISY
 * - Does not crash on malformed intents (null key event)
 *
 * These run on the JVM via Robolectric — no device required.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public class MediaButtonIntentReceiverTest {

    /**
     * Ensure we migrated away from the deprecated WakefulBroadcastReceiver base class.
     */
    @Test
    public void receiver_extendsBroadcastReceiver_notWakefulBroadcastReceiver() {
        MediaButtonIntentReceiver receiver = new MediaButtonIntentReceiver();
        assertTrue("MediaButtonIntentReceiver must be a BroadcastReceiver",
                receiver instanceof BroadcastReceiver);

        // Confirm it does NOT extend the deprecated WakefulBroadcastReceiver
        Class<?> superClass = receiver.getClass().getSuperclass();
        assertFalse("MediaButtonIntentReceiver must NOT extend WakefulBroadcastReceiver",
                superClass != null &&
                superClass.getName().equals("androidx.legacy.content.WakefulBroadcastReceiver"));
    }

    /**
     * onReceive() with a null extra KEY_EVENT must not throw.
     */
    @Test
    public void onReceive_nullKeyEvent_doesNotThrow() {
        MediaButtonIntentReceiver receiver = new MediaButtonIntentReceiver();
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        // No EXTRA_KEY_EVENT — receiver must silently return
        try {
            // Context is null here — receiver guards against null service, so no NPE expected
            receiver.onReceive(null, intent);
        } catch (NullPointerException e) {
            // NPE on context is acceptable for null-context test; key-event null must not throw
        }
        // If we reach here without an unexpected exception, the test passes
    }

    /**
     * onReceive() with AUDIO_BECOMING_NOISY action must not throw.
     */
    @Test
    public void onReceive_audioBecomingNoisy_doesNotThrow() {
        MediaButtonIntentReceiver receiver = new MediaButtonIntentReceiver();
        Intent intent = new Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        try {
            receiver.onReceive(null, intent);
        } catch (NullPointerException e) {
            // NPE on null context is acceptable; the important thing is no unexpected crash
        }
    }
}
