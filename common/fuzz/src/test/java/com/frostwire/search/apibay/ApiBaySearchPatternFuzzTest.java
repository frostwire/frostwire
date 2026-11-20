/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.apibay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.SevereLogTrap;

/**
 * Fuzzes the ApiBay (apibay.org JSON API) response parser. The JSON comes from the network, so it
 * is untrusted. {@code parseResults} declares no exceptions and must return a (possibly empty) list
 * for any body. A throw, or a body that takes seconds to parse and ties up a search thread, is a
 * finding. So is any exception the parser logs at {@code SEVERE} ({@link SevereLogTrap}): it logs
 * only a {@code WARN} for a body that is not the JSON the API documents.
 */
class ApiBaySearchPatternFuzzTest {

  private static final ApiBaySearchPattern PATTERN = new ApiBaySearchPattern();
  private static final SevereLogTrap SWALLOWED = SevereLogTrap.attachTo(ApiBaySearchPattern.class);

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
