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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.LinkedList;
import org.junit.jupiter.api.Test;

final class AppleBoxTest {

  private static byte[] dataBox(String type, int dataType, byte[] payload) {
    ByteBuffer b = ByteBuffer.allocate(8 + 16 + payload.length);
    b.putInt(8 + 16 + payload.length);
    b.putInt(Bits.make4cc(type));
    b.putInt(16 + payload.length);
    b.putInt(Box.data);
    b.putInt(dataType);
    b.putInt(0); // country and language
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

  @Test
  void coverArtRoundTripsAndExposesItsBytes() throws IOException {
    byte[] jpg = {(byte) 0xff, (byte) 0xd8, 1, 2, 3, (byte) 0xff, (byte) 0xd9};
    byte[] file = dataBox("covr", 13, jpg);
    LinkedList<Box> boxes = parse(file);
    AppleCoverBox covr = assertInstanceOf(AppleCoverBox.class, boxes.getFirst());
    assertArrayEquals(jpg, covr.value());
    assertEquals(13, covr.dataType());
    assertArrayEquals(file, write(boxes));
  }

  @Test
  void coverArtSettersChooseTheImageType() throws IOException {
    AppleCoverBox covr = new AppleCoverBox();
    assertNull(covr.value());
    covr.setPng(new byte[] {1, 2, 3});
    assertEquals(14, covr.dataType());
    covr.setJpg(new byte[] {4, 5});
    assertEquals(13, covr.dataType());
    LinkedList<Box> boxes = new LinkedList<>();
    boxes.add(covr);
    covr.update();
    covr.size = (int) covr.length() + 8;
    byte[] written = write(boxes);
    AppleCoverBox again = assertInstanceOf(AppleCoverBox.class, parse(written).getFirst());
    assertArrayEquals(new byte[] {4, 5}, again.value());
  }

  @Test
  void integerBoxesKeepFullWidthValues() throws IOException {
    AppleIntegerBox box = new AppleIntegerBox(Box.trkn);
    assertEquals(15, box.dataType());
    assertEquals(0, box.value());
    box.value(123456789);
    assertEquals(123456789, box.value());
    box.update();
    box.size = (int) box.length() + 8;
    LinkedList<Box> boxes = new LinkedList<>();
    boxes.add(box);
    byte[] written = write(boxes);
    assertEquals(8 + 16 + 4, written.length);
    // a 4-byte payload read back through the box factory
    LinkedList<Box> parsed = parse(dataBox("stik", 15, new byte[] {0, 0, 0, 9}));
    assertEquals(9, assertInstanceOf(AppleIntegerBox.class, parsed.getFirst()).value());
  }

  @Test
  void audioAndVideoInfoCarryTheirBrands() {
    byte[] jpg = {1};
    Mp4Info audio = Mp4Info.audio("t", "a", "al", jpg);
    assertEquals(Box.M4A_, audio.majorBrand);
    assertArrayEquals(new int[] {Box.M4A_, Box.mp42, Box.isom, Box.zero}, audio.compatibleBrands);
    assertEquals("t", audio.title);
    assertEquals("a", audio.author);
    assertEquals("al", audio.album);
    assertArrayEquals(jpg, audio.jpg);
    Mp4Info video = Mp4Info.avc("t", "a", "al", null);
    assertEquals(Box.MP4_, video.majorBrand);
    assertArrayEquals(new int[] {Box.iso6, Box.avc1, Box.mp41, Box.zero}, video.compatibleBrands);
    assertNull(video.jpg);
  }
}
