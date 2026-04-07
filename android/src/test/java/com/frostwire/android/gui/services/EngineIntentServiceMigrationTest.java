/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

import android.app.Service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the L2 migration: EngineIntentService must extend plain Service,
 * not the deprecated JobIntentService.
 *
 * Pure JVM test — no Android framework, no Robolectric.
 */
public class EngineIntentServiceMigrationTest {

    @Test
    public void engineIntentService_extendsService() {
        assertTrue("EngineIntentService must extend android.app.Service",
                Service.class.isAssignableFrom(EngineIntentService.class));
    }

    @Test
    public void engineIntentService_doesNotExtendJobIntentService() {
        Class<?> cls = EngineIntentService.class;
        while (cls != null && cls != Object.class) {
            assertFalse("EngineIntentService must NOT extend JobIntentService (deprecated)",
                    cls.getName().equals("androidx.core.app.JobIntentService"));
            cls = cls.getSuperclass();
        }
    }

    @Test
    public void engineIntentService_implementsIEngineService() {
        assertTrue("EngineIntentService must implement IEngineService",
                IEngineService.class.isAssignableFrom(EngineIntentService.class));
    }

    @Test
    public void iEngineService_stateConstants_areDistinct() {
        byte[] states = {
                IEngineService.STATE_UNSTARTED,
                IEngineService.STATE_INVALID,
                IEngineService.STATE_STARTED,
                IEngineService.STATE_STARTING,
                IEngineService.STATE_STOPPED,
                IEngineService.STATE_STOPPING,
                IEngineService.STATE_DISCONNECTED,
        };
        for (int i = 0; i < states.length; i++) {
            for (int j = i + 1; j < states.length; j++) {
                assertFalse("IEngineService state constants must all be distinct: states[" + i + "]=" + states[i]
                        + " vs states[" + j + "]=" + states[j], states[i] == states[j]);
            }
        }
    }

    @Test
    public void iEngineService_stateUnstarted_isZero() {
        assertTrue("STATE_UNSTARTED must be 0", IEngineService.STATE_UNSTARTED == 0);
    }

    @Test
    public void iEngineService_stateInvalid_isNegative() {
        assertTrue("STATE_INVALID must be negative", IEngineService.STATE_INVALID < 0);
    }
}
