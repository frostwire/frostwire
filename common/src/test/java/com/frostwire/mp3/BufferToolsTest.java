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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The byte-level helpers every ID3 reader and writer is built on. */
class BufferToolsTest {

  @Test
  void bytesReadAsIso88591Characters() {
    byte[] b = bytes("xCaf", 0xe9, "x");
    assertEquals("Café", BufferTools.byteBufferToString(b, 1, 4));
    assertEquals("", BufferTools.byteBufferToString(b, 1, 0));
    assertEquals("", BufferTools.byteBufferToString(b, 1, -1));
  }

  @Test
  void stringsWriteAsIso88591Bytes() {
    byte[] b = new byte[6];
    BufferTools.stringIntoByteBuffer("xCafé", 1, 4, b, 2);
    assertArrayEquals(bytes(0, 0, "Caf", 0xe9), b);
    assertArrayEquals("World".getBytes(), BufferTools.stringToByteBuffer("Hello World", 6, 5));
  }

  @Test
  void trimStringRightDropsTrailingSpacesAndControlCharacters() {
    assertEquals("abc", BufferTools.trimStringRight("abc \0\n\t"));
    assertEquals("  abc", BufferTools.trimStringRight("  abc  "));
    assertEquals("", BufferTools.trimStringRight(" \0 "));
    assertEquals("", BufferTools.trimStringRight(""));
    String untouched = "abc";
    assertSame(untouched, BufferTools.trimStringRight(untouched));
  }

  @Test
  void padStringRightFillsUpToTheLengthOnly() {
    assertEquals("ab  ", BufferTools.padStringRight("ab", 4, ' '));
    assertEquals("abcdef", BufferTools.padStringRight("abcdef", 4, ' '));
  }

  @Test
  void bitsAreReadAndWrittenByPosition() {
    byte b = (byte) 0x80;
    assertTrue(BufferTools.checkBit(b, 7));
    assertFalse(BufferTools.checkBit(b, 6));
    assertEquals((byte) 0xc0, BufferTools.setBit(b, 6, true));
    assertEquals((byte) 0x00, BufferTools.setBit(b, 7, false));
    assertEquals((byte) 0x80, BufferTools.setBit(b, 7, true));
  }

  @Test
  void shiftByteTreatsTheByteAsUnsigned() {
    assertEquals(0x80, BufferTools.shiftByte((byte) 0x80, 0));
    assertEquals(0x40, BufferTools.shiftByte((byte) 0x80, 1));
    assertEquals(0x100, BufferTools.shiftByte((byte) 0x80, -1));
  }

  @ParameterizedTest
  @CsvSource({"0, 00000000", "257, 00000101", "-559038737, deadbeef", "2147483647, 7fffffff"})
  void integersPackBigEndian(int value, String hex) {
    byte[] packed = BufferTools.packInteger(value);
    assertArrayEquals(HexFormat.of().parseHex(hex), packed);
    assertEquals(value, BufferTools.unpackInteger(packed[0], packed[1], packed[2], packed[3]));
  }

  /** Synchsafe integers keep the top bit of every byte clear: 7 bits per byte, 28 in all. */
  @ParameterizedTest
  @CsvSource({
    "0, 00000000",
    "127, 0000007f",
    "128, 00000100",
    "257, 00000201",
    "268435455, 7f7f7f7f"
  })
  void synchsafeIntegersUseSevenBitsPerByte(int value, String hex) {
    byte[] packed = BufferTools.packSynchsafeInteger(value);
    assertArrayEquals(HexFormat.of().parseHex(hex), packed);
    assertEquals(
        value, BufferTools.unpackSynchsafeInteger(packed[0], packed[1], packed[2], packed[3]));
  }

