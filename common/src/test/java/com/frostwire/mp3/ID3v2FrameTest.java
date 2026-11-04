/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static com.frostwire.mp3.Id3Bytes.bytes;
import static com.frostwire.mp3.Id3Bytes.v22Frame;
import static com.frostwire.mp3.Id3Bytes.v23Frame;
import static com.frostwire.mp3.Id3Bytes.v24Frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  /**
   * The status flags (keep with the tag, keep with the file, read only) and the format flags
   * (grouping, compression, encryption, unsynchronisation, data length indicator) are read and
   * written back as they were.
   */
  @Test
  void flagsRoundTrip() throws Exception {
    byte[] b = v23Frame("TIT2", 0x70, 0x4f, bytes(0, "Title"));
    ID3v2Frame frame = new ID3v2Frame(b, 0);
    assertEquals("TIT2", frame.getId());
    assertTrue(frame.hasPreserveTag());
    assertTrue(frame.hasPreserveFile());
    assertTrue(frame.isReadOnly());
    assertTrue(frame.hasGroup());
    assertTrue(frame.hasCompression());
    assertTrue(frame.hasEncryption());
    assertTrue(frame.hasUnsynchronisation());
    assertTrue(frame.hasDataLengthIndicator());
    assertArrayEquals(b, frame.toBytes());

    ID3v2Frame plain = new ID3v2Frame("TIT2", bytes(0, "Title"));
    assertFalse(plain.hasPreserveTag());
    assertFalse(plain.hasDataLengthIndicator());
    assertArrayEquals(v23Frame("TIT2", bytes(0, "Title")), plain.toBytes());
  }

  @Test
  void framesAreReadAtTheirOffset() throws Exception {
    byte[] b = bytes("xyz", v23Frame("TPE1", bytes(0, "Artist")));
    ID3v2Frame frame = new ID3v2Frame(b, 3);
    assertEquals("TPE1", frame.getId());
    assertEquals(7, frame.getDataLength());
    assertEquals(17, frame.getLength());
    assertArrayEquals(bytes(0, "Artist"), frame.getData());
  }

  @ParameterizedTest
  @CsvSource({"TIT2, true", "TXX1, true", "tit2, false", "TI T, false", "TIT-, false"})
  void frameIdsAreUpperCaseLettersAndDigits(String id, boolean valid) {
    byte[] b = v23Frame(id, bytes(0, "x"));
    if (valid) {
      assertEquals(id, assertDoesNotThrow(() -> new ID3v2Frame(b, 0)).getId());
    } else {
      assertThrows(InvalidDataException.class, () -> new ID3v2Frame(b, 0));
    }
  }

  @Test
  void setDataUpdatesTheLength() {
    ID3v2Frame frame = new ID3v2Frame("TIT2", bytes(0, "Title"));
    frame.setData(bytes(0, "T"));
    assertEquals(2, frame.getDataLength());
    assertEquals(12, frame.getLength());
    frame.setData(null);
    assertNull(frame.getData());
    assertEquals(0, frame.getDataLength());
  }

  @Test
  void framesDifferingInIdOrDataAreNotEqual() throws Exception {
    ID3v2Frame frame = new ID3v2Frame("TIT2", bytes(0, "Title"));
    assertEquals(frame, frame);
    assertEquals(frame, new ID3v2Frame(v23Frame("TIT2", bytes(0, "Title")), 0));
    assertEquals(frame.hashCode(), new ID3v2Frame("TIT2", bytes(0, "Title")).hashCode());
    assertNotEquals(frame, new ID3v2Frame("TPE1", bytes(0, "Title")));
    assertNotEquals(frame, new ID3v2Frame("TIT2", bytes(0, "Other")));
    assertNotEquals(frame, new ID3v2Frame("TIT2", bytes(0, "Title2")));
    assertNotEquals(frame, "TIT2");
    for (int flags1 : new int[] {0x40, 0x20, 0x10}) {
      assertNotEquals(frame, new ID3v2Frame(v23Frame("TIT2", flags1, 0, bytes(0, "Title")), 0));
    }
    for (int flags2 : new int[] {0x40, 0x08, 0x01}) {
      assertNotEquals(frame, new ID3v2Frame(v23Frame("TIT2", 0, flags2, bytes(0, "Title")), 0));
    }
    ID3v2Frame noData = new ID3v2Frame("TIT2", new byte[0]);
    noData.setData(null);
    ID3v2Frame emptyData = new ID3v2Frame("TIT2", new byte[0]);
    assertNotEquals(noData, emptyData);
    assertNotEquals(emptyData, noData);
    ID3v2Frame noId = new ID3v2Frame(null, new byte[0]);
    assertNotEquals(noId, emptyData);
    assertNotEquals(emptyData, noId);
    assertEquals(noId, new ID3v2Frame(null, new byte[0]));
  }

  /** v2.4 frame sizes are synchsafe: 200 is 0x01 0x48, not 0x00 0xc8. */
  @Test
  void v24FrameSizesAreSynchsafe() throws Exception {
    byte[] data = new byte[200];
    byte[] b = v24Frame("APIC", data);
    assertArrayEquals(bytes("APIC", 0, 0, 1, 0x48, 0, 0), Arrays.copyOf(b, 10));
    ID3v24Frame frame = new ID3v24Frame(b, 0);
    assertEquals(200, frame.getDataLength());
    assertArrayEquals(b, frame.toBytes());
    assertArrayEquals(b, new ID3v24Frame("APIC", data).toBytes());
    assertEquals(frame, new ID3v24Frame("APIC", data));
    // A v2.3 frame with the same content is not a v2.4 frame.
    assertNotEquals(frame, new ID3v2Frame("APIC", data));
  }

  /** v2.2 frames have a three-letter ID and a three-byte size, and can't be written. */
  @Test
  void v22FramesAreReadOnly() throws Exception {
    byte[] b = bytes("xx", v22Frame("TT2", bytes(0, "Title")));
    ID3v2ObseleteFrame frame = new ID3v2ObseleteFrame(b, 2);
    assertEquals("TT2", frame.getId());
    assertEquals(6, frame.getDataLength());
    assertEquals(12, frame.getLength());
    assertArrayEquals(bytes(0, "Title"), frame.getData());
    assertThrows(NotSupportedException.class, frame::toBytes);
    assertThrows(
        NotSupportedException.class,
        () -> new ID3v2ObseleteFrame("TT2", bytes(0, "T")).toBytes(new byte[20], 0));
  }

  @Test
  void aV22FrameHeaderPastTheEndIsInvalid() {
    assertThrows(InvalidDataException.class, () -> new ID3v2ObseleteFrame(bytes("TT2", 0, 0), 0));
    assertThrows(InvalidDataException.class, () -> new ID3v2Frame(bytes("TIT2", 0, 0, 0), 0));
    assertThrows(
        InvalidDataException.class, () -> new ID3v2Frame(v23Frame("TIT2", bytes(0, "T")), -1));
  }

  @Test
  void frameSetsHoldTheFramesOfOneId() {
    ID3v2FrameSet set = new ID3v2FrameSet("COMM");
    set.addFrame(new ID3v2Frame("COMM", bytes(0, "eng", 0, "a")));
    set.addFrame(new ID3v2Frame("COMM", bytes(0, "eng", 0, "b")));
    assertEquals("COMM", set.getId());
    assertEquals(2, set.getFrames().size());
    assertEquals("COMM: 2", set.toString());

    ID3v2FrameSet same = new ID3v2FrameSet("COMM");
    same.addFrame(new ID3v2Frame("COMM", bytes(0, "eng", 0, "a")));
    same.addFrame(new ID3v2Frame("COMM", bytes(0, "eng", 0, "b")));
    assertEquals(set, same);
    assertEquals(set, set);
    assertEquals(set.hashCode(), same.hashCode());
    assertNotEquals(set, new ID3v2FrameSet("TIT2"));
    assertNotEquals(set, new ID3v2FrameSet(null));
    assertNotEquals(new ID3v2FrameSet(null), set);
    assertEquals(new ID3v2FrameSet(null), new ID3v2FrameSet(null));
    assertNotEquals(set, "COMM");

    same.clear();
    assertEquals(0, same.getFrames().size());
    assertNotEquals(set, same);
  }
}
