/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Compatibility fixtures for the box-header checks (frostwire/frostwire#1356).
 *
 * <p>The boundaries the fuzzer inputs crossed: a box's size must be at least its header (8 bytes,
 * 16 with a 64-bit size, 24 for a uuid), fit in its parent and in the file (media data excepted),
 * and hold the box's own fields; a read must fit the caller's buffer. Boxes right at each limit
 * must still decode to the same values; one byte past it is an {@link IOException}. Files are read
 * with {@link IsoFile#head}, the Library's call.
 */
class BoxHeaderCompatibilityTest {

  private static final int CNAM = Box.Cnam;

  @Test
  void aTypicalM4aHeadDecodes() throws IOException {
    byte[] file =
        cat(
            ftyp("M4A ", 0x200, "M4A ", "mp42", "isom"),
            moov(44100, 441000, "Song Title"),
            box("mdat", new byte[8]));
    LinkedList<Box> boxes = head(file, 100 * 1024);
    assertEquals(List.of("ftyp", "moov", "mdat"), types(boxes));
    FileTypeBox ftyp = Box.findFirst(boxes, Box.ftyp);
    assertEquals(Bits.make4cc("M4A "), ftyp.major_brand);
    assertEquals(0x200, ftyp.minor_version);
    assertArrayEquals(
        new int[] {Bits.make4cc("M4A "), Bits.make4cc("mp42"), Bits.make4cc("isom")},
        ftyp.compatible_brands);
    MovieHeaderBox mvhd = Box.findFirst(boxes, Box.mvhd);
    assertEquals(44100, mvhd.timescale);
    assertEquals(441000, mvhd.duration);
    AppleNameBox name = Box.<AppleItemListBox>findFirst(boxes, Box.ilst).findFirst(CNAM);
    assertEquals("Song Title", name.value());
  }

  @Test
  void anEmptyContainerOfExactlyItsHeaderSizeDecodes() throws IOException {
    // moov holding an empty udta (size 8, the smallest box) before its mvhd.
    byte[] file = cat(ftyp("M4A ", 0), box("moov", box("udta"), mvhd(1000, 5000)));
    LinkedList<Box> boxes = head(file, 1024);
    assertEquals(List.of("ftyp", "moov"), types(boxes));
    assertEquals(5000, Box.<MovieHeaderBox>findFirst(boxes, Box.mvhd).duration);
    assertEquals(0, Box.<Box>findFirst(boxes, Box.udta).boxes.size());
  }

  @Test
  void aSizeOneByteSmallerThanTheHeaderIsRejected() {
    ByteBuffer b = ByteBuffer.allocate(16).putInt(7).putInt(Bits.make4cc("udta"));
    assertThrows(IOException.class, () -> head(cat(ftyp("M4A ", 0), b.array()), 1024));
  }

  @Test
  void aSixtyFourBitSizeDecodes() throws IOException {
    // free with size 1 and largesize 24: 16 bytes of header, 8 of data; then moov.
    ByteBuffer free = ByteBuffer.allocate(24).putInt(1).putInt(Bits.make4cc("free")).putLong(24);
    free.put("8 bytes!".getBytes(StandardCharsets.US_ASCII));
    LinkedList<Box> boxes = head(cat(ftyp("M4A ", 0), free.array(), moov(600, 1200, "T")), 1024);
    assertEquals(List.of("ftyp", "free", "moov"), types(boxes));
    assertEquals(1200, Box.<MovieHeaderBox>findFirst(boxes, Box.mvhd).duration);
  }

  @Test
  void aSixtyFourBitSizeSmallerThanItsHeaderIsRejected() {
    ByteBuffer free = ByteBuffer.allocate(24).putInt(1).putInt(Bits.make4cc("free")).putLong(15);
    IOException e =
        assertThrows(IOException.class, () -> head(cat(ftyp("M4A ", 0), free.array()), 1024));
    assertTrue(e.getMessage().startsWith("Invalid size"), e.getMessage());
  }

  @Test
  void aLastBoxOfSizeZeroRunsToTheEndOfTheFile() throws IOException {
    ByteBuffer mdat = ByteBuffer.allocate(108).putInt(0).putInt(Bits.make4cc("mdat"));
    LinkedList<Box> boxes = head(cat(ftyp("M4A ", 0), moov(600, 1200, "T"), mdat.array()), 1024);
    assertEquals(List.of("ftyp", "moov", "mdat"), types(boxes));
  }

  @Test
  void aUuidBoxHoldingItsUserTypeDecodes() throws IOException {
    byte[] usertype = new byte[16];
    for (int i = 0; i < 16; i++) {
      usertype[i] = (byte) (0xa0 + i);
    }
    ByteBuffer uuid = ByteBuffer.allocate(28).putInt(28).putInt(Box.uuid).put(usertype);
    uuid.putInt(0x12345678);
    LinkedList<Box> boxes = head(cat(ftyp("M4A ", 0), uuid.array(), moov(600, 1200, "T")), 1024);
    assertEquals(3, boxes.size());
    assertArrayEquals(usertype, boxes.get(1).usertype);
    assertEquals(1200, Box.<MovieHeaderBox>findFirst(boxes, Box.mvhd).duration);
  }

  @Test
  void aUuidBoxTooSmallForItsUserTypeIsRejected() {
    ByteBuffer uuid = ByteBuffer.allocate(28).putInt(23).putInt(Box.uuid);
    IOException e =
        assertThrows(IOException.class, () -> head(cat(ftyp("M4A ", 0), uuid.array()), 1024));
    assertTrue(e.getMessage().startsWith("Invalid size"), e.getMessage());
  }

  @Test
  void aChildExactlyFillingItsParentDecodes() throws IOException {
    // udta's only child, a 16-byte free, takes every byte of it; mvhd follows udta.
    byte[] free = box("free", "8 bytes!".getBytes(StandardCharsets.US_ASCII));
    LinkedList<Box> boxes =
        head(cat(ftyp("M4A ", 0), box("moov", box("udta", free), mvhd(600, 1200))), 1024);
    assertEquals(1, Box.<Box>findFirst(boxes, Box.udta).boxes.size());
    assertEquals(1200, Box.<MovieHeaderBox>findFirst(boxes, Box.mvhd).duration);
  }

  @Test
  void aChildOneByteLargerThanItsParentIsRejected() {
    byte[] free = box("free", "8 bytes!".getBytes(StandardCharsets.US_ASCII));
    free[3]++; // 17 bytes in a parent with 16
    IOException e =
        assertThrows(
            IOException.class,
            () ->
                head(cat(ftyp("M4A ", 0), box("moov", box("udta", free), mvhd(600, 1200))), 1024));
    assertTrue(e.getMessage().contains("larger than its parent"), e.getMessage());
  }

  @Test
  void aBoxEndingExactlyAtTheEndOfTheFileDecodes() throws IOException {
    byte[] free = box("free", "last 8 b".getBytes(StandardCharsets.US_ASCII));
    LinkedList<Box> boxes = head(cat(ftyp("M4A ", 0), moov(600, 1200, "T"), free), 1024);
    assertEquals(List.of("ftyp", "moov", "free"), types(boxes));
  }

  @Test
  void aBoxOneBytePastTheEndOfTheFileIsRejected() {
    byte[] free = box("free", "last 8 b".getBytes(StandardCharsets.US_ASCII));
    free[3]++;
    IOException e =
        assertThrows(
            IOException.class, () -> head(cat(ftyp("M4A ", 0), moov(600, 1200, "T"), free), 1024));
    assertTrue(e.getMessage().contains("larger than the file"), e.getMessage());
  }

  @Test
  void mediaDataCutShortStillReadsTheHead() throws IOException {
    // A file still downloading: mdat claims 1 MiB, 16 bytes are there.
    ByteBuffer mdat = ByteBuffer.allocate(24).putInt(8 + (1 << 20)).putInt(Bits.make4cc("mdat"));
    LinkedList<Box> boxes =
        head(cat(ftyp("M4A ", 0), moov(44100, 441000, "Partial"), mdat.array()), 100 * 1024);
    assertEquals(List.of("ftyp", "moov", "mdat"), types(boxes));
    AppleNameBox name = Box.<AppleItemListBox>findFirst(boxes, Box.ilst).findFirst(CNAM);
    assertEquals("Partial", name.value());
  }

  @Test
  void aFileTypeBoxWithOnlyItsRequiredFieldsDecodes() throws IOException {
    FileTypeBox ftyp = Box.findFirst(head(ftyp("isom", 1), 1024), Box.ftyp);
    assertEquals(Bits.make4cc("isom"), ftyp.major_brand);
    assertEquals(1, ftyp.minor_version);
    assertEquals(0, ftyp.compatible_brands.length);
  }

  @Test
  void aFileTypeBoxOneByteShortOfItsFieldsIsRejected() {
    byte[] ftyp = ftyp("isom", 1);
    byte[] cut = new byte[ftyp.length - 1];
    System.arraycopy(ftyp, 0, cut, 0, cut.length);
    cut[3]--;
    assertThrows(IOException.class, () -> head(cut, 1024));
  }

  @Test
  void aPayloadExactlyTheSizeOfTheBufferDecodes() throws IOException {
    // 64-byte buffer, ftyp payload 8 + 14 * 4 = 64 bytes.
    String[] brands = new String[14];
    java.util.Arrays.fill(brands, "mp42");
    FileTypeBox ftyp = Box.findFirst(head(ftyp("M4A ", 0, brands), 64), Box.ftyp);
    assertEquals(14, ftyp.compatible_brands.length);
    assertEquals(Bits.make4cc("mp42"), ftyp.compatible_brands[13]);
  }

  @Test
  void aPayloadLargerThanTheBufferIsRejected() {
    String[] brands = new String[15];
    java.util.Arrays.fill(brands, "mp42");
    assertThrows(IOException.class, () -> head(ftyp("M4A ", 0, brands), 64));
  }

  @Test
  void anEmptyTitleDecodes() throws IOException {
    // ©nam holding only its data box header: no text.
    LinkedList<Box> boxes = head(cat(ftyp("M4A ", 0), moov(600, 1200, "")), 1024);
    AppleNameBox name = Box.<AppleItemListBox>findFirst(boxes, Box.ilst).findFirst(CNAM);
    assertNull(name.value());
    assertEquals(1200, Box.<MovieHeaderBox>findFirst(boxes, Box.mvhd).duration);
  }

  @Test
  void aTitleShorterThanItsDataHeaderIsRejected() {
    // ©nam with 15 bytes: one short of the 16-byte data box header. Media data follows, so the
    // short read isn't taken for the end of the file.
    byte[] cnam = box(CNAM, new byte[15]);
    byte[] moov = box("moov", box("udta", meta(box("ilst", cnam))));
    byte[] mdat = box("mdat", new byte[16]);
    assertThrows(IOException.class, () -> head(cat(ftyp("M4A ", 0), moov, mdat), 1024));
  }

  private static LinkedList<Box> head(byte[] file, int bufferSize) throws IOException {
    File f = File.createTempFile("box-header-compat", ".m4a");
    try {
      Files.write(f.toPath(), file);
      try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
        return IsoFile.head(in, ByteBuffer.allocate(bufferSize));
      }
    } finally {
      assertTrue(f.delete());
    }
  }

  private static List<String> types(List<Box> boxes) {
    List<String> types = new ArrayList<>();
    for (Box b : boxes) {
      types.add(Bits.make4cc(b.type));
    }
    return types;
  }

  /** moov with an mvhd and an iTunes title in udta/meta/ilst/©nam. */
  private static byte[] moov(int timescale, int duration, String title) {
    byte[] text = title.getBytes(StandardCharsets.UTF_8);
    ByteBuffer data = ByteBuffer.allocate(16 + text.length);
    data.putInt(16 + text.length).putInt(Bits.make4cc("data")).putInt(1).putInt(0).put(text);
    byte[] ilst = box("ilst", box(CNAM, data.array()));
    return box("moov", mvhd(timescale, duration), box("udta", meta(ilst)));
  }

  private static byte[] mvhd(int timescale, int duration) {
    ByteBuffer b = ByteBuffer.allocate(100);
    b.putInt(0); // version 0, flags
    b.putInt(0).putInt(0).putInt(timescale).putInt(duration);
    b.putInt(0x00010000).putShort((short) 0x0100); // rate 1.0, volume 1.0
    b.put(new byte[10]); // reserved
    b.putInt(0x00010000).putInt(0).putInt(0); // unity matrix
    b.putInt(0).putInt(0x00010000).putInt(0);
    b.putInt(0).putInt(0).putInt(0x40000000);
    b.put(new byte[24]); // pre_defined
    b.putInt(2); // next_track_ID
    return box("mvhd", b.array());
  }

  /** meta (a full box) with an mdir handler, then {@code ilst}. */
  private static byte[] meta(byte[] ilst) {
    ByteBuffer hdlr = ByteBuffer.allocate(25);
    hdlr.putInt(0).putInt(0).putInt(Bits.make4cc("mdir")).put(new byte[12]).put((byte) 0);
    return box("meta", new byte[4], box("hdlr", hdlr.array()), ilst);
  }

  private static byte[] ftyp(String major, int minor, String... compatible) {
    ByteBuffer b = ByteBuffer.allocate(8 + 4 * compatible.length);
    b.putInt(Bits.make4cc(major)).putInt(minor);
    for (String c : compatible) {
      b.putInt(Bits.make4cc(c));
    }
    return box("ftyp", b.array());
  }

  private static byte[] box(String type, byte[]... payload) {
    return box(Bits.make4cc(type), payload);
  }

  private static byte[] box(int type, byte[]... payload) {
    byte[] body = cat(payload);
    return cat(ByteBuffer.allocate(8).putInt(8 + body.length).putInt(type).array(), body);
  }

  private static byte[] cat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] p : parts) {
      out.writeBytes(p);
    }
    return out.toByteArray();
  }
}
