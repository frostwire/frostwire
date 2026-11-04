/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static com.frostwire.mp3.Id3Bytes.bytes;
import static com.frostwire.mp3.Id3Bytes.utf16be;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The bytes each kind of frame data writes: text, URL, comment and picture frames, with every
 * field, with none, and unsynchronised.
 */
class ID3v2FrameDataWriteTest {

  private static final byte[] BOM = {(byte) 0xfe, (byte) 0xff};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

  @Test
  void textFrames() throws InvalidDataException {
    EncodedText text = new EncodedText((byte) 3, "Ünï");
    ID3v2TextFrameData written = new ID3v2TextFrameData(false, text);
    byte[] expected = bytes(3, "Ünï".getBytes(StandardCharsets.UTF_8));
    assertArrayEquals(expected, written.toBytes());
    assertEquals(expected.length, written.getLength());
    ID3v2TextFrameData read = new ID3v2TextFrameData(false, expected);
    assertEquals("Ünï", read.getText().toString());
    assertEquals(written, read);
    assertEquals(written.hashCode(), read.hashCode());

    ID3v2TextFrameData empty = new ID3v2TextFrameData(false);
    assertNull(empty.getText());
    assertArrayEquals(new byte[] {0}, empty.toBytes());
    empty.setText(text);
    assertEquals(written, empty);
  }

  /** Unsynchronised frame data has a zero after each 0xFF that could be read as an MPEG sync. */
  @Test
  void unsynchronisedTextFrames() throws InvalidDataException {
    EncodedText text = new EncodedText((byte) 0, bytes(0xff, 0xe0, "x"));
    byte[] unsynchronised = bytes(0, 0xff, 0, 0xe0, "x");
    assertArrayEquals(unsynchronised, new ID3v2TextFrameData(true, text).toBytes());
    assertArrayEquals(bytes(0, 0xff, 0xe0, "x"), new ID3v2TextFrameData(false, text).toBytes());
    assertEquals(text, new ID3v2TextFrameData(true, unsynchronised).getText());
    // Read without unsynchronisation, the zero stays.
    assertEquals(
        new EncodedText((byte) 0, bytes(0xff, 0, 0xe0, "x")),
        new ID3v2TextFrameData(false, unsynchronised).getText());
  }

  @Test
  void textFramesDifferingInTextOrUnsynchronisationAreNotEqual() {
    ID3v2TextFrameData a = new ID3v2TextFrameData(false, new EncodedText((byte) 0, "a"));
    assertNotEquals(a, new ID3v2TextFrameData(true, new EncodedText((byte) 0, "a")));
    assertNotEquals(a, new ID3v2TextFrameData(false, new EncodedText((byte) 0, "b")));
    assertNotEquals(a, new ID3v2TextFrameData(false));
    assertNotEquals(new ID3v2TextFrameData(false), a);
    assertEquals(new ID3v2TextFrameData(false), new ID3v2TextFrameData(false));
    assertNotEquals(a, "a");
  }

  /** The description is in the frame's encoding; the URL itself is always ISO-8859-1. */
  @Test
  void urlFrames() throws InvalidDataException {
    ID3v2UrlFrameData written =
        new ID3v2UrlFrameData(false, new EncodedText((byte) 1, "Hömepage"), "https://example.org/");
    byte[] expected = bytes(1, BOM, utf16be("Hömepage"), 0, 0, "https://example.org/");
    assertArrayEquals(expected, written.toBytes());
    ID3v2UrlFrameData read = new ID3v2UrlFrameData(false, expected);
    assertEquals("Hömepage", read.getDescription().toString());
    assertEquals("https://example.org/", read.getUrl());
    assertEquals(written, read);
    assertEquals(written.hashCode(), read.hashCode());

    ID3v2UrlFrameData empty = new ID3v2UrlFrameData(false);
    assertArrayEquals(new byte[] {0, 0}, empty.toBytes());
    empty.setDescription(new EncodedText((byte) 1, "Hömepage"));
    empty.setUrl("https://example.org/");
    assertEquals(written, empty);
  }

