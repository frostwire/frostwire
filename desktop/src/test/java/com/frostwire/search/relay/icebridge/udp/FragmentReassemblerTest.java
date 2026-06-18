/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FragmentReassemblerTest {

    @Test
    void reassemblesInOrderFragments() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("127.0.0.1:1", 0, false, "Hello ".getBytes()));
        byte[] result = r.addFragment("127.0.0.1:1", 1, true, "World".getBytes());
        assertNotNull(result);
        assertEquals("Hello World", new String(result));
    }

    @Test
    void reassemblesOutOfOrderFragments() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("127.0.0.1:1", 1, true, "World".getBytes()));
        byte[] result = r.addFragment("127.0.0.1:1", 0, false, "Hello ".getBytes());
        assertNotNull(result, "should complete when missing fragment arrives");
        assertEquals("Hello World", new String(result));
    }

    @Test
    void rejectsNegativeFragIndex() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("k1", -1, true, "data".getBytes()));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void rejectsHugeFragIndex() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("k1", Integer.MAX_VALUE, true, "data".getBytes()));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void rejectsFragIndexAtMaxLimit() {
        FragmentReassembler r = new FragmentReassembler();
        int maxIdx = FragmentReassembler.MAX_FRAGMENTS_PER_GROUP - 1;
        assertNull(r.addFragment("k1", maxIdx, true, "data".getBytes()));
        assertEquals(1, r.pendingGroupCount());
    }

    @Test
    void rejectsFragIndexAboveMaxLimit() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("k1", FragmentReassembler.MAX_FRAGMENTS_PER_GROUP, true, "data".getBytes()));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void rejectsOversizedAssembly() {
        FragmentReassembler r = new FragmentReassembler();
        byte[] huge = new byte[(int) FragmentReassembler.MAX_ASSEMBLED_SIZE + 1];
        assertNull(r.addFragment("k1", 0, true, huge));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void duplicateFragmentDoesNotCorrupt() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("k1", 0, false, "Hello ".getBytes()));
        assertNull(r.addFragment("k1", 0, false, "HELLO ".getBytes()));
        byte[] result = r.addFragment("k1", 1, true, "World".getBytes());
        assertNotNull(result);
        assertEquals("Hello World", new String(result));
    }

    @Test
    void nullPayloadRejected() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("k1", 0, false, null));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void emptyPayloadRejected() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("k1", 0, false, new byte[0]));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void evictStaleRemovesOldGroups() {
        FragmentReassembler r = new FragmentReassembler();
        r.addFragment("k1", 0, false, "partial".getBytes());
        assertEquals(1, r.pendingGroupCount());
        r.evictStale();
        assertEquals(1, r.pendingGroupCount());
    }

    @Test
    void multipleGroupsReassembledIndependently() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("peerA:100", 0, false, "A0".getBytes()));
        assertNull(r.addFragment("peerB:200", 0, false, "B0".getBytes()));
        byte[] aResult = r.addFragment("peerA:100", 1, true, "A1".getBytes());
        assertNotNull(aResult);
        assertEquals("A0A1", new String(aResult));
        byte[] bResult = r.addFragment("peerB:200", 1, true, "B1".getBytes());
        assertNotNull(bResult);
        assertEquals("B0B1", new String(bResult));
        assertEquals(0, r.pendingGroupCount());
    }

    @Test
    void sameGroupIdFromDifferentSendersDoNotCollide() {
        FragmentReassembler r = new FragmentReassembler();
        assertNull(r.addFragment("127.0.0.1:1000:1", 0, false, "A0".getBytes()));
        assertNull(r.addFragment("127.0.0.1:2000:1", 0, false, "B0".getBytes()));
        byte[] aResult = r.addFragment("127.0.0.1:1000:1", 1, true, "A1".getBytes());
        assertNotNull(aResult);
        assertEquals("A0A1", new String(aResult));
        byte[] bResult = r.addFragment("127.0.0.1:2000:1", 1, true, "B1".getBytes());
        assertNotNull(bResult);
        assertEquals("B0B1", new String(bResult));
    }
}
