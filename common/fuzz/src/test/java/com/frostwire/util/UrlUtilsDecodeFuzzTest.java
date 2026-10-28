/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes {@link UrlUtils#decode(String)}, which callers run on URLs, magnet display names and query
 * strings taken from web pages and pasted links. It declares no exceptions and returns {@code ""}
 * for {@code null}, so it must return a string for every input.
 */
class UrlUtilsDecodeFuzzTest {

  @FuzzTest
  void decode(FuzzedDataProvider data) {
    String s = data.consumeRemainingAsString();
    if (UrlUtils.decode(s) == null) {
      throw new IllegalStateException("decode returned null");
    }
  }
}
