/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1409, three ID3v2 parsing bugs found by the compatibility-fixture sweep, none
 * caused by the fuzz fixes:
 *
 * <ul>
 *   <li>UTF-16 descriptions ended at the first zero byte instead of a zero code unit, so UTF-16
 *       comments read as empty (and were written back with a one-byte terminator);
 *   <li>a tag with an extended header parsed its frames from the wrong offset;
 *   <li>the frame loop stopped ten bytes early, skipping a short final v2.2 frame.
 * </ul>
 */
class ID3v2ParsingFixesTest {

  private static final byte[] BOM = {(byte) 0xfe, (byte) 0xff};

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void utf16CommentsKeepTheirDescriptionAndText(int encoding) throws Exception {
    byte[] bom = encoding == 1 ? BOM : new byte[0];
    byte[] data =
        bytes(
            encoding,
            "eng",
            bom,
            "Désc".getBytes(StandardCharsets.UTF_16BE),
            0,
            0,
            bom,
            "Cömment".getBytes(StandardCharsets.UTF_16BE));
    ID3v2CommentFrameData comm = new ID3v2CommentFrameData(false, data);
    assertEquals("Désc", comm.getDescription().toString());
    assertEquals("Cömment", comm.getComment().toString());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void utf16UrlDescriptionsKeepTheirText(int encoding) throws Exception {
    byte[] bom = encoding == 1 ? BOM : new byte[0];
    byte[] data =
        bytes(
            encoding,
            bom,
            "Hömepage".getBytes(StandardCharsets.UTF_16BE),
            0,
            0,
            "https://example.org/");
    ID3v2UrlFrameData wxxx = new ID3v2UrlFrameData(false, data);
    assertEquals("Hömepage", wxxx.getDescription().toString());
    assertEquals("https://example.org/", wxxx.getUrl());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void utf16PictureDescriptionsDontEatTheImage(int encoding) throws Exception {
    byte[] bom = encoding == 1 ? BOM : new byte[0];
    byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
    byte[] data =
        bytes(
            encoding,
            "image/jpeg",
            0,
            3,
            bom,
            "Cövér".getBytes(StandardCharsets.UTF_16BE),
            0,
            0,
            jpeg);
    ID3v2PictureFrameData apic = new ID3v2PictureFrameData(false, data);
    assertEquals("Cövér", apic.getDescription().toString());
    assertArrayEquals(jpeg, apic.getImageData());
  }

  /** Written UTF-16 comments end the description with a two-byte terminator and read back. */
  @Test
  void utf16CommentsRoundTrip() throws Exception {
    ID3v2CommentFrameData written =
        new ID3v2CommentFrameData(
            false, "eng", new EncodedText((byte) 1, "Désc"), new EncodedText((byte) 1, "Cömment"));
    ID3v2CommentFrameData read = new ID3v2CommentFrameData(false, written.toBytes());
    assertEquals("Désc", read.getDescription().toString());
    assertEquals("Cömment", read.getComment().toString());
  }

  /** v2.3: the extended header size excludes its own four bytes (6 or 10). */
  @Test
  void v23TagsWithAnExtendedHeaderReadTheirFrames() throws Exception {
    byte[] extended = bytes(0, 0, 0, 6, 0, 0, 0, 0, 0, 0);
    byte[] tag = tag(3, 0x40, bytes(extended, v23Frame("TIT2", bytes(0, "Title"))));
    assertEquals("Title", ID3v2TagFactory.createTag(tag).getTitle());
  }

  /** v2.4: the extended header size is synchsafe and includes its own four bytes. */
  @Test
  void v24TagsWithAnExtendedHeaderReadTheirFrames() throws Exception {
    byte[] extended = bytes(0, 0, 0, 6, 1, 0);
    byte[] tag = tag(4, 0x40, bytes(extended, v24Frame("TIT2", bytes(0, "Title"))));
    assertEquals("Title", ID3v2TagFactory.createTag(tag).getTitle());
  }

  /** A short last frame (6-byte v2.2 header, 2 bytes of data) is still read. */
  @Test
  void aShortFinalV22FrameIsRead() throws Exception {
    byte[] tag =
        tag(2, 0, bytes(v22Frame("TT2", bytes(0, "Title")), v22Frame("TP1", bytes(0, "A"))));
    AbstractID3v2Tag t = ID3v2TagFactory.createTag(tag);
    assertEquals("Title", t.getTitle());
    assertEquals("A", t.getArtist());
  }

  /** Concatenates bytes (Integer), ISO-8859-1 strings and byte arrays. */
  private static byte[] bytes(Object... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Object p : parts) {
      if (p instanceof Integer i) {
        out.write(i);
      } else if (p instanceof String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
      } else {
        out.writeBytes((byte[]) p);
      }
    }
    return out.toByteArray();
  }

  private static byte[] v22Frame(String id, byte[] data) {
    int n = data.length;
    return bytes(id, new byte[] {(byte) (n >>> 16), (byte) (n >>> 8), (byte) n}, data);
  }

  private static byte[] v23Frame(String id, byte[] data) {
    int n = data.length;
    byte[] size = {(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n};
    return bytes(id, size, new byte[2], data);
  }

  private static byte[] v24Frame(String id, byte[] data) {
    return bytes(id, synchsafe(data.length), new byte[2], data);
  }

  private static byte[] tag(int majorVersion, int flags, byte[] body) {
    return bytes("ID3", majorVersion, 0, flags, synchsafe(body.length), body);
  }

  private static byte[] synchsafe(int n) {
    return new byte[] {
      (byte) ((n >> 21) & 0x7f), (byte) ((n >> 14) & 0x7f), (byte) ((n >> 7) & 0x7f), (byte) (n & 0x7f)
    };
  }
}
