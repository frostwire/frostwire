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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Compatibility fixtures for the frame-data length check (frostwire/frostwire#1357).
 *
 * <p>The boundary the fuzzer inputs crossed: a frame's data must hold its fields (encoding byte,
 * language, MIME type and its terminator, picture type, description terminator). The shortest data
 * holding every field, with empty values, must still decode to the same values; one field short is
 * an {@link InvalidDataException} from the frame, and {@code null} from the tag's getter.
 */
class ID3v2FrameDataCompatibilityTest {

  private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};

  @Test
  void textFrames() throws InvalidDataException {
    assertEquals("", new ID3v2TextFrameData(false, new byte[] {0}).getText().toString());
    assertEquals("Title", new ID3v2TextFrameData(false, bytes(0, "Title", 0)).getText().toString());
  }

  @Test
  void urlFrames() throws InvalidDataException {
    ID3v2UrlFrameData empty = new ID3v2UrlFrameData(false, new byte[] {0, 0});
    assertEquals("", empty.getDescription().toString());
    assertEquals("", empty.getUrl());
    ID3v2UrlFrameData url = new ID3v2UrlFrameData(false, bytes(0, "home", 0, "http://x.org"));
    assertEquals("home", url.getDescription().toString());
    assertEquals("http://x.org", url.getUrl());
  }

  @Test
  void commentFrames() throws InvalidDataException {
    ID3v2CommentFrameData empty = new ID3v2CommentFrameData(false, bytes(0, "eng", 0));
    assertEquals("eng", empty.getLanguage());
    assertEquals("", empty.getDescription().toString());
    assertEquals("", empty.getComment().toString());
    // The comment text needn't be terminated.
    ID3v2CommentFrameData c = new ID3v2CommentFrameData(false, bytes(0, "eng", "desc", 0, "hi"));
    assertEquals("desc", c.getDescription().toString());
    assertEquals("hi", c.getComment().toString());
  }

  @Test
  void pictureFrames() throws InvalidDataException {
    // Every field present and empty: MIME type, picture type, description, no image bytes.
    ID3v2PictureFrameData empty = new ID3v2PictureFrameData(false, bytes(0, 0, 3, 0));
    assertEquals("", empty.getMimeType());
    assertEquals(3, empty.getPictureType());
    assertEquals("", empty.getDescription().toString());
    assertEquals(0, empty.getImageData().length);

    ID3v2PictureFrameData p =
        new ID3v2PictureFrameData(false, bytes(0, "image/jpeg", 0, 3, "cover", 0, JPEG));
    assertEquals("image/jpeg", p.getMimeType());
    assertEquals(3, p.getPictureType());
    assertEquals("cover", p.getDescription().toString());
    assertArrayEquals(JPEG, p.getImageData());
  }

  @Test
  void v22PictureFrames() throws InvalidDataException {
    ID3v2ObseletePictureFrameData empty =
        new ID3v2ObseletePictureFrameData(false, bytes(0, "JPG", 3, 0));
    assertEquals("image/jpg", empty.getMimeType());
    assertEquals(3, empty.getPictureType());
    assertEquals(0, empty.getImageData().length);

    ID3v2ObseletePictureFrameData p =
        new ID3v2ObseletePictureFrameData(false, bytes(0, "PNG", 0, "c", 0, JPEG));
    assertEquals("image/png", p.getMimeType());
    assertEquals("c", p.getDescription().toString());
    assertArrayEquals(JPEG, p.getImageData());
  }

  @Test
  void dataOneFieldShortIsRejected() {
    assertThrows(InvalidDataException.class, () -> new ID3v2TextFrameData(false, new byte[0]));
    assertThrows(InvalidDataException.class, () -> new ID3v2UrlFrameData(false, new byte[0]));
    // Comment: no terminator after the (empty) description.
    assertThrows(
        InvalidDataException.class, () -> new ID3v2CommentFrameData(false, bytes(0, "eng")));
    // Picture: no description terminator, then no picture type.
    assertThrows(
        InvalidDataException.class,
        () -> new ID3v2PictureFrameData(false, bytes(0, "image/jpeg", 0, 3)));
    assertThrows(
        InvalidDataException.class,
        () -> new ID3v2PictureFrameData(false, bytes(0, "image/jpeg", 0)));
    // v2.2 picture: no description terminator.
    assertThrows(
        InvalidDataException.class,
        () -> new ID3v2ObseletePictureFrameData(false, bytes(0, "JPG", 3)));
  }

  @Test
  void aV23TagWithMinimalFramesDecodesThroughItsGetters() throws Exception {
    byte[] tag =
        tag(
            3,
            frame("TIT2", new byte[] {0}),
            frame("TPE1", bytes(0, "Artist")),
            frame("COMM", bytes(0, "eng", 0, "hi")),
            frame("WXXX", bytes(0, 0, "http://x.org")),
            frame("APIC", bytes(0, "image/jpeg", 0, 3, 0, JPEG)),
            new byte[16]);
    AbstractID3v2Tag t = ID3v2TagFactory.createTag(tag);
    assertEquals("", t.getTitle());
    assertEquals("Artist", t.getArtist());
    assertEquals("hi", t.getComment());
    assertEquals("http://x.org", t.getUrl());
    assertEquals("image/jpeg", t.getAlbumImageMimeType());
    assertArrayEquals(JPEG, t.getAlbumImage());
  }

  @Test
  void aV22TagWithAPictureDecodesThroughItsGetters() throws Exception {
    byte[] pic = bytes(0, "JPG", 3, 0, JPEG);
    byte[] tag =
        tag(
            2,
            "TT2".getBytes(StandardCharsets.ISO_8859_1),
            new byte[] {0, 0, 6},
            bytes(0, "Title"),
            "PIC".getBytes(StandardCharsets.ISO_8859_1),
            new byte[] {0, 0, (byte) pic.length},
            pic,
            new byte[16]);
    AbstractID3v2Tag t = ID3v2TagFactory.createTag(tag);
    assertEquals("Title", t.getTitle());
    assertEquals("image/jpg", t.getAlbumImageMimeType());
    assertArrayEquals(JPEG, t.getAlbumImage());
  }

  @Test
  void aTruncatedFrameMakesOnlyItsGetterReturnNull() throws Exception {
    byte[] tag =
        tag(
            3,
            frame("TIT2", new byte[0]),
            frame("TPE1", bytes(0, "Artist")),
            frame("APIC", bytes(0, "image/jpeg", 0)),
            new byte[16]);
    AbstractID3v2Tag t = ID3v2TagFactory.createTag(tag);
    assertNull(t.getTitle());
    assertEquals("Artist", t.getArtist());
    assertNull(t.getAlbumImage());
    assertNull(t.getAlbumImageMimeType());
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
    return bytes(
        id,
        new byte[] {(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n},
        new byte[2],
        data);
  }

  private static byte[] tag(int version, byte[]... parts) {
    byte[] body = bytes((Object[]) parts);
    int n = body.length;
    return bytes(
        "ID3",
        version,
        0,
        0,
        new byte[] {
          (byte) ((n >> 21) & 0x7f),
          (byte) ((n >> 14) & 0x7f),
          (byte) ((n >> 7) & 0x7f),
          (byte) (n & 0x7f)
        },
        body);
  }
}
