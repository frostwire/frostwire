/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compatibility fixtures for the text-encoding check (frostwire/frostwire#1358).
 *
 * <p>The boundary the fuzzer input crossed: an encoding byte must be one of the four ID3v2
 * encodings, 0 (ISO-8859-1) to 3 (UTF-8). Each of the four must still decode text to the same
 * string, in every frame type that has one; 4, and every byte above 3 or below 0, rejects the frame
 * with {@link InvalidDataException}, so its getter returns {@code null} instead of throwing.
 */
class ID3v2TextEncodingCompatibilityTest {

  private static final String TITLE = "Título ñ €";
  private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void eachValidEncodingDecodesTextFrames(int encoding) throws Exception {
    // ISO-8859-1 has no euro sign.
    String title = encoding == 0 ? "Título ñ" : TITLE;
    assertEquals(title, new ID3v2TextFrameData(false, text(encoding, title)).getText().toString());
    AbstractID3v2Tag t =
        ID3v2TagFactory.createTag(
            tag(
                frame("TIT2", text(encoding, title)),
                frame("TPE1", text(encoding, "Ártist")),
                frame("TALB", text(encoding, "Álbum")),
                new byte[16]));
    assertEquals(title, t.getTitle());
    assertEquals("Ártist", t.getArtist());
    assertEquals("Álbum", t.getAlbum());
  }

  @Test
  void utf16TextDecodesWithEitherByteOrderMark() throws InvalidDataException {
    byte[] le = bytes(1, 0xff, 0xfe, TITLE.getBytes(StandardCharsets.UTF_16LE));
    byte[] be = bytes(1, 0xfe, 0xff, TITLE.getBytes(StandardCharsets.UTF_16BE));
    assertEquals(TITLE, new ID3v2TextFrameData(false, le).getText().toString());
    assertEquals(TITLE, new ID3v2TextFrameData(false, be).getText().toString());
  }

  /** Encodings with a one-byte terminator: description, then the comment, URL or picture. */
  @ParameterizedTest
  @ValueSource(ints = {0, 3})
  void singleByteTerminatedEncodingsDecodeEveryFrameType(int encoding) throws Exception {
    Charset cs = encoding == 0 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
    ID3v2CommentFrameData comm =
        new ID3v2CommentFrameData(
            false, bytes(encoding, "eng", "dé".getBytes(cs), 0, "Cómment".getBytes(cs)));
    assertEquals("dé", comm.getDescription().toString());
    assertEquals("Cómment", comm.getComment().toString());
    ID3v2UrlFrameData url =
        new ID3v2UrlFrameData(false, bytes(encoding, "hóme".getBytes(cs), 0, "http://x.org"));
    assertEquals("hóme", url.getDescription().toString());
    assertEquals("http://x.org", url.getUrl());
    ID3v2PictureFrameData apic =
        new ID3v2PictureFrameData(
            false, bytes(encoding, "image/jpeg", 0, 3, "cóver".getBytes(cs), 0, JPEG));
    assertEquals("cóver", apic.getDescription().toString());
    assertArrayEquals(JPEG, apic.getImageData());
    ID3v2ObseletePictureFrameData pic =
        new ID3v2ObseletePictureFrameData(
            false, bytes(encoding, "JPG", 3, "cóver".getBytes(cs), 0, JPEG));
    assertEquals("cóver", pic.getDescription().toString());
    assertArrayEquals(JPEG, pic.getImageData());
  }

  @Disabled(
      "Not caused by frostwire/frostwire#1358, fails before it too: the COMM, WXXX, APIC and PIC"
          + " parsers end a UTF-16 description at the first zero byte instead of a zero code unit"
          + " (00 00), so a UTF-16 comment decodes as empty and a UTF-16 picture's image data"
          + " gains a leading zero byte")
  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void utf16EncodingsDecodeEveryFrameType(int encoding) throws Exception {
    byte[] bom = encoding == 1 ? new byte[] {(byte) 0xfe, (byte) 0xff} : new byte[0];
    Charset cs = StandardCharsets.UTF_16BE;
    ID3v2CommentFrameData comm =
        new ID3v2CommentFrameData(
            false, bytes(encoding, "eng", bom, 0, 0, bom, "Cómment".getBytes(cs)));
    assertEquals("", comm.getDescription().toString());
    assertEquals("Cómment", comm.getComment().toString());
    ID3v2PictureFrameData apic =
        new ID3v2PictureFrameData(false, bytes(encoding, "image/jpeg", 0, 3, bom, 0, 0, JPEG));
    assertArrayEquals(JPEG, apic.getImageData());
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 5, 6, 0x7f, 0x80, 0xff})
  void anyOtherEncodingByteRejectsTheFrame(int encoding) {
    assertThrows(
        InvalidDataException.class, () -> new ID3v2TextFrameData(false, text(encoding, "x")));
    assertThrows(
        InvalidDataException.class,
        () -> new ID3v2CommentFrameData(false, bytes(encoding, "eng", 0, "x")));
    assertThrows(
        InvalidDataException.class,
        () -> new ID3v2UrlFrameData(false, bytes(encoding, 0, "http://x.org")));
    assertThrows(
        InvalidDataException.class,
        () -> new ID3v2PictureFrameData(false, bytes(encoding, "image/jpeg", 0, 3, 0, JPEG)));
    assertThrows(
        InvalidDataException.class,
        () -> new ID3v2ObseletePictureFrameData(false, bytes(encoding, "JPG", 3, 0, JPEG)));
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 0xff})
  void aFrameWithABadEncodingByteOnlyLosesItsOwnValue(int encoding) throws Exception {
    AbstractID3v2Tag t =
        ID3v2TagFactory.createTag(
            tag(
                frame("TIT2", bytes(encoding, "Title")),
                frame("TPE1", text(3, "Ártist")),
                frame("COMM", bytes(encoding, "eng", 0, "x")),
                new byte[16]));
    assertNull(t.getTitle());
    assertNull(t.getComment());
    assertEquals("Ártist", t.getArtist());
  }

  /** An encoding byte followed by {@code s} in that encoding (UTF-16 with a byte order mark). */
  private static byte[] text(int encoding, String s) {
    Charset cs =
        switch (encoding) {
          case 0 -> StandardCharsets.ISO_8859_1;
          case 1 -> StandardCharsets.UTF_16;
          case 2 -> StandardCharsets.UTF_16BE;
          case 3 -> StandardCharsets.UTF_8;
          default -> StandardCharsets.ISO_8859_1;
        };
    return bytes(encoding, s.getBytes(cs));
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

  /** A v2.3 frame: id, 32-bit size, no flags, data. */
  private static byte[] frame(String id, byte[] data) {
    int n = data.length;
    byte[] size = {(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n};
    return bytes(id, size, new byte[2], data);
  }

  /** A v2.3 tag holding {@code parts}. */
  private static byte[] tag(byte[]... parts) {
    byte[] body = bytes((Object[]) parts);
    int n = body.length;
    byte[] size = {
      (byte) ((n >> 21) & 0x7f),
      (byte) ((n >> 14) & 0x7f),
      (byte) ((n >> 7) & 0x7f),
      (byte) (n & 0x7f)
    };
    return bytes("ID3", 3, 0, 0, size, body);
  }
}
