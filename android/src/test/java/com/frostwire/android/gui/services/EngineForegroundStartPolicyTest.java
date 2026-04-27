/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EngineForegroundStartPolicyTest {

    @Test
    public void android15BackgroundNullIntentRestart_stopsInsteadOfPromoting() {
        assertEquals(
                EngineForegroundStartPolicy.Action.STOP_BACKGROUND_RESTART,
                EngineForegroundStartPolicy.resolve(35, true, false));
    }

    @Test
    public void android16BackgroundNullIntentRestart_stopsInsteadOfPromoting() {
        assertEquals(
                EngineForegroundStartPolicy.Action.STOP_BACKGROUND_RESTART,
                EngineForegroundStartPolicy.resolve(36, true, false));
    }

    @Test
    public void android15ForegroundNullIntentRestart_keepsForegroundBehavior() {
        assertEquals(
                EngineForegroundStartPolicy.Action.START_FOREGROUND,
                EngineForegroundStartPolicy.resolve(35, true, true));
    }

    @Test
    public void android11BackgroundNullIntentRestart_keepsLegacyBehavior() {
        assertEquals(
                EngineForegroundStartPolicy.Action.START_FOREGROUND,
                EngineForegroundStartPolicy.resolve(30, true, false));
    }

    @Test
    public void explicitStartRequest_neverGetsAutoStoppedByNullRestartPolicy() {
        assertEquals(
                EngineForegroundStartPolicy.Action.START_FOREGROUND,
                EngineForegroundStartPolicy.resolve(35, false, false));
    }
}