  @Test
  void unpackingASynchsafeIntegerIgnoresTheTopBits() {
    assertEquals(
        BufferTools.unpackSynchsafeInteger((byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04),
        BufferTools.unpackSynchsafeInteger((byte) 0x81, (byte) 0x82, (byte) 0x83, (byte) 0x84));
  }

  @Test
  void copyBufferCopiesTheRangeOrThrows() {
    byte[] b = {1, 2, 3, 4, 5};
    assertArrayEquals(new byte[] {2, 3, 4}, BufferTools.copyBuffer(b, 1, 3));
    assertArrayEquals(new byte[0], BufferTools.copyBuffer(b, 5, 0));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> BufferTools.copyBuffer(b, -1, 1));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> BufferTools.copyBuffer(b, 1, -1));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> BufferTools.copyBuffer(b, 3, 3));
  }

  @Test
  void copyIntoByteBufferCopiesAtTheDestinationOffset() {
    byte[] dest = new byte[5];
    BufferTools.copyIntoByteBuffer(new byte[] {7, 8, 9}, 0, 3, dest, 1);
    assertArrayEquals(new byte[] {0, 7, 8, 9, 0}, dest);
  }

  /**
   * Unsynchronisation puts a zero after every 0xFF followed by a byte that could start an MPEG sync
   * (0xE0 and up) or by a zero, and after a final 0xFF; synchronisation takes them out again.
   */
  @ParameterizedTest
  @CsvSource({
    "0102ff10, 0102ff10",
    "ffe0, ff00e0",
    "fffb, ff00fb",
    "ff00, ff0000",
    "41ff, 41ff00",
    "ffff, ff00ff00",
    "ff00e0, ff0000e0",
    "'', ''",
  })
  void unsynchronisationRoundTrips(String plainHex, String unsynchronisedHex) {
    byte[] plain = HexFormat.of().parseHex(plainHex);
    byte[] unsynchronised = HexFormat.of().parseHex(unsynchronisedHex);
    assertEquals(
        unsynchronised.length - plain.length, BufferTools.sizeUnsynchronisationWouldAdd(plain));
    assertArrayEquals(unsynchronised, BufferTools.unsynchroniseBuffer(plain));
    assertEquals(
        unsynchronised.length - plain.length,
        BufferTools.sizeSynchronisationWouldSubtract(unsynchronised));
    assertArrayEquals(plain, BufferTools.synchroniseBuffer(unsynchronised));
  }

  @Test
  void buffersThatNeedNoChangeAreReturnedAsIs() {
    byte[] b = {1, 2, 3};
    assertSame(b, BufferTools.unsynchroniseBuffer(b));
    assertSame(b, BufferTools.synchroniseBuffer(b));
  }

  @Test
  void substituteReplacesEveryOccurrence() {
    assertEquals("a+b+c", BufferTools.substitute("a-b-c", "-", "+"));
    assertEquals("abc", BufferTools.substitute("a-b-c", "-", null));
    assertEquals("<x>yy<x>", BufferTools.substitute("xyyx", "x", "<x>"));
    String unchanged = "abc";
    assertSame(unchanged, BufferTools.substitute(unchanged, "z", "y"));
    assertSame(unchanged, BufferTools.substitute(unchanged, "", "y"));
  }

  @Test
  void asciiOnlyReplacesEverythingOutsidePrintableAscii() {
    assertEquals("h?llo? ~", BufferTools.asciiOnly("héllo\n ~"));
  }

  @Test
  void terminatorsAreOneByteExceptForUtf16() {
    assertEquals(1, BufferTools.terminatorLength(EncodedText.TEXT_ENCODING_ISO_8859_1));
    assertEquals(2, BufferTools.terminatorLength(EncodedText.TEXT_ENCODING_UTF_16));
    assertEquals(2, BufferTools.terminatorLength(EncodedText.TEXT_ENCODING_UTF_16BE));
    assertEquals(1, BufferTools.terminatorLength(EncodedText.TEXT_ENCODING_UTF_8));
  }

  /** A UTF-16 terminator is a zero code unit: two zero bytes at an even distance from the start. */
  @Test
  void utf16TerminatorsAreAlignedZeroCodeUnits() throws InvalidDataException {
    // 'A' 0x00 | 0x00 'B' | 0x00 0x00: the zero pair straddling two code units is not one.
    byte[] b = {0x41, 0, 0, 0x42, 0, 0};
    assertEquals(4, BufferTools.indexOfTerminator(b, 0, EncodedText.TEXT_ENCODING_UTF_16));
    assertEquals(1, BufferTools.indexOfTerminator(b, 0, EncodedText.TEXT_ENCODING_ISO_8859_1));
    assertEquals(4, BufferTools.indexOfRequiredTerminator(b, 0, EncodedText.TEXT_ENCODING_UTF_16));
    assertEquals(2, BufferTools.indexOfTerminator(b, 2, EncodedText.TEXT_ENCODING_UTF_8));
  }

  @Test
  void aMissingTerminatorIsTheEndOfTheBufferOrInvalidWhenRequired() {
    byte[] b = {0x41, 0x42, 0x43};
    assertEquals(3, BufferTools.indexOfTerminator(b, 0, EncodedText.TEXT_ENCODING_ISO_8859_1));
    assertEquals(3, BufferTools.indexOfTerminator(b, 0, EncodedText.TEXT_ENCODING_UTF_16BE));
    assertThrows(
        InvalidDataException.class,
        () -> BufferTools.indexOfRequiredTerminator(b, 0, EncodedText.TEXT_ENCODING_UTF_8));
  }
}
