/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes ID3v2 tag parsing. The bytes come from the start of any downloaded MP3, so they are
 * untrusted. {@link ID3v2TagFactory#createTag(byte[])} documents three checked exceptions for bad
 * input; anything else escaping it (index, arithmetic, null, allocation errors) is a bug.
 */
class ID3v2TagFactoryFuzzTest {

  @FuzzTest
  void createTag(FuzzedDataProvider data) {
    byte[] bytes = data.consumeRemainingAsBytes();
    try {
      AbstractID3v2Tag tag = ID3v2TagFactory.createTag(bytes);
      // Read back what the library and the Soundcloud tagger read from a parsed tag.
      tag.getTitle();
      tag.getArtist();
      tag.getAlbum();
      tag.getAlbumImage();
      tag.getLength();
    } catch (NoSuchTagException | UnsupportedTagException | InvalidDataException expected) {
      // Documented rejections of malformed tags.
    }
  }
}
