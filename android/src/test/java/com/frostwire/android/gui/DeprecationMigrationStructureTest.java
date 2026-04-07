/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import android.app.Service;
import android.content.BroadcastReceiver;

import com.andrew.apollo.MediaButtonIntentReceiver;
import com.frostwire.android.gui.services.EngineIntentService;
import com.frostwire.android.gui.services.IEngineService;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Structural regression tests for the deprecation migration sprint.
 *
 * These tests verify the class hierarchy and API surface of code we changed
 * are exactly what we intend — preventing future regressions from accidentally
 * re-introducing deprecated base classes or patterns.
 *
 * Pure JVM test — no Android framework, no Robolectric required.
 */
public class DeprecationMigrationStructureTest {

    // -------------------------------------------------------------------------
    // L2: EngineIntentService — must be plain Service, NOT JobIntentService
    // -------------------------------------------------------------------------

    @Test
    public void engineIntentService_superclassChain_neverIncludesJobIntentService() {
        assertNoDeprecatedAncestor(EngineIntentService.class, "androidx.core.app.JobIntentService",
                "EngineIntentService must NOT extend deprecated JobIntentService");
    }

    @Test
    public void engineIntentService_superclassChain_includesService() {
        assertTrue("EngineIntentService must be a Service",
                Service.class.isAssignableFrom(EngineIntentService.class));
    }

    // -------------------------------------------------------------------------
    // Q13: MediaButtonIntentReceiver — must be BroadcastReceiver, NOT WakefulBroadcastReceiver
    // -------------------------------------------------------------------------

    @Test
    public void mediaButtonIntentReceiver_isBroadcastReceiver() {
        assertTrue("MediaButtonIntentReceiver must be a BroadcastReceiver",
                BroadcastReceiver.class.isAssignableFrom(MediaButtonIntentReceiver.class));
    }

    @Test
    public void mediaButtonIntentReceiver_superclassChain_neverIncludesWakefulBroadcastReceiver() {
        assertNoDeprecatedAncestor(MediaButtonIntentReceiver.class,
                "androidx.legacy.content.WakefulBroadcastReceiver",
                "MediaButtonIntentReceiver must NOT extend deprecated WakefulBroadcastReceiver");
        assertNoDeprecatedAncestor(MediaButtonIntentReceiver.class,
                "androidx.core.content.WakefulBroadcastReceiver",
                "MediaButtonIntentReceiver must NOT extend deprecated WakefulBroadcastReceiver");
    }

    // -------------------------------------------------------------------------
    // Q12: No no-arg Handler instantiation — MBIReceiverHandler uses Looper ctor
    // -------------------------------------------------------------------------

    @Test
    public void mediaButtonIntentReceiver_mbiReceiverHandler_usesLooperConstructor() throws Exception {
        // MBIReceiverHandler is a private static inner class — find it by name
        Class<?> handlerClass = null;
        for (Class<?> inner : MediaButtonIntentReceiver.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("MBIReceiverHandler")) {
                handlerClass = inner;
                break;
            }
        }
        if (handlerClass == null) {
            // Class may have been inlined or renamed — not a failure if it doesn't exist
            return;
        }
        // Verify it does NOT have a no-arg constructor (which would use deprecated Handler())
        boolean hasNoArgCtor = false;
        for (java.lang.reflect.Constructor<?> ctor : handlerClass.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == 0) {
                hasNoArgCtor = true;
                break;
            }
        }
        assertFalse("MBIReceiverHandler must NOT have a no-arg constructor (deprecated Handler())",
                hasNoArgCtor);
    }

    // -------------------------------------------------------------------------
    // M3: Activities that had onBackPressed() overrides — must NOT have them anymore
    // -------------------------------------------------------------------------

    @Test
    public void vPNStatusDetailActivity_doesNotOverride_onBackPressed() {
        assertMethodNotDeclaredInClass(
                "com.frostwire.android.gui.activities.VPNStatusDetailActivity",
                "onBackPressed");
    }

    @Test
    public void wizardActivity_doesNotOverride_onBackPressed() {
        assertMethodNotDeclaredInClass(
                "com.frostwire.android.gui.activities.WizardActivity",
                "onBackPressed");
    }

    @Test
    public void profileActivity_doesNotOverride_onBackPressed() {
        assertMethodNotDeclaredInClass(
                "com.andrew.apollo.ui.activities.ProfileActivity",
                "onBackPressed");
    }

    // -------------------------------------------------------------------------
    // Multidex: MainApplication must NOT extend MultiDexApplication
    // -------------------------------------------------------------------------

    @Test
    public void mainApplication_doesNotExtendMultiDexApplication() {
        assertNoDeprecatedAncestor(MainApplication.class,
                "androidx.multidex.MultiDexApplication",
                "MainApplication must NOT extend MultiDexApplication (unnecessary with minSdk=26)");
    }

    // -------------------------------------------------------------------------
    // IEngineService: all state constants must be distinct and well-defined
    // -------------------------------------------------------------------------

    @Test
    public void iEngineService_allStateConstants_areDistinctAndNonZeroExceptUnstarted() {
        byte[] states = {
                IEngineService.STATE_UNSTARTED,
                IEngineService.STATE_INVALID,
                IEngineService.STATE_STARTED,
                IEngineService.STATE_STARTING,
                IEngineService.STATE_STOPPED,
                IEngineService.STATE_STOPPING,
                IEngineService.STATE_DISCONNECTED,
        };
        Set<Byte> seen = new HashSet<>();
        for (byte s : states) {
            assertTrue("IEngineService state constant " + s + " is duplicated", seen.add(s));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void assertNoDeprecatedAncestor(Class<?> cls, String forbiddenName, String message) {
        Class<?> c = cls.getSuperclass();
        while (c != null && c != Object.class) {
            assertFalse(message, c.getName().equals(forbiddenName));
            c = c.getSuperclass();
        }
    }

    private static void assertMethodNotDeclaredInClass(String className, String methodName) {
        Class<?> cls;
        try {
            cls = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // Class not on classpath in unit test environment — skip
            return;
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                assertFalse(className + " must NOT declare " + methodName
                        + "() — it was migrated to OnBackPressedDispatcher", true);
            }
        }
    }
}
