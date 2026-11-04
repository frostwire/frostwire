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
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * ID3Wrapper reads a field from the ID3v2 tag when it has one and falls back to the ID3v1 tag, and
 * writes fields to both. SoundCloud downloads are re-tagged through it.
 */
class ID3WrapperTest {

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

  @Test
  void settersWriteBothTagsAndTheTagsReadBack() throws Exception {
    ID3v1Tag v1 = new ID3v1Tag();
    ID3v23Tag v2 = new ID3v23Tag();
    ID3Wrapper wrapper = new ID3Wrapper(v1, v2);
    assertSame(v1, wrapper.getId3v1Tag());
    assertSame(v2, wrapper.getId3v2Tag());
    wrapper.setTitle("Title");
    wrapper.setArtist("Artist");
    wrapper.setAlbum("Album");
    wrapper.setYear("2001");
    wrapper.setTrack("5");
    wrapper.setGenre(17);
    wrapper.setComment("Comment");
    wrapper.setComposer("Composer");
    wrapper.setOriginalArtist("Original");
    wrapper.setCopyright("(c)");
    wrapper.setUrl("https://example.org/");
    wrapper.setEncoder("FrostWire");
    wrapper.setAlbumImage(PNG, "image/png");

    ID3Wrapper read =
        new ID3Wrapper(new ID3v1Tag(v1.toBytes()), ID3v2TagFactory.createTag(v2.toBytes()));
    for (ID3Wrapper w : new ID3Wrapper[] {wrapper, read}) {
      assertEquals("Title", w.getTitle());
      assertEquals("Artist", w.getArtist());
      assertEquals("Album", w.getAlbum());
      assertEquals("2001", w.getYear());
      assertEquals("5", w.getTrack());
      assertEquals(17, w.getGenre());
      assertEquals("Rock", w.getGenreDescription());
      assertEquals("Comment", w.getComment());
      assertEquals("Composer", w.getComposer());
      assertEquals("Original", w.getOriginalArtist());
      assertEquals("(c)", w.getCopyright());
      assertEquals("https://example.org/", w.getUrl());
      assertEquals("FrostWire", w.getEncoder());
      assertArrayEquals(PNG, w.getAlbumImage());
      assertEquals("image/png", w.getAlbumImageMimeType());
    }
    assertEquals("Title", read.getId3v1Tag().getTitle());
    assertEquals("Title", read.getId3v2Tag().getTitle());
  }

  @Test
  void fieldsMissingFromTheId3v2TagComeFromTheId3v1Tag() throws Exception {
    ID3v1Tag v1 =
        new ID3v1Tag(ID3v1TagTest.v11Tag("V1 title", "V1 artist", "V1 album", "1999", "V1", 3, 8));
    ID3v23Tag v2 = new ID3v23Tag();
    v2.setTitle("V2 title");
    ID3Wrapper wrapper = new ID3Wrapper(v1, v2);
    assertEquals("V2 title", wrapper.getTitle());
    assertEquals("V1 artist", wrapper.getArtist());
    assertEquals("V1 album", wrapper.getAlbum());
    assertEquals("1999", wrapper.getYear());
    assertEquals("3", wrapper.getTrack());
    assertEquals("V1", wrapper.getComment());
    // The ID3v1 genre wins whenever it has one.
    v2.setGenre(17);
    assertEquals(8, wrapper.getGenre());
    assertEquals("Jazz", wrapper.getGenreDescription());
    // Fields ID3v1 can't hold.
    assertNull(wrapper.getComposer());
    assertNull(wrapper.getAlbumImage());
  }

  @Test
  void theId3v2GenreIsUsedWhenTheId3v1TagHasNone() {
    ID3v23Tag v2 = new ID3v23Tag();
    v2.setGenre(17);
    assertEquals(17, new ID3Wrapper(new ID3v1Tag(), v2).getGenre());
    assertEquals(17, new ID3Wrapper(null, v2).getGenre());
    assertEquals("Rock", new ID3Wrapper(null, v2).getGenreDescription());
    // The ID3v1 tag names its genre even when it has none.
    assertEquals("Unknown", new ID3Wrapper(new ID3v1Tag(), v2).getGenreDescription());
  }

  @Test
  void anId3v1TagAloneAnswersWhatItHolds() throws Exception {
    ID3v1Tag v1 = new ID3v1Tag(ID3v1TagTest.v11Tag("T", "A", "B", "1999", "C", 3, 8));
    ID3Wrapper wrapper = new ID3Wrapper(v1, null);
    assertEquals("T", wrapper.getTitle());
    assertEquals("A", wrapper.getArtist());
    assertEquals("B", wrapper.getAlbum());
    assertEquals("1999", wrapper.getYear());
    assertEquals("3", wrapper.getTrack());
    assertEquals("C", wrapper.getComment());
    assertEquals(8, wrapper.getGenre());
    assertNull(wrapper.getComposer());
    assertNull(wrapper.getOriginalArtist());
    assertNull(wrapper.getCopyright());
    assertNull(wrapper.getUrl());
    assertNull(wrapper.getEncoder());
    assertNull(wrapper.getAlbumImage());
    assertNull(wrapper.getAlbumImageMimeType());

    wrapper.setTitle("New");
    wrapper.setComposer("Composer");
    wrapper.setOriginalArtist("Original");
    wrapper.setCopyright("(c)");
    wrapper.setUrl("https://example.org/");
    wrapper.setEncoder("FrostWire");
    wrapper.setAlbumImage(PNG, "image/png");
    assertEquals("New", v1.getTitle());
    assertNull(wrapper.getComposer());
  }

  @Test
  void withoutTagsThereIsNothingToReadOrWrite() {
    ID3Wrapper wrapper = new ID3Wrapper(null, null);
    wrapper.setTitle("T");
    wrapper.setArtist("A");
    wrapper.setAlbum("B");
    wrapper.setYear("1999");
    wrapper.setTrack("1");
    wrapper.setGenre(1);
    wrapper.setComment("C");
    wrapper.clearComment();
    wrapper.clearCopyright();
    wrapper.clearEncoder();
    assertNull(wrapper.getTitle());
    assertNull(wrapper.getArtist());
    assertNull(wrapper.getAlbum());
    assertNull(wrapper.getYear());
    assertNull(wrapper.getTrack());
    assertEquals(-1, wrapper.getGenre());
    assertNull(wrapper.getGenreDescription());
    assertNull(wrapper.getComment());
  }

  @Test
  void clearingRemovesTheFieldFromBothTags() {
    ID3v1Tag v1 = new ID3v1Tag();
    ID3v23Tag v2 = new ID3v23Tag();
    ID3Wrapper wrapper = new ID3Wrapper(v1, v2);
    wrapper.setComment("Comment");
    wrapper.setCopyright("(c)");
    wrapper.setEncoder("FrostWire");
    wrapper.clearComment();
    wrapper.clearCopyright();
    wrapper.clearEncoder();
    assertNull(wrapper.getComment());
    assertNull(v1.getComment());
    assertNull(wrapper.getCopyright());
    assertNull(wrapper.getEncoder());
    assertNull(v2.getFrameSets().get(AbstractID3v2Tag.ID_COMMENT));
  }
}
