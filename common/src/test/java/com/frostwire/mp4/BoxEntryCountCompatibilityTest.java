/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compatibility fixtures for the entry-count check (frostwire/frostwire#1354).
 *
 * <p>The boundary the fuzzer input crossed: an entry table's count times its entry size must fit in
 * the box (and the file). Valid tables at that boundary (empty, exactly filling the box) must still
 * decode to the same entries; a count past it is an {@link IOException}, and a hostile count must
 * be rejected before anything is allocated for it.
 *
 * <p>Boxes are read through a file channel, as {@code MP4Parser} and {@code Mp4Demuxer} do.
 */
class BoxEntryCountCompatibilityTest {

  /** Allocation allowed while rejecting a hostile count: kilobytes, not the advertised size. */
  private static final long ALLOCATION_BOUND = 64 * 1024;

  @Test
  void anEmptyTableDecodes() throws IOException {
    ChunkOffsetBox stco = only(read(fullBox("stco", 0, 0, ints(0))));
    assertEquals(0, stco.entry_count);
    assertEquals(0, stco.entries.length);
  }

  @Test
  void aTableExactlyFillingItsBoxDecodes() throws IOException {
    ChunkOffsetBox stco =
        only(read(fullBox("stco", 0, 0, ints(4, 0x100, 0x200, 0x300, 0x7fffffff))));
    assertEquals(4, stco.entry_count);
    assertArrayEquals(
        new int[] {0x100, 0x200, 0x300, 0x7fffffff},
        Arrays.stream(stco.entries).mapToInt(e -> e.chunk_offset).toArray());
  }

  @Test
  void largeChunkOffsetsDecode() throws IOException {
    ByteBuffer b = ByteBuffer.allocate(4 + 16).putInt(2).putLong(0x1_0000_0000L).putLong(8L);
    ChunkLargeOffsetBox co64 = only(read(fullBox("co64", 0, 0, b.array())));
    assertEquals(2, co64.entry_count);
    assertEquals(0x1_0000_0000L, co64.entries[0].chunk_offset);
    assertEquals(8L, co64.entries[1].chunk_offset);
  }

  @Test
  void editListsOfBothVersionsDecode() throws IOException {
    // Version 0: 32-bit duration and media time, rate 1.0.
    EditListBox v0 = only(read(fullBox("elst", 0, 0, ints(1, 441000, 1024, 0x00010000))));
    assertEquals(1, v0.entry_count);
    assertEquals(441000, v0.entries[0].segment_duration);
    assertEquals(1024, v0.entries[0].media_time);
    assertEquals(1, v0.entries[0].media_rate_integer);
    assertEquals(0, v0.entries[0].media_rate_fraction);
    // Version 1: 64-bit duration, and an empty edit (media time -1).
    ByteBuffer b = ByteBuffer.allocate(4 + 20);
    b.putInt(1).putLong(0x1_0000_0000L).putLong(-1L).putShort((short) 1).putShort((short) 0);
    EditListBox v1 = only(read(fullBox("elst", 1, 0, b.array())));
    assertEquals(0x1_0000_0000L, v1.entries[0].segment_duration);
    assertEquals(-1L, v1.entries[0].media_time);
  }

  @Test
  void sampleTablesDecode() throws IOException {
    TimeToSampleBox stts = only(read(fullBox("stts", 0, 0, ints(2, 100, 1024, 1, 512))));
    assertEquals(2, stts.entry_count);
    assertEquals(100, stts.entries[0].sample_count);
    assertEquals(1024, stts.entries[0].sample_delta);
    assertEquals(1, stts.entries[1].sample_count);
    assertEquals(512, stts.entries[1].sample_delta);

    SampleToChunkBox stsc = only(read(fullBox("stsc", 0, 0, ints(1, 1, 22, 1))));
    assertEquals(1, stsc.entry_count);
    assertEquals(22, stsc.entries[0].samples_per_chunk);

    // sample_size 0: one size per sample follows.
    SampleSizeBox stsz = only(read(fullBox("stsz", 0, 0, ints(0, 3, 371, 372, 0))));
    assertEquals(3, stsz.sample_count);
    assertArrayEquals(
        new int[] {371, 372, 0}, Arrays.stream(stsz.entries).mapToInt(e -> e.entry_size).toArray());

    SyncSampleBox stss = only(read(fullBox("stss", 0, 0, ints(2, 1, 49))));
    assertEquals(2, stss.entry_count);
  }

  @Test
  void compactSampleSizesDecode() throws IOException {
    // field_size 8: three one-byte sizes, padded to the box's end with one zero byte.
    ByteBuffer b8 = ByteBuffer.allocate(12).putInt(8).putInt(3).put(new byte[] {10, 20, 30, 0});
    CompactSampleSizeBox stz2 = only(read(fullBox("stz2", 0, 0, b8.array())));
    assertEquals(3, stz2.sample_count);
    assertEquals(30, stz2.entries[2].entry_size);
    // field_size 16: two sizes exactly filling the box.
    ByteBuffer b16 = ByteBuffer.allocate(12).putInt(16).putInt(2).putShort((short) 300);
    b16.putShort((short) 4000);
    stz2 = only(read(fullBox("stz2", 0, 0, b16.array())));
    assertEquals(2, stz2.sample_count);
    assertEquals(300, stz2.entries[0].entry_size);
    assertEquals(4000, stz2.entries[1].entry_size);
  }

  @Test
  void trackRunsWithPerSampleFieldsDecode() throws IOException {
    // data-offset, sample-duration and sample-size present: two samples of 8 bytes each.
    TrackRunBox trun =
        only(read(fragment(fullBox("trun", 0, 0x301, ints(2, 200, 1024, 371, 1024, 372)))));
    assertEquals(2, trun.sample_count);
    assertEquals(200, trun.data_offset);
    assertEquals(1024, trun.entries[1].sample_duration);
    assertEquals(372, trun.entries[1].sample_size);
  }

  @Test
  void trackRunsWithoutPerSampleFieldsDecodeUpToTheBoxSize() throws IOException {
    // Only data-offset present: every sample takes tfhd's defaults, so the run is 12 bytes
    // whatever its count. 12 samples is the largest count the entry-count check lets through.
    TrackRunBox trun = only(read(fragment(fullBox("trun", 0, 0x001, ints(12, 200)))));
    assertEquals(12, trun.sample_count);
    assertEquals(12, trun.entries.length);
    assertEquals(200, trun.data_offset);
  }

  /**
   * frostwire/frostwire#1354, still supported: a trun with no per-sample fields (every sample
   * takes tfhd's defaults, as in DASH audio fragments) is 12 bytes whatever its count. Its samples'
   * data follows in the mdat, so a real file always has at least one byte per sample after it.
   */
  @Test
  void trackRunsWithoutPerSampleFieldsDecodeAnyCountTheFileHoldsMediaFor() throws IOException {
    for (int count : new int[] {13, 1024, 100_000}) {
      TrackRunBox trun =
          only(read(fragmentWithMedia(fullBox("trun", 0, 0x001, ints(count, 200)), count)));
      assertEquals(count, trun.sample_count);
      assertEquals(count, trun.entries.length);
      assertEquals(200, trun.data_offset);
    }
  }

  /**
   * frostwire/frostwire#1354, rejected as unsafe: a field-less trun claiming more samples than the
   * rest of the file has bytes describes media that isn't there (a truncated or hostile file).
   */
  @Test
  void aFieldlessTrackRunTheFileCantBackIsRejected() {
    assertThrows(
        IOException.class, () -> read(fragment(fullBox("trun", 0, 0x001, ints(1024, 200)))));
  }

  /**
   * frostwire/frostwire#1354, still supported: an stsz with a constant sample_size has no table,
   * whatever sample_count says (ISO/IEC 14496-12 §8.7.3.2). Before the fix the box was read as if
   * it had one and silently lost.
   */
  @Test
  void constantSizeSampleTablesDecode() throws IOException {
    SampleSizeBox stsz = only(read(fragment(fullBox("stsz", 0, 0, ints(1024, 1000)))));
    assertEquals(1024, stsz.sample_size);
    assertEquals(1000, stsz.sample_count);
    assertEquals(0, stsz.entries.length);
  }

  @Test
  void aCountLargerThanTheBoxIsRejected() {
    // 32-byte stco: 24 bytes after the header, so 7 four-byte entries can't fit.
    IOException e =
        assertThrows(
            IOException.class, () -> read(fragment(fullBox("stco", 0, 0, ints(7, 1, 2, 3, 4)))));
    assertTrue(e.getMessage().startsWith("Invalid entry count"), e.getMessage());
  }

  @Test
  void aCountOneMoreThanTheTableIsRejected() {
    // Four entries in the box, a count of five: the fifth would be read from the next box.
    assertThrows(
        IOException.class, () -> read(fragment(fullBox("stco", 0, 0, ints(5, 1, 2, 3, 4)))));
  }

  static Stream<Arguments> hostileCounts() {
    return Stream.of(
        Arguments.of("stco", 0, 0, Integer.MAX_VALUE), // claims 8 GiB of offsets
        Arguments.of("stco", 0, 0, 1 << 29), // claims 2 GiB
        Arguments.of("stco", 0, 0, 1 << 24), // claims 64 MiB
        Arguments.of("elst", 1, 0, 1 << 27), // claims 2.5 GiB of 20-byte edits
        Arguments.of("stts", 0, 0, 1 << 28), // claims 2 GiB
        Arguments.of("trun", 0, 0xf00, 1 << 27), // claims 2 GiB of 16-byte samples
        Arguments.of("trun", 0, 0x001, Integer.MAX_VALUE)); // field-less: 2^31 samples, no data
  }

  @ParameterizedTest
  @MethodSource("hostileCounts")
  void aHostileCountIsRejectedAfterOnlyKilobytesOfWork(
      String type, int version, int flags, int count) throws IOException {
    byte[] file = fragment(fullBox(type, version, flags, ints(count, 0, 0, 0)));
    File f = tempFile(file);
    try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
      InputChannel ch = new InputChannel(in.getChannel());
      ByteBuffer buf = ByteBuffer.allocate(1024);
      long before = allocatedBytes();
      Throwable thrown = null;
      try {
        IsoMedia.read(ch, buf, b -> true);
      } catch (Throwable t) {
        thrown = t;
      }
      long allocated = allocatedBytes() - before;
      assertTrue(thrown instanceof IOException, "expected IOException, got " + thrown);
      assertTrue(
          allocated < ALLOCATION_BOUND,
          type + " count " + count + " allocated " + allocated + " bytes");
    } finally {
      assertTrue(f.delete());
    }
  }

  private static long allocatedBytes() {
    return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
        .getThreadAllocatedBytes(Thread.currentThread().getId());
  }

  @SuppressWarnings("unchecked")
  private static <T extends Box> T only(List<Box> boxes) {
    return (T) boxes.get(0);
  }

  /** Every top-level box in {@code file}, read from a real file. */
  private static List<Box> read(byte[] file) throws IOException {
    File f = tempFile(file);
    try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
      List<Box> boxes = new ArrayList<>();
      IsoMedia.read(new InputChannel(in.getChannel()), ByteBuffer.allocate(1024), boxes::add);
      return boxes;
    } finally {
      assertTrue(f.delete());
    }
  }

  private static File tempFile(byte[] content) throws IOException {
    File f = File.createTempFile("entry-count-compat", ".m4a");
    Files.write(f.toPath(), content);
    return f;
  }

  /** {@code box} followed by 16 bytes of media data, as a run or table is in a real file. */
  private static byte[] fragment(byte[] box) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(box);
    out.writeBytes(ByteBuffer.allocate(16).putInt(16).putInt(Bits.make4cc("mdat")).array());
    return out.toByteArray();
  }

  /** {@code box} followed by an mdat holding {@code mediaBytes} bytes of sample data. */
  private static byte[] fragmentWithMedia(byte[] box, int mediaBytes) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(box);
    out.writeBytes(ByteBuffer.allocate(8).putInt(8 + mediaBytes).putInt(Bits.make4cc("mdat")).array());
    out.writeBytes(new byte[mediaBytes]);
    return out.toByteArray();
  }

  private static byte[] fullBox(String type, int version, int flags, byte[] payload) {
    ByteBuffer b = ByteBuffer.allocate(12 + payload.length);
    b.putInt(12 + payload.length).putInt(Bits.make4cc(type));
    b.putInt((version << 24) | flags).put(payload);
    return b.array();
  }

  private static byte[] ints(int... values) {
    ByteBuffer b = ByteBuffer.allocate(4 * values.length);
    for (int v : values) {
      b.putInt(v);
    }
    return b.array();
  }
}