  @Test
  void urlFramesDifferingInAnyFieldAreNotEqual() {
    EncodedText home = new EncodedText((byte) 0, "home");
    ID3v2UrlFrameData a = new ID3v2UrlFrameData(false, home, "http://a/");
    assertNotEquals(a, new ID3v2UrlFrameData(false, home, "http://b/"));
    assertNotEquals(a, new ID3v2UrlFrameData(false, new EncodedText((byte) 0, "x"), "http://a/"));
    assertNotEquals(a, new ID3v2UrlFrameData(true, home, "http://a/"));
    assertNotEquals(a, new ID3v2UrlFrameData(false, null, "http://a/"));
    assertNotEquals(new ID3v2UrlFrameData(false, null, "http://a/"), a);
    assertNotEquals(a, new ID3v2UrlFrameData(false, home, null));
    assertNotEquals(new ID3v2UrlFrameData(false, home, null), a);
    assertEquals(new ID3v2UrlFrameData(false), new ID3v2UrlFrameData(false));
    assertNotEquals(a, "http://a/");
  }

  /** The language is three letters: none writes "eng", longer ones are cut, shorter padded. */
  @ParameterizedTest
  @CsvSource({"'', '   '", "fr, 'fr '", "deu, deu", "english, eng"})
  void commentLanguagesAreThreeLetters(String language, String written)
      throws InvalidDataException {
    ID3v2CommentFrameData comment =
        new ID3v2CommentFrameData(false, language, null, new EncodedText((byte) 0, "c"));
    assertEquals(written, new ID3v2CommentFrameData(false, comment.toBytes()).getLanguage());
  }

  @Test
  void commentFrames() throws InvalidDataException {
    ID3v2CommentFrameData written =
        new ID3v2CommentFrameData(
            false, "deu", new EncodedText((byte) 1, "Désc"), new EncodedText((byte) 1, "Cömment"));
    byte[] expected = bytes(1, "deu", BOM, utf16be("Désc"), 0, 0, BOM, utf16be("Cömment"));
    assertArrayEquals(expected, written.toBytes());
    ID3v2CommentFrameData read = new ID3v2CommentFrameData(false, expected);
    assertEquals(written, read);
    assertEquals(written.hashCode(), read.hashCode());

    ID3v2CommentFrameData empty = new ID3v2CommentFrameData(false);
    assertNull(empty.getLanguage());
    assertArrayEquals(bytes(0, "eng", 0), empty.toBytes());
    empty.setLanguage("deu");
    empty.setDescription(new EncodedText((byte) 1, "Désc"));
    empty.setComment(new EncodedText((byte) 1, "Cömment"));
    assertEquals(written, empty);
  }

  @Test
  void commentFramesDifferingInAnyFieldAreNotEqual() {
    EncodedText d = new EncodedText((byte) 0, "d");
    EncodedText c = new EncodedText((byte) 0, "c");
    ID3v2CommentFrameData a = new ID3v2CommentFrameData(false, "eng", d, c);
    assertNotEquals(a, new ID3v2CommentFrameData(true, "eng", d, c));
    assertNotEquals(a, new ID3v2CommentFrameData(false, "deu", d, c));
    assertNotEquals(a, new ID3v2CommentFrameData(false, null, d, c));
    assertNotEquals(new ID3v2CommentFrameData(false, null, d, c), a);
    assertNotEquals(a, new ID3v2CommentFrameData(false, "eng", c, c));
    assertNotEquals(a, new ID3v2CommentFrameData(false, "eng", null, c));
    assertNotEquals(new ID3v2CommentFrameData(false, "eng", null, c), a);
    assertNotEquals(a, new ID3v2CommentFrameData(false, "eng", d, d));
    assertNotEquals(a, new ID3v2CommentFrameData(false, "eng", d, null));
    assertNotEquals(new ID3v2CommentFrameData(false, "eng", d, null), a);
    assertEquals(new ID3v2CommentFrameData(false), new ID3v2CommentFrameData(false));
    assertNotEquals(a, "c");
  }

