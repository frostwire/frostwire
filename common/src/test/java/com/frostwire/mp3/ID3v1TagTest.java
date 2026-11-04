/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ID3v1 and v1.1 tags: the fixed 128 bytes at the end of an MP3. */
class ID3v1TagTest {

  /** The v1.1 track number is an unsigned byte, 1 to 255; 128 and up used to read as negative. */
  @ParameterizedTest
  @ValueSource(ints = {1, 127, 128, 200, 255})
  void tracksReadAsUnsignedBytes(int track) throws NoSuchTagException {
    ID3v1Tag read = new ID3v1Tag(v11Tag("Title", "Artist", "Album", "2001", "Comment", track, 17));
    assertEquals(Integer.toString(track), read.getTrack());

    ID3v1Tag written = new ID3v1Tag();
    written.setTrack(Integer.toString(track));
    assertEquals(Integer.toString(track), new ID3v1Tag(written.toBytes()).getTrack());
  }

  /** A v1.1 tag: 28-byte comment, a zero byte, then the track. */
  static byte[] v11Tag(
      String title,
      String artist,
      String album,
      String year,
      String comment,
      int track,
      int genre) {
    byte[] b = new byte[128];
    put(b, 0, "TAG", 3);
    put(b, 3, title, 30);
    put(b, 33, artist, 30);
    put(b, 63, album, 30);
    put(b, 93, year, 4);
    put(b, 97, comment, 28);
    b[126] = (byte) track;
    b[127] = (byte) genre;
    return b;
  }

  static void put(byte[] b, int offset, String s, int max) {
    byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
    System.arraycopy(bytes, 0, b, offset, Math.min(bytes.length, max));
  }
}
