/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.LinkedList;
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
}
