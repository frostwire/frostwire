/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.LinkedList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Field widths, signedness and lengths of tfhd, stz2, tfdt and Apple integer boxes. */
final class BoxFieldWidthRegressionTest {

  private static byte[] fullBox(String type, int versionAndFlags, byte[] payload) {
    ByteBuffer b = ByteBuffer.allocate(12 + payload.length);
    b.putInt(12 + payload.length);
    b.putInt(Bits.make4cc(type));
    b.putInt(versionAndFlags);
    b.put(payload);
    return b.array();
  }

  private static byte[] dataBox(String type, int dataType, byte[] payload) {
    ByteBuffer b = ByteBuffer.allocate(8 + 16 + payload.length);
    b.putInt(8 + 16 + payload.length);
    b.putInt(Bits.make4cc(type));
    b.putInt(16 + payload.length);
    b.putInt(Box.data);
    b.putInt(dataType);
    b.putInt(0);
    b.put(payload);
    return b.array();
  }

  private static LinkedList<Box> parse(byte[] file) throws IOException {
    LinkedList<Box> top = new LinkedList<>();
    IsoMedia.read(
        new InputChannel(Channels.newChannel(new ByteArrayInputStream(file)), file.length),
        ByteBuffer.allocate(4096),
        b -> {
          if (b.parent == null) top.add(b);
          return true;
        });
    return top;
  }

  private static byte[] write(LinkedList<Box> boxes) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Box b : boxes) b.update();
    IsoMedia.write(
        new OutputChannel(Channels.newChannel(out)), boxes, ByteBuffer.allocate(4096), null);
    return out.toByteArray();
  }

  private static void assertRoundTrips(byte[] file) throws IOException {
    assertArrayEquals(file, write(parse(file)));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x1, 0x2, 0x8, 0x10, 0x20, 0x3b, 0x01003b})
  void tfhdWithOptionalFieldsRoundTrips(int flags) throws IOException {
    ByteBuffer p = ByteBuffer.allocate(4 + 8 + 4 * 4);
    p.putInt(7);
    if ((flags & 0x1) != 0) p.putLong(0x1000L);
    if ((flags & 0x2) != 0) p.putInt(1);
    if ((flags & 0x8) != 0) p.putInt(1024);
    if ((flags & 0x10) != 0) p.putInt(4096);
    if ((flags & 0x20) != 0) p.putInt(0x01010000);
    byte[] payload = new byte[p.position()];
    p.flip();
    p.get(payload);
    assertRoundTrips(fullBox("tfhd", flags, payload));
  }

  @Test
  void stz2EightBitSizesAreUnsignedAndRoundTrip() throws IOException {
    byte[] payload = {0, 0, 0, 8, 0, 0, 0, 3, 10, (byte) 200, (byte) 255};
    byte[] file = fullBox("stz2", 0, payload);
    CompactSampleSizeBox stz2 =
        assertInstanceOf(CompactSampleSizeBox.class, parse(file).getFirst());
    assertEquals(200, stz2.entries[1].entry_size);
    assertEquals(255, stz2.entries[2].entry_size);
    assertRoundTrips(file);
  }

  @Test
  void stz2SixteenBitSizesAreUnsignedAndRoundTrip() throws IOException {
    byte[] payload = {0, 0, 0, 16, 0, 0, 0, 2, (byte) 0x80, 0, (byte) 0xff, (byte) 0xff};
    byte[] file = fullBox("stz2", 0, payload);
    CompactSampleSizeBox stz2 =
        assertInstanceOf(CompactSampleSizeBox.class, parse(file).getFirst());
    assertEquals(32768, stz2.entries[0].entry_size);
    assertEquals(65535, stz2.entries[1].entry_size);
    assertRoundTrips(file);
  }

  @Test
  void tfdtVersionZeroTimeIsUnsigned() throws IOException {
    byte[] file = fullBox("tfdt", 0, new byte[] {(byte) 0xf0, 0, 0, 1});
    TrackFragmentBaseMediaDecodeTimeBox tfdt =
        assertInstanceOf(TrackFragmentBaseMediaDecodeTimeBox.class, parse(file).getFirst());
    assertEquals(0xf0000001L, tfdt.base_media_decode_time);
    assertRoundTrips(file);
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        0,
        5,
        127,
        -1,
        -5,
        -128,
        128,
        300,
        32767,
        -129,
        -32768,
        32768,
        70000,
        8388607,
        -32769,
        -8388608,
        -8000000,
        8388608,
        123456789,
        -123456789,
        Integer.MIN_VALUE,
        Integer.MAX_VALUE
      })
  void appleIntegerSetterAndGetterAgree(int v) throws IOException {
    AppleIntegerBox box = new AppleIntegerBox(Box.stik);
    box.value(v);
    assertEquals(v, box.value());
    LinkedList<Box> boxes = new LinkedList<>();
    boxes.add(box);
    box.update();
    box.size = (int) box.length() + 8;
    AppleIntegerBox again = assertInstanceOf(AppleIntegerBox.class, parse(write(boxes)).getFirst());
    assertEquals(v, again.value());
  }

  @Test
  void appleIntegerUsesTheNarrowestPayload() {
    int[][] cases = {
      {5, 1}, {-128, 1}, {128, 2}, {-32768, 2}, {32768, 3}, {-8388608, 3}, {8388608, 4}
    };
    for (int[] c : cases) {
      AppleIntegerBox box = new AppleIntegerBox(Box.stik);
      box.value(c[0]);
      assertEquals(c[1] + 16, box.dataLength, "value " + c[0]);
    }
  }

  @Test
  void appleIntegerShortPayloadsReadBigEndianSigned() throws IOException {
    assertEquals(
        5, ((AppleIntegerBox) parse(dataBox("stik", 15, new byte[] {5})).getFirst()).value());
    assertEquals(
        -2,
        ((AppleIntegerBox)
                parse(dataBox("stik", 15, new byte[] {(byte) 0xff, (byte) 0xfe})).getFirst())
            .value());
    assertEquals(
        0x010203,
        ((AppleIntegerBox) parse(dataBox("stik", 15, new byte[] {1, 2, 3})).getFirst()).value());
  }
}
