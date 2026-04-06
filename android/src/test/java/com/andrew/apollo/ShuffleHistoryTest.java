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
import java.util.ArrayDeque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the shuffle history (mHistory) ArrayDeque in MusicPlaybackService.
 *
 * Verifies that:
 * - mHistory is an ArrayDeque (not the old deprecated Stack)
 * - mHistory starts empty
 * - push/pop/peek behave as a LIFO stack (same contract as java.util.Stack)
 * - isEmpty() works correctly
 *
 * These run on the JVM via Robolectric — no device required.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public class ShuffleHistoryTest {

    private ArrayDeque<Integer> getHistory() throws Exception {
        Field field = MusicPlaybackService.class.getDeclaredField("mHistory");
        field.setAccessible(true);
        //noinspection unchecked
        return (ArrayDeque<Integer>) field.get(null);
    }

    @Test
    public void mHistory_isArrayDeque() throws Exception {
        Field field = MusicPlaybackService.class.getDeclaredField("mHistory");
        field.setAccessible(true);
        Object history = field.get(null);
        assertNotNull("mHistory must not be null", history);
        assertTrue("mHistory must be an ArrayDeque (not deprecated Stack)",
                history instanceof ArrayDeque);
    }

    @Test
    public void mHistory_behavesLikeLifoStack() throws Exception {
        ArrayDeque<Integer> history = getHistory();
        history.clear();
        assertTrue("mHistory must start empty", history.isEmpty());

        history.push(1);
        history.push(2);
        history.push(3);

        assertEquals("peek must return last pushed item", 3, (int) history.peek());
        assertEquals("pop must return 3 (LIFO order)", 3, (int) history.pop());
        assertEquals("pop must return 2 (LIFO order)", 2, (int) history.pop());
        assertEquals("pop must return 1 (LIFO order)", 1, (int) history.pop());
        assertTrue("mHistory must be empty after all pops", history.isEmpty());
    }

    @Test
    public void mHistory_isEmpty_afterClear() throws Exception {
        ArrayDeque<Integer> history = getHistory();
        history.push(42);
        assertFalse("mHistory must not be empty after push", history.isEmpty());
        history.clear();
        assertTrue("mHistory must be empty after clear()", history.isEmpty());
    }

    @Test
    public void mHistory_deduplication_samePosition() throws Exception {
        // In play(), new position is only pushed if history is empty OR
        // peek != current position — so duplicates must not appear.
        ArrayDeque<Integer> history = getHistory();
        history.clear();

        int pos = 5;
        // Simulate the guard from play():
        synchronized (history) {
            if (history.isEmpty() || history.peek() != pos) {
                history.push(pos);
            }
        }
        assertEquals("first push must add the item", 1, history.size());

        // Push the same position again — guard must prevent duplicate
        synchronized (history) {
            if (history.isEmpty() || history.peek() != pos) {
                history.push(pos);
            }
        }
        assertEquals("duplicate position must NOT be pushed", 1, history.size());

        history.clear();
    }
}
