/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.tpb;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes the Pirate Bay result page parser. The HTML comes from whichever mirror answered, so it is
 * untrusted. {@code parseResults} declares no exceptions and must return a (possibly empty) list
 * for any page; a throw drops every result of the search, and a page that takes seconds to parse
 * ties up a search thread. Both are findings.
 */
class TPBSearchPatternFuzzTest {

  private static final TPBSearchPattern PATTERN = new TPBSearchPattern("example.invalid");

  @FuzzTest
  void parseResults(FuzzedDataProvider data) {
    String responseBody = data.consumeRemainingAsString();
    if (PATTERN.parseResults(responseBody) == null) {
      throw new IllegalStateException("parseResults returned null");
    }
  }
}
