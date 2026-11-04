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
import static com.frostwire.mp3.Id3Bytes.v23Frame;
import static com.frostwire.mp3.Id3Bytes.v24Frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** ID3v2 tags as callers use them: the getters on parsed tags and the setters on written ones. */
class ID3v2TagTest {

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
