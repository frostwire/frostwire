/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.yt;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.SevereLogTrap;

/**
 * Fuzzes the YouTube result page parser, which cuts the {@code videoRenderer} objects out of the
 * page's inline script and reads each with Gson. The HTML comes from youtube.com over the network,
 * so it is untrusted. {@code parseResults} declares no exceptions and must return a (possibly
 * empty) list for any page. A throw, or a page that takes seconds to parse and ties up a search
 * thread, is a finding. So is an exception the parser catches around the whole page and logs at
 * {@code SEVERE} ({@link SevereLogTrap}): matching the page with RE2/J cannot throw, so it means
 * one malformed row made the parser drop every result of the page.
 */
class YTSearchPatternFuzzTest {

  private static final YTSearchPattern PATTERN = new YTSearchPattern();
  private static final SevereLogTrap SWALLOWED = SevereLogTrap.attachTo(YTSearchPattern.class);

  @FuzzTest
  void parseResults(FuzzedDataProvider data) {
    String responseBody = data.consumeRemainingAsString();
    SWALLOWED.clear();
    if (PATTERN.parseResults(responseBody) == null) {
      throw new IllegalStateException("parseResults returned null");
    }
    SWALLOWED.rethrowUnexplained(t -> false);
  }
}
