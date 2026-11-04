/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static com.frostwire.mp3.Id3Bytes.bytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Text as ID3v2 frames hold it: an encoding byte (0 to 3) and the encoded bytes. */
class EncodedTextTest {

  @Test
  void eachEncodingWritesItsCharset() {
    String s = "Cöver";
    assertArrayEquals(
        s.getBytes(StandardCharsets.ISO_8859_1), new EncodedText((byte) 0, s).toBytes());
    // UTF-16 starts with a byte order mark.
    assertArrayEquals(
        bytes(0xfe, 0xff, Id3Bytes.utf16be(s)), new EncodedText((byte) 1, s).toBytes());
    assertArrayEquals(Id3Bytes.utf16be(s), new EncodedText((byte) 2, s).toBytes());
    assertArrayEquals(s.getBytes(StandardCharsets.UTF_8), new EncodedText((byte) 3, s).toBytes());
  }

  @ParameterizedTest
  @ValueSource(bytes = {0, 1, 2, 3})
  void textRoundTripsInEachEncoding(byte encoding) {
    EncodedText written = new EncodedText(encoding, "Cöver");
    EncodedText read = new EncodedText(encoding, written.toBytes());
    assertEquals("Cöver", read.toString());
    assertEquals(encoding, read.getTextEncoding());
  }

  @Test
  void characterSetsFollowTheEncodingByte() {
    assertEquals("ISO-8859-1", new EncodedText((byte) 0, new byte[0]).getCharacterSet());
    assertEquals("UTF-16", new EncodedText((byte) 1, new byte[0]).getCharacterSet());
    assertEquals("UTF-16BE", new EncodedText((byte) 2, new byte[0]).getCharacterSet());
    assertEquals("UTF-8", new EncodedText((byte) 3, new byte[0]).getCharacterSet());
  }

  @Test
  void utf16WithoutAByteOrderMarkIsReadBigEndian() {
    assertEquals("Hi", new EncodedText((byte) 1, Id3Bytes.utf16be("Hi")).toString());
    assertEquals("Hi", new EncodedText((byte) 1, bytes(0xff, 0xfe, "H", 0, "i", 0)).toString());
  }

  @Test
  void textEndsAtTheFirstNulCharacter() {
    assertEquals("Title", new EncodedText((byte) 0, bytes("Title", 0, "junk")).toString());
  }

  /** Bytes that aren't valid in their charset read as null, not as replacement characters. */
  @Test
  void undecodableBytesReadAsNull() {
    assertNull(new EncodedText((byte) 3, bytes(0xff, 0xfe)).toString());
    assertNull(new EncodedText((byte) 2, bytes("A")).toString());
  }

  /** ISO-8859-1 text that can't be encoded in ISO-8859-1 is written as UTF-8 instead. */
  @Test
  void unencodableTextFallsBackToUtf8Bytes() {
    assertArrayEquals(
        "€uro".getBytes(StandardCharsets.UTF_8), new EncodedText((byte) 0, "€uro").toBytes());
  }

  @ParameterizedTest
  @ValueSource(bytes = {-1, 4, 0x7f})
  void invalidEncodingBytes(byte encoding) {
    assertThrows(InvalidDataException.class, () -> EncodedText.checkTextEncoding(encoding));
    assertThrows(IllegalArgumentException.class, () -> new EncodedText(encoding, "x"));
    EncodedText text = new EncodedText(encoding, new byte[] {'x'});
    assertThrows(IllegalArgumentException.class, text::toString);
  }

  @Test
  void validEncodingBytesPassTheCheck() throws InvalidDataException {
    for (byte b = 0; b <= 3; b++) {
      assertEquals(b, EncodedText.checkTextEncoding(b));
    }
  }

  @Test
  void settingTheEncodingChangesHowTheBytesRead() {
    EncodedText text = new EncodedText((byte) 0, bytes(0xc3, 0xa9));
    assertEquals("Ã©", text.toString());
    text.setTextEncoding((byte) 3);
    assertEquals("é", text.toString());
  }

  @Test
  void equalTextsHaveTheSameEncodingAndBytes() {
    EncodedText a = new EncodedText((byte) 0, "abc");
    EncodedText b = new EncodedText((byte) 0, bytes("abc"));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, new EncodedText((byte) 3, bytes("abc")));
    assertNotEquals(a, new EncodedText((byte) 0, bytes("abd")));
    assertNotEquals(a, "abc");
  }
}
