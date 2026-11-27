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
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Boxes that no sample file carries: parse hand-built bytes, write them back, compare. */
final class BoxRoundTripTest {

  private static byte[] bytes(Object... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Object p : parts) {
      if (p instanceof Integer) {
        out.writeBytes(ByteBuffer.allocate(4).putInt((Integer) p).array());
      } else if (p instanceof Long) {
        out.writeBytes(ByteBuffer.allocate(8).putLong((Long) p).array());
      } else if (p instanceof Short) {
        out.writeBytes(ByteBuffer.allocate(2).putShort((Short) p).array());
      } else if (p instanceof Byte) {
        out.write((Byte) p);
      } else if (p instanceof String) {
        out.writeBytes(((String) p).getBytes(StandardCharsets.UTF_8));
        out.write(0);
      } else {
        out.writeBytes((byte[]) p);
      }
    }
    return out.toByteArray();
  }

  private static byte[] box(String type, byte[] payload) {
    return bytes(8 + payload.length, Bits.make4cc(type), payload);
  }

  private static byte[] fullBox(String type, int versionAndFlags, byte[] payload) {
    return box(type, bytes(versionAndFlags, payload));
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
    IsoMedia.write(
        new OutputChannel(Channels.newChannel(out)), boxes, ByteBuffer.allocate(4096), null);
    return out.toByteArray();
  }

  static Stream<Object[]> cases() {
    return Stream.of(
        new Object[] {"urn name and location", fullBox("urn ", 0, bytes("name", "http://x/y"))},
        new Object[] {"urn name only", fullBox("urn ", 0, bytes("name"))},
        new Object[] {"urn empty", fullBox("urn ", 0, new byte[0])},
        new Object[] {"url with location", fullBox("url ", 0, bytes("http://example.test/a"))},
        new Object[] {"url self contained", fullBox("url ", 1, new byte[0])},
        new Object[] {
          "hmhd", fullBox("hmhd", 0, bytes((short) 1500, (short) 900, 64000, 32000, 0))
        },
        new Object[] {"nmhd", fullBox("nmhd", 0, new byte[0])},
        new Object[] {"stsh", fullBox("stsh", 0, bytes(2, 5, 1, 9, 4))},
        new Object[] {"stsh empty", fullBox("stsh", 0, bytes(0))},
        new Object[] {"sbgp", fullBox("sbgp", 0, bytes(Bits.make4cc("roll"), 2, 10, 1, 20, 2))},
        new Object[] {"co64", fullBox("co64", 0, bytes(2, 0x1_0000_0000L, 0x2_0000_0000L))},
        new Object[] {"tfdt v0", fullBox("tfdt", 0, bytes(123456))},
        new Object[] {"tfdt v1", fullBox("tfdt", 0x01000000, bytes(0x1_2345_6789L))},
        new Object[] {"tfhd track only", fullBox("tfhd", 0x010000, bytes(7))});
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void parsedBoxesWriteBackIdentically(String name, byte[] file) throws IOException {
    LinkedList<Box> boxes = parse(file);
    assertEquals(1, boxes.size());
    for (Box b : boxes) b.update();
    assertArrayEquals(file, write(boxes), name);
  }

  @Test
  void parsedFieldsAreExposed() throws IOException {
    DataEntryUrnBox urn =
        assertInstanceOf(
            DataEntryUrnBox.class,
            parse(fullBox("urn ", 0, bytes("name", "http://x/y"))).getFirst());
    assertEquals("name", urn.name());
    assertEquals("http://x/y", urn.location());
    DataEntryUrlBox url =
        assertInstanceOf(
            DataEntryUrlBox.class, parse(fullBox("url ", 0, bytes("http://z"))).getFirst());
    assertEquals("http://z", url.location());

    TrackFragmentHeaderBox tfhd =
        assertInstanceOf(
            TrackFragmentHeaderBox.class,
            parse(fullBox("tfhd", 0x3b, bytes(7, 0x1000L, 1, 1024, 4096, 0x01010000))).getFirst());
    assertEquals(7, tfhd.track_ID);
    assertEquals(0x1000L, tfhd.base_data_offset);
    assertEquals(1, tfhd.sample_description_index);
    assertEquals(1024, tfhd.default_sample_duration);
    assertEquals(4096, tfhd.default_sample_size);
    assertEquals(0x01010000, tfhd.default_sample_flags);
    assertEquals(true, tfhd.baseDataOffsetPresent());
    assertEquals(true, tfhd.sampleDescriptionIndexPresent());
    assertEquals(true, tfhd.defaultSampleDurationPresent());
    assertEquals(true, tfhd.defaultSampleSizePresent());
    assertEquals(true, tfhd.defaultSampleFlagsPresent());
    assertEquals(false, tfhd.durationIsEmpty());
    TrackFragmentHeaderBox bare =
        assertInstanceOf(
            TrackFragmentHeaderBox.class, parse(fullBox("tfhd", 0x010000, bytes(7))).getFirst());
    assertEquals(true, bare.durationIsEmpty());
    assertEquals(false, bare.baseDataOffsetPresent());

    TrackFragmentBaseMediaDecodeTimeBox tfdt =
        assertInstanceOf(
            TrackFragmentBaseMediaDecodeTimeBox.class,
            parse(fullBox("tfdt", 0x01000000, bytes(0x1_2345_6789L))).getFirst());
    assertEquals(0x1_2345_6789L, tfdt.base_media_decode_time);

    ChunkLargeOffsetBox co64 =
        assertInstanceOf(
            ChunkLargeOffsetBox.class,
            parse(fullBox("co64", 0, bytes(1, 0x1_0000_0000L))).getFirst());
    assertEquals(0x1_0000_0000L, co64.entries[0].chunk_offset);

    SampleToGroupBox sbgp =
        assertInstanceOf(
            SampleToGroupBox.class,
            parse(fullBox("sbgp", 0, bytes(Bits.make4cc("roll"), 1, 10, 3))).getFirst());
    assertEquals(Bits.make4cc("roll"), sbgp.grouping_type);
    assertEquals(10, sbgp.entries[0].sample_count);
    assertEquals(3, sbgp.entries[0].group_description_index);

    ShadowSyncSampleBox stsh =
        assertInstanceOf(
            ShadowSyncSampleBox.class, parse(fullBox("stsh", 0, bytes(1, 5, 1))).getFirst());
    assertEquals(5, stsh.entries[0].shadowed_sample_number);
    assertEquals(1, stsh.entries[0].sync_sample_number);

    HintMediaHeaderBox hmhd =
        assertInstanceOf(
            HintMediaHeaderBox.class,
            parse(fullBox("hmhd", 0, bytes((short) 1500, (short) 900, 64000, 32000, 0)))
                .getFirst());
    assertEquals(1500, hmhd.maxPDUsize);
    assertEquals(900, hmhd.avgPDUsize);
    assertEquals(64000, hmhd.maxbitrate);
    assertEquals(32000, hmhd.avgbitrate);
  }

  @Test
  void compactSampleSizesAreParsed() throws IOException {
    CompactSampleSizeBox eight =
        assertInstanceOf(
            CompactSampleSizeBox.class,
            parse(
                    fullBox(
                        "stz2",
                        0,
                        bytes(new byte[] {0, 0, 0}, (byte) 8, 3, (byte) 1, (byte) 2, (byte) 3)))
                .getFirst());
    assertEquals(3, eight.sample_count);
    assertEquals(2, eight.entries[1].entry_size);
    CompactSampleSizeBox sixteen =
        assertInstanceOf(
            CompactSampleSizeBox.class,
            parse(
                    fullBox(
                        "stz2",
                        0,
                        bytes(new byte[] {0, 0, 0}, (byte) 16, 2, (short) 300, (short) 7)))
                .getFirst());
    assertEquals(300, sixteen.entries[0].entry_size);
    assertEquals(7, sixteen.entries[1].entry_size);
  }

  @Test
  void unsupportedCompactSampleSizesAreRejected() {
    byte[] file = fullBox("stz2", 0, bytes(new byte[] {0, 0, 0}, (byte) 4, 2, (byte) 0x12));
    org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> parse(file));
  }
}
