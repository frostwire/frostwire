/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.udp;

import static com.frostwire.search.relay.icebridge.udp.FragmentReassembler.State.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FragmentReassemblerTest {
  @Test
  void reassemblesInOrderAndRetainsCompletionUntilRelease() {
    FragmentReassembler r = new FragmentReassembler();
    assertEquals(RETAINED, r.accept("a:1", 0, 2, false, new byte[] {1}).state);
    FragmentReassembler.Result complete = r.accept("a:1", 1, 2, true, new byte[] {2});
    assertEquals(COMPLETE, complete.state);
    assertArrayEquals(new byte[] {1, 2}, complete.payload);
    assertArrayEquals(complete.payload, r.accept("a:1", 1, 2, true, new byte[] {2}).payload);
    assertEquals(1, r.pendingGroupCount());
    r.release("a:1");
    assertEquals(0, r.pendingGroupCount());
  }

  @Test
  void reassemblesOutOfOrder() {
    FragmentReassembler r = new FragmentReassembler();
    assertEquals(RETAINED, r.accept("a", 1, 2, true, new byte[] {2}).state);
    assertArrayEquals(new byte[] {1, 2}, r.accept("a", 0, 2, false, new byte[] {1}).payload);
  }

  @Test
  void rejectsInvalidIndicesTotalsShapesAndPayloadsWithoutAllocation() {
    FragmentReassembler r = new FragmentReassembler();
    for (int index : new int[] {-1, 256, Integer.MAX_VALUE}) {
      assertEquals(REJECTED, r.accept("a", index, 256, true, new byte[] {1}).state);
    }
    for (int total : new int[] {-1, 0, 257, Integer.MAX_VALUE}) {
      assertEquals(REJECTED, r.accept("a", 0, total, true, new byte[] {1}).state);
    }
    assertEquals(REJECTED, r.accept("a", 0, 2, true, new byte[] {1}).state);
    assertEquals(REJECTED, r.accept("a", 0, 1, false, new byte[] {1}).state);
    assertEquals(REJECTED, r.accept("a", 0, 1, true, null).state);
    assertEquals(REJECTED, r.accept("a", 0, 1, true, new byte[0]).state);
    assertEquals(REJECTED, r.accept("a", 0, 1, true, new byte[1025]).state);
    assertEquals(
        REJECTED,
        r.accept("a", 0, 1, true, new byte[(int) FragmentReassembler.MAX_ASSEMBLED_SIZE + 1])
            .state);
    assertEquals(0, r.pendingGroupCount());
    assertEquals(RETAINED, r.accept("a", 255, 256, true, new byte[] {1}).state);
  }

  @Test
  void duplicateAndConflictingFragmentsDoNotCorruptAcceptedPrefix() {
    FragmentReassembler r = new FragmentReassembler();
    assertEquals(RETAINED, r.accept("a", 0, 2, false, new byte[] {1}).state);
    assertEquals(RETAINED, r.accept("a", 0, 2, false, new byte[] {1}).state);
    assertEquals(REJECTED, r.accept("a", 0, 2, false, new byte[] {9}).state);
    assertEquals(REJECTED, r.accept("a", 1, 3, false, new byte[] {2}).state);
    assertArrayEquals(new byte[] {1, 2}, r.accept("a", 1, 2, true, new byte[] {2}).payload);
  }

  @Test
  void independentSessionsAndGroupsNeverCollide() {
    FragmentReassembler r = new FragmentReassembler();
    assertEquals(RETAINED, r.accept("a:1", 0, 2, false, new byte[] {1}).state);
    assertEquals(RETAINED, r.accept("b:1", 0, 2, false, new byte[] {2}).state);
    assertArrayEquals(new byte[] {1, 3}, r.accept("a:1", 1, 2, true, new byte[] {3}).payload);
    assertArrayEquals(new byte[] {2, 4}, r.accept("b:1", 1, 2, true, new byte[] {4}).payload);
    assertFalse(r.hasExpired("a:"));
    r.removeSession("a:");
    assertEquals(1, r.pendingGroupCount());
    r.removeSession("b:");
    assertEquals(0, r.pendingGroupCount());
  }

  @Test
  void groupCapacityRejectsWithoutEvictingAcceptedWork() {
    FragmentReassembler r = new FragmentReassembler();
    for (int i = 0; i < 64; i++) {
      assertEquals(RETAINED, r.accept(i + ":1", 0, 2, false, new byte[] {1}).state);
    }
    assertEquals(REJECTED, r.accept("extra", 0, 2, false, new byte[] {1}).state);
    assertEquals(64, r.pendingGroupCount());
    assertArrayEquals(new byte[] {1, 2}, r.accept("0:1", 1, 2, true, new byte[] {2}).payload);
    r.release("0:1");
    assertEquals(RETAINED, r.accept("extra", 0, 2, false, new byte[] {1}).state);
  }
}
