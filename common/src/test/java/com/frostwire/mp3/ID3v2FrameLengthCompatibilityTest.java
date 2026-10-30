/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compatibility fixtures for the frame-length check (frostwire/frostwire#1355).
 *
 * <p>The boundary the fuzzer input crossed: a frame's data length must fit in what is left of the
 * tag buffer. A frame ending exactly at the end of the buffer, in each frame-size encoding (v2.2
 * 24-bit, v2.3 32-bit, v2.4 synchsafe), must still decode to the same text; one byte more rejects
 * that frame (and only that frame), and a hostile length must be rejected before it is allocated.
 */
class ID3v2FrameLengthCompatibilityTest {

  /** Allocation allowed while rejecting a hostile length: kilobytes, not the advertised size. */
  private static final long ALLOCATION_BOUND = 64 * 1024;

  @Test
  void v23FrameEndingAtTheEndOfTheTagDecodes() throws Exception {
    byte[] tag = tag(3, frame23("TIT2", text(0, "Title")));
    assertEquals("Title", ID3v2TagFactory.createTag(tag).getTitle());
  }

  @Test
  void v24FrameWithASynchsafeLengthEndingAtTheEndOfTheTagDecodes() throws Exception {
    // 200 bytes of data: synchsafe 00 00 01 48, which a plain 32-bit read would take as 328.
    String title = "t".repeat(199);
    byte[] tag = tag(4, frame24("TIT2", text(3, title)));
    assertEquals(title, ID3v2TagFactory.createTag(tag).getTitle());
  }

  @Test
  void v22FrameEndingAtTheEndOfTheTagDecodes() throws Exception {
    byte[] tag = tag(2, frame22("TT2", text(0, "Title")));
    assertEquals("Title", ID3v2TagFactory.createTag(tag).getTitle());
  }

  @Test
  void aFrameHoldingOnlyItsEncodingByteDecodesToAnEmptyTitle() throws Exception {
    byte[] tag = tag(3, frame23("TIT2", new byte[] {0}));
    assertEquals("", ID3v2TagFactory.createTag(tag).getTitle());
  }

  @Test
  void severalFramesThenPaddingDecode() throws Exception {
    byte[] tag =
        tag(
            3,
            frame23("TPE1", text(0, "Artist")),
            frame23("TALB", text(1, "Álbum")),
            frame23("TIT2", text(0, "Title")),
            new byte[64]);
    AbstractID3v2Tag t = ID3v2TagFactory.createTag(tag);
    assertEquals("Artist", t.getArtist());
    assertEquals("Álbum", t.getAlbum());
    assertEquals("Title", t.getTitle());
  }

  @Test
  void aFrameOneBytePastTheEndOfTheTagIsInvalid() {
    // TIT2 claiming 7 bytes with 6 left.
    byte[] frame = HexFormat.of().parseHex("5449543200000007000000" + "5469746c65");
    assertThrows(InvalidDataException.class, () -> new ID3v2Frame(frame, 0));
    byte[] obsolete = HexFormat.of().parseHex("545432000007" + "005469746c65");
    assertThrows(InvalidDataException.class, () -> new ID3v2ObseleteFrame(obsolete, 0));
  }

  @Test
  void aFrameRunningPastTheTagIsDroppedAndTheFramesBeforeItAreKept() throws Exception {
    byte[] title = frame23("TIT2", text(0, "Title"));
    title[7]++; // one byte longer than the tag holds
    AbstractID3v2Tag t =
        ID3v2TagFactory.createTag(tag(3, frame23("TPE1", text(0, "Artist")), title));
    assertEquals("Artist", t.getArtist());
    assertNull(t.getTitle());
  }

  /** Frame size bytes of a TIT2 (TT2 for v2.2) claiming far more than a 30-byte tag holds. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "3:7fffffff", // 2 GiB
        "3:ffffffff", // negative
        "3:10000000", // 256 MiB
        "4:7f7f7f7f", // synchsafe 256 MiB
        "2:ffffff", // 16 MiB
      })
  void aHostileFrameLengthIsRejectedAfterOnlyKilobytesOfWork(String spec) {
    int version = spec.charAt(0) - '0';
    byte[] size = HexFormat.of().parseHex(spec.substring(2));
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    frame.writeBytes((version == 2 ? "TT2" : "TIT2").getBytes(StandardCharsets.ISO_8859_1));
    frame.writeBytes(size);
    if (version != 2) {
      frame.writeBytes(new byte[2]); // flags
    }
    frame.writeBytes(text(0, "Title"));
    byte[] tag = tag(version, frame.toByteArray(), new byte[8]);

    long before = allocatedBytes();
    Throwable thrown = null;
    AbstractID3v2Tag t = null;
    try {
      t = ID3v2TagFactory.createTag(tag);
    } catch (Throwable e) {
      thrown = e;
    }
    long allocated = allocatedBytes() - before;
    assertTrue(
        thrown == null
            || thrown instanceof InvalidDataException
            || thrown instanceof NoSuchTagException
            || thrown instanceof UnsupportedTagException,
        "undocumented " + thrown);
    if (t != null) {
      assertNull(t.getTitle());
    }
    assertTrue(allocated < ALLOCATION_BOUND, spec + " allocated " + allocated + " bytes");
  }

  private static long allocatedBytes() {
    return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
        .getThreadAllocatedBytes(Thread.currentThread().getId());
  }

  /** A whole tag: the 10-byte header with a synchsafe size, then {@code parts}. */
  static byte[] tag(int version, byte[]... parts) {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    for (byte[] p : parts) {
      body.writeBytes(p);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] {'I', 'D', '3', (byte) version, 0, 0});
    out.writeBytes(synchsafe(body.size()));
    out.writeBytes(body.toByteArray());
    return out.toByteArray();
  }

  /** An encoding byte followed by {@code s} in that encoding. */
  static byte[] text(int encoding, String s) {
    byte[] value =
        s.getBytes(
            switch (encoding) {
              case 0 -> StandardCharsets.ISO_8859_1;
              case 1 -> StandardCharsets.UTF_16; // with a byte order mark
              case 2 -> StandardCharsets.UTF_16BE;
              default -> StandardCharsets.UTF_8;
            });
    byte[] b = new byte[1 + value.length];
    b[0] = (byte) encoding;
    System.arraycopy(value, 0, b, 1, value.length);
    return b;
  }

  static byte[] frame23(String id, byte[] data) {
    return frame(
        id,
        new byte[] {
          (byte) (data.length >>> 24),
          (byte) (data.length >>> 16),
          (byte) (data.length >>> 8),
          (byte) data.length,
          0,
          0
        },
        data);
  }

  static byte[] frame24(String id, byte[] data) {
    byte[] size = synchsafe(data.length);
    return frame(id, new byte[] {size[0], size[1], size[2], size[3], 0, 0}, data);
  }

  static byte[] frame22(String id, byte[] data) {
    return frame(
        id,
        new byte[] {(byte) (data.length >>> 16), (byte) (data.length >>> 8), (byte) data.length},
        data);
  }

  private static byte[] frame(String id, byte[] header, byte[] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(id.getBytes(StandardCharsets.ISO_8859_1));
    out.writeBytes(header);
    out.writeBytes(data);
    return out.toByteArray();
  }

  private static byte[] synchsafe(int n) {
    return new byte[] {
      (byte) ((n >> 21) & 0x7f),
      (byte) ((n >> 14) & 0x7f),
      (byte) ((n >> 7) & 0x7f),
      (byte) (n & 0x7f)
    };
  }
}
