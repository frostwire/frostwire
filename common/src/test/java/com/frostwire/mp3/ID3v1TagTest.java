/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
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

  @Test
  void v11TagsReadEveryField() throws NoSuchTagException {
    ID3v1Tag tag = new ID3v1Tag(v11Tag("Títle", "Artist", "Album", "2001", "Comment", 7, 17));
    assertEquals("1", tag.getVersion());
    assertEquals("Títle", tag.getTitle());
    assertEquals("Artist", tag.getArtist());
    assertEquals("Album", tag.getAlbum());
    assertEquals("2001", tag.getYear());
    assertEquals("Comment", tag.getComment());
    assertEquals("7", tag.getTrack());
    assertEquals(17, tag.getGenre());
    assertEquals("Rock", tag.getGenreDescription());
  }

  /** Without the zero byte at 125, the comment runs to 30 bytes and there is no track. */
  @Test
  void v10TagsHaveALongerCommentAndNoTrack() throws NoSuchTagException {
    byte[] b = v11Tag("T", "A", "B", "1999", "", 0, 0);
    put(b, 97, "123456789012345678901234567890", 30);
    ID3v1Tag tag = new ID3v1Tag(b);
    assertEquals("0", tag.getVersion());
    assertEquals("123456789012345678901234567890", tag.getComment());
    assertNull(tag.getTrack());
  }

  @Test
  void aZeroTrackByteIsAnEmptyTrack() throws NoSuchTagException {
    ID3v1Tag tag = new ID3v1Tag(v11Tag("T", "A", "B", "1999", "C", 0, 0));
    assertEquals("", tag.getTrack());
    assertEquals("1", tag.getVersion());
  }

  @Test
  void fieldsAreTrimmedOfTrailingSpacesAndNuls() throws NoSuchTagException {
    byte[] b = v11Tag("", "", "", "", "", 1, 0);
    put(b, 3, "Title    ", 30);
    ID3v1Tag tag = new ID3v1Tag(b);
    assertEquals("Title", tag.getTitle());
    assertEquals("", tag.getArtist());
  }

  /** Genre 255 means none; numbers past the genre list have no name. */
  @Test
  void genreDescriptions() throws NoSuchTagException {
    ID3v1Tag none = new ID3v1Tag(v11Tag("T", "A", "B", "1999", "C", 1, 255));
    assertEquals(-1, none.getGenre());
    assertEquals("Unknown", none.getGenreDescription());
    ID3v1Tag past = new ID3v1Tag(v11Tag("T", "A", "B", "1999", "C", 1, 254));
    assertEquals(254, past.getGenre());
    assertEquals("Unknown", past.getGenreDescription());
    assertEquals("Blues", new ID3v1Tag(v11Tag("", "", "", "", "", 1, 0)).getGenreDescription());
  }

  @Test
  void onlyExactly128BytesStartingWithTagAreATag() {
    assertThrows(NoSuchTagException.class, () -> new ID3v1Tag(new byte[127]));
    assertThrows(NoSuchTagException.class, () -> new ID3v1Tag(new byte[129]));
    byte[] b = v11Tag("T", "A", "B", "1999", "C", 1, 0);
    b[2] = 'X';
    assertThrows(NoSuchTagException.class, () -> new ID3v1Tag(b));
  }

  @Test
  void writtenTagsReadBack() throws NoSuchTagException {
    ID3v1Tag written = new ID3v1Tag();
    written.setTitle("Títle");
    written.setArtist("Artist");
    written.setAlbum("Album");
    written.setYear("2001");
    written.setComment("Comment");
    written.setTrack("7");
    written.setGenre(200);
    byte[] bytes = written.toBytes();
    assertArrayEquals(v11Tag("Títle", "Artist", "Album", "2001", "Comment", 7, 200), bytes);
    ID3v1Tag read = new ID3v1Tag(bytes);
    assertEquals(written, read);
    assertEquals(written.hashCode(), read.hashCode());
  }

  /**
   * An empty tag writes genre 255 (none) and zeros elsewhere. Its zero comment bytes read as a v1.1
   * tag with no track: the two can't be told apart.
   */
  @Test
  void anEmptyTagWritesNoGenre() throws NoSuchTagException {
    byte[] b = new ID3v1Tag().toBytes();
    assertEquals("TAG", new String(b, 0, 3, StandardCharsets.ISO_8859_1));
    assertEquals((byte) 0xff, b[127]);
    ID3v1Tag read = new ID3v1Tag(b);
    assertEquals(-1, read.getGenre());
    assertEquals("", read.getTitle());
    assertEquals("", read.getComment());
    assertEquals("", read.getTrack());
  }

  @Test
  void longFieldsAreCutToTheirSlot() throws NoSuchTagException {
    ID3v1Tag written = new ID3v1Tag();
    written.setTitle("x".repeat(40));
    written.setYear("20011");
    written.setComment("c".repeat(40));
    written.setTrack("3");
    ID3v1Tag read = new ID3v1Tag(written.toBytes());
    assertEquals("x".repeat(30), read.getTitle());
    assertEquals("2001", read.getYear());
    assertEquals("c".repeat(28), read.getComment());
    assertEquals("3", read.getTrack());

    written.setTrack(null);
    read = new ID3v1Tag(written.toBytes());
    assertEquals("c".repeat(30), read.getComment());
    assertNull(read.getTrack());
  }

  /** "3/12" (track 3 of 12) writes 3; a track that doesn't start with a digit writes none. */
  @Test
  void tracksWriteTheirLeadingNumber() throws NoSuchTagException {
    ID3v1Tag written = new ID3v1Tag();
    written.setTrack("3/12");
    assertEquals("3", new ID3v1Tag(written.toBytes()).getTrack());
    written.setTrack("A1");
    assertEquals("", new ID3v1Tag(written.toBytes()).getTrack());
  }

  @Test
  void packTagOverwritesTheWholeBuffer() {
    ID3v1Tag tag = new ID3v1Tag();
    tag.setTitle("T");
    byte[] b = new byte[128];
    Arrays.fill(b, (byte) 'z');
    tag.toBytes(b);
    assertArrayEquals(tag.toBytes(), b);
  }

  @Test
  void tagsDifferingInAnyFieldAreNotEqual() throws NoSuchTagException {
    byte[] base = v11Tag("T", "A", "B", "1999", "C", 1, 2);
    ID3v1Tag tag = new ID3v1Tag(base);
    assertEquals(tag, tag);
    assertNotEquals(tag, "T");
    assertNotEquals(tag, new ID3v1Tag(v11Tag("X", "A", "B", "1999", "C", 1, 2)));
    assertNotEquals(tag, new ID3v1Tag(v11Tag("T", "X", "B", "1999", "C", 1, 2)));
    assertNotEquals(tag, new ID3v1Tag(v11Tag("T", "A", "X", "1999", "C", 1, 2)));
    assertNotEquals(tag, new ID3v1Tag(v11Tag("T", "A", "B", "2000", "C", 1, 2)));
    assertNotEquals(tag, new ID3v1Tag(v11Tag("T", "A", "B", "1999", "X", 1, 2)));
    assertNotEquals(tag, new ID3v1Tag(v11Tag("T", "A", "B", "1999", "C", 3, 2)));
    assertNotEquals(tag, new ID3v1Tag(v11Tag("T", "A", "B", "1999", "C", 1, 3)));
    ID3v1Tag empty = new ID3v1Tag();
    assertNotEquals(empty, tag);
    assertNotEquals(tag, empty);
    assertEquals(empty, new ID3v1Tag());
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
