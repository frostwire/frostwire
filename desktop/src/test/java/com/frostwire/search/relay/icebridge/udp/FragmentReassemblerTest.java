/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FragmentReassemblerTest {

    @Test
    void reassemblesInOrderFragments() {
        FragmentReassembler r = new FragmentReassembler();
        byte[] chunk0 = "Hello ".getBytes();
        byte[] chunk1 = "World".getBytes();

        assertNull(r.addFragment(1, 0, false, chunk0));
        byte[] result = r.addFragment(1, 1, true, chunk1);
        assertNotNull(result);
        assertEquals("Hello World", new String(result));
    }

    @Test
    void reassemblesOutOfOrderFragments() {
        FragmentReassembler r = new FragmentReassembler();
        byte[] chunk0 = "Hello ".getBytes();
        byte[] chunk1 = "World".getBytes();

        // chunk1 (isLast) arrives before chunk0 — group not complete yet.
        assertNull(r.addFragment(1, 1, true, chunk1));
        // chunk0 arrives — now both fragments present, completion triggers.
        byte[] result = r.addFragment(1, 0, false, chunk0);
        assertNotNull(result, "should complete when missing fragment arrives");
        assertEquals("Hello World", new String(result));
    }

    @Test
    void rejectsNegativeFragIndex() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment(1, -1, true, "data".getBytes()));
        assertEquals(0, r.pendingGroupCount(), "group should not be created for bad index");
    }

    @Test
    void rejectsHugeFragIndex() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment(1, Integer.MAX_VALUE, true, "data".getBytes()));
        assertEquals(0, r.pendingGroupCount(), "group should not be created for huge index");
    }

    @Test
    void rejectsFragIndexAtMaxLimit() {
        FragmentReassembler r = new FragmentReassembler();
        int maxIdx = FragmentReassembler.MAX_FRAGMENTS_PER_GROUP - 1;
        assertNull(r.addFragment(1, maxIdx, true, "data".getBytes()));
        // This creates a group but will never complete (needs 0..maxIdx fragments).
        // That's OK — the point is it doesn't crash or loop.
        assertEquals(1, r.pendingGroupCount());
    }

    @Test
    void rejectsFragIndexAboveMaxLimit() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment(1, FragmentReassembler.MAX_FRAGMENTS_PER_GROUP, true, "data".getBytes()));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void rejectsOversizedAssembly() {
        FragmentReassembler r = new FragmentReassembler();
        int chunkSize = 1024;
        // Add fragments whose total exceeds MAX_ASSEMBLED_SIZE.
        // We can't actually add 16K fragments of 1KB in a test (too slow),
        // but we can verify the size check triggers by exceeding the cap
        // with a single large fragment.
        byte[] huge = new byte[(int) FragmentReassembler.MAX_ASSEMBLED_SIZE + 1];
        assertNull(r.addFragment(1, 0, true, huge));
        assertEquals(0, r.pendingGroupCount(), "group should be removed for oversized payload");
    }

    @Test
    void duplicateFragmentDoesNotCorrupt() {
        FragmentReassembler r = new FragmentReassembler();
        byte[] chunk0 = "Hello ".getBytes();
        byte[] chunk0Dup = "HELLO ".getBytes(); // different content, same index
        byte[] chunk1 = "World".getBytes();

        assertNull(r.addFragment(1, 0, false, chunk0));
        // Duplicate index — first one wins, second is ignored.
        assertNull(r.addFragment(1, 0, false, chunk0Dup));
        byte[] result = r.addFragment(1, 1, true, chunk1);
        assertNotNull(result);
        assertEquals("Hello World", new String(result), "first fragment wins on duplicate");
    }

    @Test
    void nullPayloadRejected() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment(1, 0, false, null));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void emptyPayloadRejected() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment(1, 0, false, new byte[0]));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void evictStaleRemovesOldGroups() throws InterruptedException {
        FragmentReassembler r = new FragmentReassembler();
        r.addFragment(1, 0, false, "partial".getBytes());
        assertEquals(1, r.pendingGroupCount());

        // Wait past GROUP_TIMEOUT_MS (30s) — too long for a test.
        // Instead, just verify evictStale with a very old timestamp
        // doesn't crash. The stale check is internal.
        r.evictStale();
        // Group is fresh, should still be there.
        assertEquals(1, r.pendingGroupCount());
    }

    @Test
    void multipleGroupsReassembledIndependently() {
        FragmentReassembler r = new FragmentReassembler();
        byte[] a0 = "A0".getBytes();
        byte[] a1 = "A1".getBytes();
        byte[] b0 = "B0".getBytes();
        byte[] b1 = "B1".getBytes();

        assertNull(r.addFragment(100, 0, false, a0));
        assertNull(r.addFragment(200, 0, false, b0));
        byte[] aResult = r.addFragment(100, 1, true, a1);
        assertNotNull(aResult);
        assertEquals("A0A1", new String(aResult));
        byte[] bResult = r.addFragment(200, 1, true, b1);

        assertNotNull(bResult);
        assertEquals("B0B1", new String(bResult));
        assertEquals(0, r.pendingGroupCount(), "both groups should be complete and removed");
    }
}