  @Test
  void pictureFrames() throws InvalidDataException {
    ID3v2PictureFrameData written =
        new ID3v2PictureFrameData(
            false, "image/png", (byte) 3, new EncodedText((byte) 0, "Cover"), PNG);
    byte[] expected = bytes(0, "image/png", 0, 3, "Cover", 0, PNG);
    assertArrayEquals(expected, written.toBytes());
    ID3v2PictureFrameData read = new ID3v2PictureFrameData(false, expected);
    assertEquals("image/png", read.getMimeType());
    assertEquals(3, read.getPictureType());
    assertEquals("Cover", read.getDescription().toString());
    assertArrayEquals(PNG, read.getImageData());
    assertEquals(written, read);
    assertEquals(written.hashCode(), read.hashCode());

    // Encoding, an empty MIME type and its terminator, picture type, description terminator.
    ID3v2PictureFrameData empty = new ID3v2PictureFrameData(false);
    assertArrayEquals(new byte[] {0, 0, 0, 0}, empty.toBytes());
    empty.setMimeType("image/png");
    empty.setPictureType((byte) 3);
    empty.setDescription(new EncodedText((byte) 0, "Cover"));
    empty.setImageData(PNG);
    assertEquals(written, empty);
  }

  @Test
  void utf16PictureDescriptionsEndWithTwoZeros() throws InvalidDataException {
    ID3v2PictureFrameData written =
        new ID3v2PictureFrameData(
            false, "image/png", (byte) 0, new EncodedText((byte) 2, "Cövér"), PNG);
    byte[] expected = bytes(2, "image/png", 0, 0, utf16be("Cövér"), 0, 0, PNG);
    assertArrayEquals(expected, written.toBytes());
    assertEquals(written, new ID3v2PictureFrameData(false, expected));
  }

  @Test
  void pictureFramesDifferingInAnyFieldAreNotEqual() {
    EncodedText d = new EncodedText((byte) 0, "d");
    ID3v2PictureFrameData a = new ID3v2PictureFrameData(false, "image/png", (byte) 3, d, PNG);
    assertNotEquals(a, new ID3v2PictureFrameData(true, "image/png", (byte) 3, d, PNG));
    assertNotEquals(a, new ID3v2PictureFrameData(false, "image/png", (byte) 4, d, PNG));
    assertNotEquals(a, new ID3v2PictureFrameData(false, "image/gif", (byte) 3, d, PNG));
    assertNotEquals(a, new ID3v2PictureFrameData(false, null, (byte) 3, d, PNG));
    assertNotEquals(new ID3v2PictureFrameData(false, null, (byte) 3, d, PNG), a);
    assertNotEquals(
        a,
        new ID3v2PictureFrameData(
            false, "image/png", (byte) 3, new EncodedText((byte) 0, "x"), PNG));
    assertNotEquals(a, new ID3v2PictureFrameData(false, "image/png", (byte) 3, null, PNG));
    assertNotEquals(new ID3v2PictureFrameData(false, "image/png", (byte) 3, null, PNG), a);
    assertNotEquals(a, new ID3v2PictureFrameData(false, "image/png", (byte) 3, d, new byte[1]));
    assertNotEquals(a, new ID3v2PictureFrameData(false, "image/png", (byte) 3, d, null));
    assertNotEquals(new ID3v2PictureFrameData(false, "image/png", (byte) 3, d, null), a);
    assertEquals(new ID3v2PictureFrameData(false), new ID3v2PictureFrameData(false));
    assertNotEquals(a, "image/png");
  }

  /** v2.2 PIC frames name the image format in three letters instead of a MIME type. */
  @Test
  void v22PictureFrames() throws InvalidDataException {
    ID3v2ObseletePictureFrameData read =
        new ID3v2ObseletePictureFrameData(
            false, bytes(1, "JPG", 3, BOM, utf16be("Cövér"), 0, 0, PNG));
    assertEquals("image/jpg", read.getMimeType());
    assertEquals(3, read.getPictureType());
    assertEquals("Cövér", read.getDescription().toString());
    assertArrayEquals(PNG, read.getImageData());

    ID3v2ObseletePictureFrameData built =
        new ID3v2ObseletePictureFrameData(
            false, "image/jpg", (byte) 3, new EncodedText((byte) 1, "Cövér"), PNG);
    assertEquals(read, built);
    assertNull(new ID3v2ObseletePictureFrameData(false).getMimeType());
  }
}
