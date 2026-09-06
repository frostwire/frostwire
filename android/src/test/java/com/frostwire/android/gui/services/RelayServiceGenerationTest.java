/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RelayServiceGenerationTest {
    @Test
    public void restartCannotAdoptAnOldStartup() {
        RelayServiceGeneration owner = new RelayServiceGeneration();
        long old = owner.current();
        owner.invalidate();
        assertFalse(owner.isCurrent(old));
        assertTrue(owner.isCurrent(owner.current()));
    }

    @Test
    public void explicitStopAndDestroyedServiceCannotBeRestartedByQueuedWork() {
        RelayServiceGeneration owner = new RelayServiceGeneration();
        long pending = owner.current();
        owner.retire();
        owner.invalidate();
        assertFalse(owner.isCurrent(pending));
        assertFalse(owner.isCurrent(owner.current()));
    }

    @Test
    public void oldCleanupDoesNotInvalidateRecreatedService() {
        RelayServiceGeneration old = new RelayServiceGeneration();
        RelayServiceGeneration recreated = new RelayServiceGeneration();
        old.retire();
        old.invalidate();
        assertTrue(recreated.isCurrent(recreated.current()));
        assertFalse(old.isCurrent(old.current()));
    }
}
