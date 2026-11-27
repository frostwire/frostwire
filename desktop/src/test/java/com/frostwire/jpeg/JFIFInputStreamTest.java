/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.jpeg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class JFIFInputStreamTest {

  private static JFIFInputStream stream(int... bytes) {
    byte[] b = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) b[i] = (byte) bytes[i];
    return new JFIFInputStream(new ByteArrayInputStream(b));
  }

  /**
   * SOI, APP0 (4 payload bytes), COM (3 payload bytes), SOS (2 payload bytes), entropy-coded data
   * with a stuffed 0xff, then EOI.
   */
  private static JFIFInputStream sampleJpeg() {
    return stream(
        0xff, 0xd8, //
        0xff, 0xe0, 0x00, 0x06, 1, 2, 3, 4, //
        0xff, 0xfe, 0x00, 0x05, 'a', 'b', 'c', //
        0xff, 0xda, 0x00, 0x04, 9, 9, //
        0x11, 0xff, 0x00, 0x22, //
        0xff, 0xd9);
  }

  @Test
  void walksMarkersSegmentsAndEntropyCodedData() throws IOException {
    JFIFInputStream in = sampleJpeg();
    JFIFInputStream.Segment soi = in.getNextSegment();
    assertEquals(0xffd8, soi.marker);
    assertEquals(-1, soi.length);

    JFIFInputStream.Segment app0 = in.getNextSegment();
    assertEquals(0xffe0, app0.marker);
    assertEquals(4, app0.length);
    assertFalse(app0.isEntropyCoded());
    byte[] payload = new byte[10];
    assertEquals(4, in.read(payload, 0, 10));
    assertArrayEquals(new byte[] {1, 2, 3, 4}, java.util.Arrays.copyOf(payload, 4));
    assertEquals(-1, in.read(payload, 0, 10));

    JFIFInputStream.Segment com = in.getNextSegment();
    assertEquals(0xfffe, com.marker);
    assertEquals(3, com.length);
    assertEquals(2, in.skip(2));
    assertEquals('c', in.read() & 0xff);

    JFIFInputStream.Segment sos = in.getNextSegment();
    assertEquals(0xffda, sos.marker);
    assertEquals(2, sos.length);

    // the scan data follows the SOS header as one entropy-coded segment
    JFIFInputStream.Segment scan = in.getNextSegment();
    assertTrue(scan.isEntropyCoded());
    assertEquals(0x11, in.read());
    assertEquals(0xff, in.read()); // 0xff 0x00 is a stuffed 0xff
    assertEquals(0x22, in.read());
    assertEquals(-1, in.read()); // the next marker ends the segment
    assertEquals(-1, in.read(payload, 0, 4));
    assertEquals(-1, in.skip(5));

    JFIFInputStream.Segment eoi = in.getNextSegment();
    assertEquals(0xffd9, eoi.marker);
    assertNull(in.getNextSegment());
    assertTrue(eoi.toString().startsWith("Segment marker=0xffd9"));
  }

  @Test
  void entropyCodedBulkReadsAndSkips() throws IOException {
    JFIFInputStream in = stream(0xff, 0xda, 0x00, 0x02, 1, 2, 0xff, 0x00, 3, 4, 5, 0xff, 0xd9);
    assertEquals(0xffda, in.getNextSegment().marker);
    assertTrue(in.getNextSegment().isEntropyCoded());
    byte[] buf = new byte[3];
    assertEquals(3, in.read(buf, 0, 3));
    assertArrayEquals(new byte[] {1, 2, (byte) 0xff}, buf);
    assertEquals(1, in.skip(1));
    byte[] rest = new byte[8];
    assertEquals(2, in.read(rest, 0, 8));
    assertEquals(4, rest[0]);
    assertEquals(5, rest[1]);
    assertEquals(0xffd9, in.getNextSegment().marker);
  }

  @Test
  void restartMarkersAreStandalone() throws IOException {
    JFIFInputStream in = stream(0xff, 0xd0, 0xff, 0xd7, 0xff, 0xd9);
    assertEquals(0xffd0, in.getNextSegment().marker);
    assertEquals(0xffd7, in.getNextSegment().marker);
    assertEquals(0xffd9, in.getNextSegment().marker);
    assertNull(in.getNextSegment());
  }

  @Test
  void fillBytesAndStuffingBetweenMarkersAreSkipped() throws IOException {
    JFIFInputStream in = stream(0x12, 0x34, 0xff, 0x00, 0xff, 0xff, 0xd9);
    assertEquals(0xffd9, in.getNextSegment().marker);
  }

  @Test
  void illegalSegmentLengthsAreRejected() {
    JFIFInputStream tooShort = stream(0xff, 0xe0, 0x00, 0x01);
    assertThrows(IOException.class, tooShort::getNextSegment);
    JFIFInputStream tooLong = stream(0xff, 0xe0, 0xff, 0xff);
    assertThrows(IOException.class, tooLong::getNextSegment);
  }

  @Test
  void truncatedStreamsEndWithNoSegment() throws IOException {
    assertNull(stream().getNextSegment());
    assertNull(stream(0xff).getNextSegment());
    assertNull(stream(1, 2, 3).getNextSegment());
  }

  @Test
  void markingIsUnsupported() throws IOException {
    JFIFInputStream in = sampleJpeg();
    assertFalse(in.markSupported());
    in.mark(10);
    assertThrows(IOException.class, in::reset);
    assertNotNull(in.getNextSegment());
  }
}
