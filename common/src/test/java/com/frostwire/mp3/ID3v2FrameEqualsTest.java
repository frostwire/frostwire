/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * ID3v2Frame.equals compared this frame's unsynchronisation flag with the other frame's encryption
 * flag, so two frames that differed only in unsynchronisation were equal.
 */
class ID3v2FrameEqualsTest {

  /** An ID3v2.3 "TIT2" frame with two data bytes and the given second flags byte. */
  private static ID3v2Frame frame(int flags2) throws Exception {
    byte[] buffer = {'T', 'I', 'T', '2', 0, 0, 0, 2, 0, (byte) flags2, 0, 'a'};
    return new ID3v2Frame(buffer, 0);
  }

  @Test
  void framesThatDifferOnlyInUnsynchronisationAreNotEqual() throws Exception {
    ID3v2Frame plain = frame(0x00);
    ID3v2Frame unsynchronised = frame(0x02); // UNSYNCHRONISATION_BIT

    assertNotEquals(plain, unsynchronised);
    assertNotEquals(unsynchronised, plain);
  }

  @Test
  void identicalFramesAreEqualAndHashAlike() throws Exception {
    assertEquals(frame(0x02), frame(0x02));
    assertEquals(frame(0x02).hashCode(), frame(0x02).hashCode());
    assertNotEquals(frame(0x00), frame(0x04)); // ENCRYPTION_BIT
  }
}
