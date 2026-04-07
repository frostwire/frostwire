/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import org.junit.Test;

import java.util.ArrayDeque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the shuffle history LIFO stack contract.
 *
 * MusicPlaybackService.mHistory is an ArrayDeque used as a LIFO stack for
 * shuffle history. These tests verify the ArrayDeque contract directly —
 * without loading MusicPlaybackService (which drags in ExoPlayer and hangs
 * Robolectric during class instrumentation).
 *
 * Pure JVM — no Robolectric, no Android framework.
 */
public class ShuffleHistoryTest {

    @Test
    public void arrayDeque_usedAsStack_isLifo() {
        ArrayDeque<Integer> history = new ArrayDeque<>();
        assertTrue(history.isEmpty());

        history.push(1);
        history.push(2);
        history.push(3);

        assertEquals("peek must return last pushed", 3, (int) history.peek());
        assertEquals("pop must return 3 (LIFO)", 3, (int) history.pop());
        assertEquals("pop must return 2 (LIFO)", 2, (int) history.pop());
        assertEquals("pop must return 1 (LIFO)", 1, (int) history.pop());
        assertTrue(history.isEmpty());
    }

    @Test
    public void arrayDeque_clear_emptiesStack() {
        ArrayDeque<Integer> history = new ArrayDeque<>();
        history.push(42);
        assertFalse(history.isEmpty());
        history.clear();
        assertTrue(history.isEmpty());
    }

    @Test
    public void shuffleGuard_preventsDuplicateAdjacentPositions() {
        // Mirrors the guard in MusicPlaybackService.play():
        // if (mHistory.isEmpty() || mHistory.peek() != mPlayPos) { mHistory.push(mPlayPos); }
        ArrayDeque<Integer> history = new ArrayDeque<>();

        int pos = 5;
        if (history.isEmpty() || history.peek() != pos) history.push(pos);
        assertEquals(1, history.size());

        // Pushing same position again must be blocked by guard
        if (history.isEmpty() || history.peek() != pos) history.push(pos);
        assertEquals("duplicate adjacent position must NOT be pushed", 1, history.size());

        // Different position must be allowed
        int newPos = 7;
        if (history.isEmpty() || history.peek() != newPos) history.push(newPos);
        assertEquals(2, history.size());
        assertEquals(newPos, (int) history.peek());
    }

    @Test
    public void arrayDeque_preferredOverStack_sameLifoContract() {
        // Verifies ArrayDeque is a suitable drop-in for java.util.Stack (which is deprecated)
        ArrayDeque<Integer> deque = new ArrayDeque<>();
        java.util.Stack<Integer> stack = new java.util.Stack<>();

        for (int i = 0; i < 5; i++) {
            deque.push(i);
            stack.push(i);
        }
        while (!deque.isEmpty()) {
            assertEquals("ArrayDeque and Stack must yield same LIFO order",
                    stack.pop(), deque.pop());
        }
    }
}
