/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static com.frostwire.mp3.Id3Bytes.bytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** ID3v2 frames: a header (ID, size, flags) and the frame's data. */
class ID3v2FrameTest {

  /** packFrame wrote the frame header at the start of the buffer, whatever the offset. */
  @Test
  void framesPackAtTheGivenOffset() throws Exception {
    ID3v2Frame frame = new ID3v2Frame("TIT2", bytes(0, "Title"));
    byte[] buffer = new byte[3 + frame.getLength()];
    frame.toBytes(buffer, 3);
    assertArrayEquals(bytes(0, 0, 0, frame.toBytes()), buffer);
  }
}
