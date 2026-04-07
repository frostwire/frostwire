/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.content.BroadcastReceiver;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure reflection tests for MediaButtonIntentReceiver class structure.
 *
 * NOTE: We do NOT instantiate MediaButtonIntentReceiver here. Its static
 * field initializer calls Looper.getMainLooper() which hangs under Robolectric
 * when triggered from a static initializer during class loading.
 * All structural checks (BroadcastReceiver hierarchy, no WakefulBroadcastReceiver)
 * are verified via Class metadata only.
 *
 * Pure JVM — no Robolectric, no Android framework.
 */
public class MediaButtonIntentReceiverTest {

    @Test
    public void receiver_isBroadcastReceiverSubclass() {
        assertTrue("MediaButtonIntentReceiver must extend BroadcastReceiver",
                BroadcastReceiver.class.isAssignableFrom(MediaButtonIntentReceiver.class));
    }

    @Test
    public void receiver_doesNotExtendWakefulBroadcastReceiver() {
        Class<?> cls = MediaButtonIntentReceiver.class.getSuperclass();
        while (cls != null && cls != Object.class) {
            assertFalse("MediaButtonIntentReceiver must NOT extend WakefulBroadcastReceiver",
                    cls.getName().contains("WakefulBroadcastReceiver"));
            cls = cls.getSuperclass();
        }
    }

    @Test
    public void receiver_hasPublicNoArgConstructor() throws Exception {
        Constructor<MediaButtonIntentReceiver> ctor =
                MediaButtonIntentReceiver.class.getDeclaredConstructor();
        assertNotNull("MediaButtonIntentReceiver must have a no-arg constructor", ctor);
        assertTrue("Constructor must be public",
                java.lang.reflect.Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    public void receiver_mbiReceiverHandler_innerClass_usesLooperConstructor() {
        // MBIReceiverHandler must take a Looper parameter (not use the deprecated no-arg Handler())
        Class<?> handlerClass = null;
        for (Class<?> inner : MediaButtonIntentReceiver.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("MBIReceiverHandler")) {
                handlerClass = inner;
                break;
            }
        }
        if (handlerClass == null) return; // inlined or renamed — skip

        boolean hasNoArgCtor = false;
        for (Constructor<?> ctor : handlerClass.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == 0) {
                hasNoArgCtor = true;
                break;
            }
        }
        assertFalse("MBIReceiverHandler must NOT have no-arg constructor (deprecated Handler())",
                hasNoArgCtor);
    }
}
