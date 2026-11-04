/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static com.frostwire.mp3.Id3Bytes.bytes;
import static com.frostwire.mp3.Id3Bytes.synchsafe;
import static com.frostwire.mp3.Id3Bytes.tag;
import static com.frostwire.mp3.Id3Bytes.v22Frame;
import static com.frostwire.mp3.Id3Bytes.v23Frame;
import static com.frostwire.mp3.Id3Bytes.v24Frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** ID3v2 tags as callers use them: the getters on parsed tags and the setters on written ones. */
class ID3v2TagTest {

  private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2};

  /** A TCON frame of nothing but spaces has no genre; getGenreDescription threw an NPE on it. */
  @Test
  void aBlankGenreHasNoDescription() throws Exception {
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(tag(3, 0, v23Frame("TCON", bytes(0, "   "))));
    assertEquals(-1, tag.getGenre());
    assertNull(tag.getGenreDescription());
  }

  /**
   * A comment whose description doesn't decode in its encoding (bytes that aren't UTF-8, or half a
   * UTF-16 surrogate pair) is still the tag's comment, not an iTunes one. getComment threw an NPE
   * on it.
   */
  @Test
  void commentsWithUndecodableDescriptionsAreStillRead() throws Exception {
    byte[] utf8 = v23Frame("COMM", bytes(3, "eng", 0xff, 0xfe, 0, "Comment"));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(tag(3, 0, utf8));
    assertEquals("Comment", tag.getComment());
    assertNull(tag.getItunesComment());

    // An unpaired UTF-16 surrogate.
    byte[] utf16 = v23Frame("COMM", bytes(2, "eng", 0xd8, 0, 0, 0, Id3Bytes.utf16be("Hi")));
    tag = ID3v2TagFactory.createTag(tag(3, 0, utf16));
    assertEquals("Hi", tag.getComment());
    assertNull(tag.getItunesComment());
  }

  /**
   * ID3v2.4 footers: the header's size counts the extended header, frames and padding but not the
   * footer, which follows them and repeats the header with "3DI" in place of "ID3". The size used
   * to include the footer, so a footer where the spec puts it was rejected as invalid, and a
   * written footer had version 0.0 and was counted in the size.
   */
  @Test
  void v24FootersFollowTheFramesAndRepeatTheHeader() throws Exception {
    byte[] frames = v24Frame("TIT2", bytes(0, "Title"));
    byte[] header = tag(4, 0x10, new byte[0]);
    header = bytes(Arrays.copyOf(header, 6), synchsafe(frames.length));
    byte[] footer = bytes("3DI", Arrays.copyOfRange(header, 3, 10));
    byte[] spec = bytes(header, frames, footer);

    AbstractID3v2Tag read = ID3v2TagFactory.createTag(spec);
    assertTrue(read.hasFooter());
    assertEquals("Title", read.getTitle());

    ID3v24Tag written = new ID3v24Tag();
    written.setFooter(true);
    written.setTitle("Title");
    assertArrayEquals(spec, written.toBytes());
    assertEquals(spec.length, written.getLength());
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 4})
  void everyFieldRoundTrips(int majorVersion) throws Exception {
    AbstractID3v2Tag written = majorVersion == 3 ? new ID3v23Tag() : new ID3v24Tag();
    setEveryField(written);
    AbstractID3v2Tag read = ID3v2TagFactory.createTag(written.toBytes());
    assertEquals(majorVersion + ".0", read.getVersion());
    assertFalse(read.getObseleteFormat());
    assertEquals("Títle", read.getTitle());
    assertEquals("Artist", read.getArtist());
    assertEquals("Album", read.getAlbum());
    assertEquals("2001", read.getYear());
    assertEquals("3/12", read.getTrack());
    assertEquals(17, read.getGenre());
    assertEquals("Rock", read.getGenreDescription());
    assertEquals("Comment", read.getComment());
    assertEquals("0000 0001", read.getItunesComment());
    assertEquals("Composer", read.getComposer());
    assertEquals("Original", read.getOriginalArtist());
    assertEquals("(c) 2001", read.getCopyright());
    assertEquals("https://example.org/", read.getUrl());
    assertEquals("Encoder", read.getEncoder());
    assertArrayEquals(JPEG, read.getAlbumImage());
    assertEquals("image/jpeg", read.getAlbumImageMimeType());
    assertEquals(written.getLength(), read.getLength());
    assertArrayEquals(written.toBytes(), read.toBytes());
  }

  private static void setEveryField(AbstractID3v2Tag tag) {
    tag.setTitle("Títle");
    tag.setArtist("Artist");
    tag.setAlbum("Album");
    tag.setYear("2001");
    tag.setTrack("3/12");
    tag.setGenre(17);
    tag.setComment("Comment");
    tag.setItunesComment("0000 0001");
    tag.setComposer("Composer");
    tag.setOriginalArtist("Original");
    tag.setCopyright("(c) 2001");
    tag.setUrl("https://example.org/");
    tag.setEncoder("Encoder");
    tag.setAlbumImage(JPEG, "image/jpeg");
  }

  @Test
  void anEmptyTagHasNoFields() {
    ID3v23Tag tag = new ID3v23Tag();
    assertNull(tag.getTitle());
    assertNull(tag.getArtist());
    assertNull(tag.getAlbum());
    assertNull(tag.getYear());
    assertNull(tag.getTrack());
    assertEquals(-1, tag.getGenre());
    assertNull(tag.getGenreDescription());
    assertNull(tag.getComment());
    assertNull(tag.getItunesComment());
    assertNull(tag.getComposer());
    assertNull(tag.getOriginalArtist());
    assertNull(tag.getCopyright());
    assertNull(tag.getUrl());
    assertNull(tag.getEncoder());
    assertNull(tag.getAlbumImage());
    assertNull(tag.getAlbumImageMimeType());
    assertEquals(10, tag.getLength());
  }

  /** v2.3 frame sizes are plain integers, v2.4 ones synchsafe. */
  @Test
  void v24FrameSizesAreSynchsafe() throws Exception {
    String title = "x".repeat(199);
    ID3v23Tag v23 = new ID3v23Tag();
    v23.setTitle(title);
    ID3v24Tag v24 = new ID3v24Tag();
    v24.setTitle(title);
    // 200 bytes of data: the encoding byte and the title.
    assertArrayEquals(bytes("TIT2", 0, 0, 0, 200), Arrays.copyOfRange(v23.toBytes(), 10, 18));
    assertArrayEquals(bytes("TIT2", 0, 0, 1, 72), Arrays.copyOfRange(v24.toBytes(), 10, 18));
    assertInstanceOf(
        ID3v24Frame.class, v24.getFrameSets().get(AbstractID3v2Tag.ID_TITLE).getFrames().get(0));
    assertEquals(title, ID3v2TagFactory.createTag(v24.toBytes()).getTitle());
  }

  @Test
  void settersIgnoreNullAndEmptyValues() {
    ID3v24Tag tag = new ID3v24Tag();
    setEveryField(tag);
    int length = tag.getLength();
    tag.setTitle(null);
    tag.setArtist("");
    tag.setAlbum(null);
    tag.setYear("");
    tag.setTrack(null);
    tag.setGenre(-1);
    tag.setComment("");
    tag.setItunesComment(null);
    tag.setComposer("");
    tag.setOriginalArtist(null);
    tag.setCopyright("");
    tag.setUrl(null);
    tag.setEncoder("");
    tag.setAlbumImage(null, "image/png");
    tag.setAlbumImage(new byte[0], "image/png");
    tag.setAlbumImage(JPEG, null);
    tag.setAlbumImage(JPEG, "");
    assertEquals("Títle", tag.getTitle());
    assertEquals(17, tag.getGenre());
    assertEquals("image/jpeg", tag.getAlbumImageMimeType());
    assertEquals(length, tag.getLength());
  }

  @Test
  void settingAFieldAgainReplacesItsFrame() throws Exception {
    ID3v23Tag tag = new ID3v23Tag();
    tag.setTitle("One");
    tag.setTitle("Two");
    assertEquals(1, tag.getFrameSets().get(AbstractID3v2Tag.ID_TITLE).getFrames().size());
    assertEquals("Two", ID3v2TagFactory.createTag(tag.toBytes()).getTitle());
    assertEquals(10 + 10 + 4, tag.getLength());
  }

  @Test
  void clearingAFrameSetRemovesTheField() {
    ID3v23Tag tag = new ID3v23Tag();
    tag.setTitle("Title");
    tag.setEncoder("Encoder");
    tag.clearFrameSet(AbstractID3v2Tag.ID_ENCODER);
    tag.clearFrameSet("XXXX");
    assertNull(tag.getEncoder());
    assertEquals("Title", tag.getTitle());
    assertEquals(10 + 10 + 6, tag.getLength());
  }

  @Test
  void paddingAddsZerosAfterTheFrames() throws Exception {
    ID3v23Tag tag = new ID3v23Tag();
    tag.setTitle("Title");
    int length = tag.getLength();
    tag.setPadding(true);
    assertTrue(tag.getPadding());
    assertEquals(length + 256, tag.getLength());
    byte[] b = tag.toBytes();
    assertArrayEquals(new byte[256], Arrays.copyOfRange(b, length, b.length));
    AbstractID3v2Tag read = ID3v2TagFactory.createTag(b);
    assertEquals("Title", read.getTitle());
    assertEquals(1, read.getFrameSets().size());
    tag.setPadding(false);
    assertEquals(length, tag.getLength());
  }

  /** The picture goes after every other frame, though "APIC" sorts first. */
  @Test
  void thePictureIsWrittenLast() throws Exception {
    ID3v23Tag tag = new ID3v23Tag();
    tag.setAlbumImage(JPEG, "image/jpeg");
    tag.setTitle("Title");
    String b = new String(tag.toBytes(), StandardCharsets.ISO_8859_1);
    assertTrue(b.indexOf("TIT2") < b.indexOf("APIC"));
  }

  /**
   * v2.4 unsynchronisation inserts a zero after each 0xFF that could be read as an MPEG sync, so
   * players don't mistake the picture for audio; reading takes them out again.
   */
  @Test
  void v24UnsynchronisedFramesRoundTrip() throws Exception {
    ID3v24Tag tag = new ID3v24Tag();
    tag.setUnsynchronisation(true);
    tag.setAlbumImage(JPEG, "image/jpeg");
    tag.setTitle("Title");
    assertTrue(tag.hasUnsynchronisation());
    byte[] b = tag.toBytes();
    assertEquals((byte) 0x80, b[5]);
    String hex = HexFormat.of().formatHex(b);
    assertTrue(hex.contains("ffd8ff00e0"), hex);
    AbstractID3v2Tag read = ID3v2TagFactory.createTag(b);
    assertTrue(read.hasUnsynchronisation());
    assertArrayEquals(JPEG, read.getAlbumImage());
    assertEquals("Title", read.getTitle());
  }

  @ParameterizedTest
  @CsvSource({
    "'(17)Rock', 17, Rock",
    "'(17)', 17, Rock",
    "17, 17, Rock",
    "'  (8) ', 8, Jazz",
    "Rock, 17, Rock",
    "jazz, 8, Jazz",
    "'(abc)Jazz', 8, Jazz",
    "Chiptune, -1, Chiptune",
    "'(200)', 200, ",
    "'(-5)', -5, ",
  })
  void genresAreReadByNumberOrName(String tcon, int genre, String description) throws Exception {
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(tag(3, 0, v23Frame("TCON", bytes(0, tcon))));
    assertEquals(genre, tag.getGenre());
    assertEquals(description, tag.getGenreDescription());
  }

  /** The genre is written as "(number)name", or "(number)" past the end of the genre list. */
  @Test
  void genresAreWrittenWithTheirNumberAndName() throws Exception {
    ID3v23Tag tag = new ID3v23Tag();
    tag.setGenre(8);
    assertEquals("(8)Jazz", textOf(tag, "TCON"));
    tag.setGenre(200);
    assertEquals("(200)", textOf(tag, "TCON"));
    assertEquals(200, ID3v2TagFactory.createTag(tag.toBytes()).getGenre());
  }

  private static String textOf(AbstractID3v2Tag tag, String id) throws InvalidDataException {
    byte[] data = tag.getFrameSets().get(id).getFrames().get(0).getData();
    return new ID3v2TextFrameData(false, data).getText().toString();
  }

  /** iTunes keeps loudness data in comments described "iTunNORM", "iTunSMPB" and so on. */
  @Test
  void iTunesCommentsAreKeptApartFromTheComment() throws Exception {
    byte[] body =
        bytes(
            v23Frame("COMM", bytes(0, "eng", "iTunSMPB", 0, "smpb")),
            v23Frame("COMM", bytes(0, "eng", "iTunNORM", 0, "norm")),
            v23Frame("COMM", bytes(0, "eng", "", 0, "Comment")),
            v23Frame("COMM", bytes(0, "eng", "", 0, "Second")));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(tag(3, 0, body));
    assertEquals("Comment", tag.getComment());
    assertEquals("norm", tag.getItunesComment());
    assertEquals(4, tag.getFrameSets().get("COMM").getFrames().size());

    AbstractID3v2Tag onlyItunes =
        ID3v2TagFactory.createTag(
            tag(3, 0, v23Frame("COMM", bytes(0, "eng", "iTunSMPB", 0, "smpb"))));
    assertNull(onlyItunes.getComment());
    assertNull(onlyItunes.getItunesComment());
  }

  @Test
  void invalidFramesReadAsMissingFields() throws Exception {
    byte[] body =
        bytes(
            v23Frame("TIT2", bytes(9, "Title")),
            v23Frame("WXXX", bytes(9, "", 0, "https://example.org/")),
            v23Frame("COMM", bytes(9, "eng", 0, "Comment")),
            v23Frame("APIC", bytes(0, "image/jpeg")));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(tag(3, 0, body));
    assertEquals(4, tag.getFrameSets().size());
    assertNull(tag.getTitle());
    assertNull(tag.getUrl());
    assertNull(tag.getComment());
    assertNull(tag.getAlbumImage());
    assertNull(tag.getAlbumImageMimeType());
  }

  /** ID3v2.2 uses three-letter frame IDs and six-byte frame headers, read but never written. */
  @Test
  void v22TagsReadEveryField() throws Exception {
    byte[] body =
        bytes(
            v22Frame("TT2", bytes(0, "Title")),
            v22Frame("TP1", bytes(0, "Artist")),
            v22Frame("TAL", bytes(0, "Album")),
            v22Frame("TYE", bytes(0, "1999")),
            v22Frame("TRK", bytes(0, "4")),
            v22Frame("TCO", bytes(0, "(8)")),
            v22Frame("COM", bytes(0, "eng", "", 0, "Comment")),
            v22Frame("TCM", bytes(0, "Composer")),
            v22Frame("TOA", bytes(0, "Original")),
            v22Frame("TCR", bytes(0, "(c)")),
            v22Frame("WXX", bytes(0, "", 0, "https://example.org/")),
            v22Frame("TEN", bytes(0, "Encoder")),
            v22Frame("PIC", bytes(0, "PNG", 3, "Cover", 0, JPEG)));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(tag(2, 0, body));
    assertInstanceOf(ID3v22Tag.class, tag);
    assertTrue(tag.getObseleteFormat());
    assertEquals("2.0", tag.getVersion());
    assertEquals("Title", tag.getTitle());
    assertEquals("Artist", tag.getArtist());
    assertEquals("Album", tag.getAlbum());
    assertEquals("1999", tag.getYear());
    assertEquals("4", tag.getTrack());
    assertEquals(8, tag.getGenre());
    assertEquals("Jazz", tag.getGenreDescription());
    assertEquals("Comment", tag.getComment());
    assertNull(tag.getItunesComment());
    assertEquals("Composer", tag.getComposer());
    assertEquals("Original", tag.getOriginalArtist());
    assertEquals("(c)", tag.getCopyright());
    assertEquals("https://example.org/", tag.getUrl());
    assertEquals("Encoder", tag.getEncoder());
    assertArrayEquals(JPEG, tag.getAlbumImage());
    assertEquals("image/png", tag.getAlbumImageMimeType());
    assertThrows(NotSupportedException.class, tag::toBytes);
  }

  @Test
  void v22HeaderFlagsAreUnsynchronisationAndCompression() throws Exception {
    byte[] body = v22Frame("TT2", bytes(0, "Title"));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(tag(2, 0xc0, body));
    assertTrue(tag.hasUnsynchronisation());
    assertEquals("Title", tag.getTitle());
    ID3v22Tag empty = new ID3v22Tag();
    assertEquals("2.0", empty.getVersion());
    assertFalse(empty.hasUnsynchronisation());
  }

  /** A v2.3 or v2.4 extended header is kept and written back as it was read. */
  @ParameterizedTest
  @CsvSource({"3, 0000000600000000000a", "4, 000000060100"})
  void extendedHeadersRoundTrip(int majorVersion, String extendedHex) throws Exception {
    byte[] extended = HexFormat.of().parseHex(extendedHex);
    byte[] frame =
        majorVersion == 3
            ? v23Frame("TIT2", bytes(0, "Title"))
            : v24Frame("TIT2", bytes(0, "Title"));
    byte[] b = tag(majorVersion, 0x40, bytes(extended, frame));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(b);
    assertEquals("Title", tag.getTitle());
    assertArrayEquals(b, tag.toBytes());
    assertEquals(b.length, tag.getLength());
  }

  @Test
  void experimentalFlagRoundTrips() throws Exception {
    byte[] b = tag(4, 0x20, v24Frame("TIT2", bytes(0, "Title")));
    assertArrayEquals(b, ID3v2TagFactory.createTag(b).toBytes());
  }

  @Test
  void framesStopAtPaddingOrAnInvalidFrameId() throws Exception {
    byte[] body =
        bytes(
            v23Frame("TIT2", bytes(0, "Title")),
            v23Frame("tpe1", bytes(0, "Artist")),
            v23Frame("TALB", bytes(0, "Album")));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(tag(3, 0, body));
    assertEquals("Title", tag.getTitle());
    assertNull(tag.getAlbum());
    assertEquals(1, tag.getFrameSets().size());

    tag =
        ID3v2TagFactory.createTag(tag(3, 0, bytes(v23Frame("TIT2", bytes(0, "T")), new byte[50])));
    assertEquals(1, tag.getFrameSets().size());
  }

  /** A tag cut short keeps the frames that fit. */
  @Test
  void aTruncatedTagKeepsItsWholeFrames() throws Exception {
    byte[] whole =
        tag(3, 0, bytes(v23Frame("TIT2", bytes(0, "Title")), v23Frame("TPE1", bytes(0, "A"))));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(Arrays.copyOf(whole, whole.length - 2));
    assertEquals("Title", tag.getTitle());
    assertNull(tag.getArtist());
  }

  @Test
  void tagsThatArentId3v2AreRejected() {
    assertThrows(NoSuchTagException.class, () -> ID3v2TagFactory.createTag(new byte[9]));
    assertThrows(NoSuchTagException.class, () -> ID3v2TagFactory.createTag(new byte[20]));
    assertThrows(
        NoSuchTagException.class,
        () -> ID3v2TagFactory.createTag(bytes("TAG", 3, 0, 0, 0, 0, 0, 1, 0)));
    assertThrows(NoSuchTagException.class, () -> new ID3v23Tag(new byte[20]));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5, 0x7f})
  void unknownMajorVersionsAreUnsupported(int majorVersion) {
    byte[] b = tag(majorVersion, 0, v23Frame("TIT2", bytes(0, "T")));
    assertThrows(UnsupportedTagException.class, () -> ID3v2TagFactory.createTag(b));
  }

  /** The low four flag bits are undefined in every version. */
  @ParameterizedTest
  @ValueSource(ints = {2, 3, 4})
  void undefinedHeaderFlagsAreUnsupported(int majorVersion) {
    byte[] b = tag(majorVersion, 0x01, v23Frame("TIT2", bytes(0, "T")));
    assertThrows(UnsupportedTagException.class, () -> ID3v2TagFactory.createTag(b));
  }

  @Test
  void aTagOfSizeZeroIsInvalid() {
    assertThrows(
        InvalidDataException.class, () -> ID3v2TagFactory.createTag(tag(3, 0, new byte[0])));
  }

  @Test
  void aMissingOrWrongFooterIsInvalid() {
    byte[] frame = v24Frame("TIT2", bytes(0, "Title"));
    byte[] noFooter = tag(4, 0x10, frame);
    InvalidDataException e =
        assertThrows(InvalidDataException.class, () -> ID3v2TagFactory.createTag(noFooter));
    assertEquals("Premature end of tag", e.getMessage());
    byte[] wrongFooter = bytes(noFooter, "ID3", 4, 0, 0x10, synchsafe(frame.length));
    e = assertThrows(InvalidDataException.class, () -> ID3v2TagFactory.createTag(wrongFooter));
    assertEquals("Invalid footer", e.getMessage());
  }

  @Test
  void theFactoryCreatesATagOfEachVersion() throws Exception {
    assertInstanceOf(
        ID3v23Tag.class, ID3v2TagFactory.createTag(tag(3, 0, v23Frame("TIT2", bytes(0, "T")))));
    assertInstanceOf(
        ID3v24Tag.class, ID3v2TagFactory.createTag(tag(4, 0, v24Frame("TIT2", bytes(0, "T")))));
    // A v2.2 tag whose frames read as v2.3 ones isn't reread in the v2.2 format.
    AbstractID3v2Tag v22 = ID3v2TagFactory.createTag(tag(2, 0, v23Frame("TIT2", bytes(0, "T"))));
    assertInstanceOf(ID3v22Tag.class, v22);
    assertFalse(v22.getObseleteFormat());
  }

  @Test
  void tagsWithTheSameFramesAndHeaderAreEqual() throws Exception {
    byte[] b = tag(3, 0, v23Frame("TIT2", bytes(0, "Title")));
    AbstractID3v2Tag tag = ID3v2TagFactory.createTag(b);
    assertEquals(tag, ID3v2TagFactory.createTag(b));
    assertEquals(tag, tag);
    assertNotEquals(tag, "ID3");
    assertNotEquals(tag, ID3v2TagFactory.createTag(tag(3, 0, v23Frame("TIT2", bytes(0, "Other")))));
    assertNotEquals(
        tag, ID3v2TagFactory.createTag(tag(3, 0x80, v23Frame("TIT2", bytes(0, "Title")))));
    assertNotEquals(
        tag, ID3v2TagFactory.createTag(tag(3, 0x20, v23Frame("TIT2", bytes(0, "Title")))));
    assertNotEquals(tag, ID3v2TagFactory.createTag(tag(2, 0, v23Frame("TIT2", bytes(0, "Title")))));
    assertNotEquals(
        tag, ID3v2TagFactory.createTag(tag(3, 0, bytes(v23Frame("TIT2", bytes(0, "Title")), 0))));
    assertNotEquals(
        tag,
        ID3v2TagFactory.createTag(
            tag(3, 0x40, bytes(0, 0, 0, 6, new byte[6], v23Frame("TIT2", bytes(0, "Title"))))));

    ID3v24Tag a = new ID3v24Tag();
    ID3v24Tag c = new ID3v24Tag();
    c.setFooter(true);
    assertTrue(c.hasFooter());
    assertNotEquals(a, c);
    c.setFooter(false);
    assertEquals(a, c);
  }

  /**
   * The comment and the iTunes comment share the COMM frame set, told apart by their description.
   * setItunesComment wrote "iTunNORM" as the language, so getItunesComment never found it, and each
   * setter replaced the whole set, so setting the comment dropped the iTunes one.
   */
  @Test
  void theCommentAndTheItunesCommentAreSetIndependently() throws Exception {
    ID3v23Tag written = new ID3v23Tag();
    written.setComment("Comment");
    written.setItunesComment("0000 0001");
    written.setComment("New comment");
    AbstractID3v2Tag read = ID3v2TagFactory.createTag(written.toBytes());
    assertEquals("New comment", read.getComment());
    assertEquals("0000 0001", read.getItunesComment());
    assertEquals(2, read.getFrameSets().get("COMM").getFrames().size());

    read.setItunesComment("0000 0002");
    assertEquals("New comment", read.getComment());
    assertEquals("0000 0002", read.getItunesComment());
    assertEquals(2, read.getFrameSets().get("COMM").getFrames().size());
  }
}
