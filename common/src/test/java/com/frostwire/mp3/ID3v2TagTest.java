/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static com.frostwire.mp3.Id3Bytes.bytes;
import static com.frostwire.mp3.Id3Bytes.tag;
import static com.frostwire.mp3.Id3Bytes.v23Frame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
