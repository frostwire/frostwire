/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes {@link SizeParser#parseSize(String)}, which turns the size column scraped from search
 * engine pages into bytes. Its contract is "size in bytes, or -1 if parsing fails": it declares no
 * exceptions, and a negative size other than -1 would be shown and sorted as a real size.
 */
class SizeParserFuzzTest {

  @FuzzTest
  void parseSize(FuzzedDataProvider data) {
    String sizeStr = data.consumeRemainingAsString();
    long size = SizeParser.parseSize(sizeStr);
    if (size < -1) {
      throw new IllegalStateException("negative size " + size + " for \"" + sizeStr + "\"");
    }
  }
}
