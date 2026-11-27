/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ByteArrayBufferTest {

  @Test
  void appendsBytesAndGrows() {
    ByteArrayBuffer buf = new ByteArrayBuffer(2);
    assertTrue(buf.isEmpty());
    assertEquals(2, buf.capacity());
    buf.append(new byte[] {1, 2, 3, 4, 5}, 1, 3);
    assertEquals(3, buf.length());
    assertTrue(buf.capacity() >= 3);
    assertArrayEquals(new byte[] {2, 3, 4}, buf.toByteArray());
    buf.append(7);
    buf.append(0x1FF); // truncated to the low byte
    assertArrayEquals(new byte[] {2, 3, 4, 7, (byte) 0xFF}, buf.toByteArray());
    assertEquals(-1, buf.byteAt(4));
    assertFalse(buf.isEmpty());
    buf.append((byte[]) null, 0, 0);
    buf.append(new byte[] {9}, 0, 0);
    assertEquals(5, buf.length());
  }

  @Test
  void appendsCharsAsLowBytes() {
    ByteArrayBuffer buf = new ByteArrayBuffer(1);
    buf.append("hello".toCharArray(), 1, 3);
    assertArrayEquals("ell".getBytes(), buf.toByteArray());
    buf.append((char[]) null, 0, 1);
    buf.append(new char[] {'x'}, 0, 0);
    assertEquals(3, buf.length());
  }

  @Test
  void rejectsBadRanges() {
    ByteArrayBuffer buf = new ByteArrayBuffer(4);
    byte[] b = new byte[3];
    assertThrows(IndexOutOfBoundsException.class, () -> buf.append(b, -1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> buf.append(b, 4, 0));
    assertThrows(IndexOutOfBoundsException.class, () -> buf.append(b, 0, -1));
    assertThrows(IndexOutOfBoundsException.class, () -> buf.append(b, 2, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> buf.append(b, Integer.MAX_VALUE, 2));
    char[] c = new char[3];
    assertThrows(IndexOutOfBoundsException.class, () -> buf.append(c, -1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> buf.append(c, 2, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> buf.setLength(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> buf.setLength(5));
  }

  @Test
  void lengthCapacityAndClear() {
    ByteArrayBuffer buf = new ByteArrayBuffer(4);
    buf.append(new byte[] {1, 2, 3, 4}, 0, 4);
    assertTrue(buf.isFull());
    buf.setLength(2);
    assertEquals(2, buf.length());
    assertFalse(buf.isFull());
    assertEquals(4, buf.buffer().length);
    buf.ensureCapacity(0);
    buf.ensureCapacity(1);
    assertEquals(4, buf.capacity());
    buf.ensureCapacity(10);
    assertTrue(buf.capacity() >= 12);
    assertArrayEquals(new byte[] {1, 2}, buf.toByteArray());
    buf.clear();
    assertEquals(0, buf.length());
    assertArrayEquals(new byte[0], buf.toByteArray());
  }

  @Test
  void searchesForBytes() {
    ByteArrayBuffer buf = new ByteArrayBuffer(8);
    buf.append(new byte[] {5, 6, 7, 6, 8}, 0, 5);
    assertEquals(1, buf.indexOf((byte) 6));
    assertEquals(3, buf.indexOf((byte) 6, 2, 5));
    assertEquals(-1, buf.indexOf((byte) 6, 4, 5));
    assertEquals(-1, buf.indexOf((byte) 9));
    assertEquals(0, buf.indexOf((byte) 5, -10, 100));
    assertEquals(-1, buf.indexOf((byte) 5, 3, 2));
    assertEquals(-1, buf.indexOf((byte) 8, 0, 4));
  }
}
