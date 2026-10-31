/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.knaben;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.SevereLogTrap;

/**
 * Fuzzes the Knaben API response parser. The JSON comes from api.knaben.org over the network, so it
 * is untrusted. {@code parseResults} declares no exceptions and must return a (possibly empty) list
 * for any body. A throw, or a body that takes seconds to parse and ties up a search thread, is a
 * finding. So is an exception the parser catches around the whole body and logs at {@code SEVERE}
 * ({@link SevereLogTrap}), unless the body simply is not the JSON the API documents: that one
 * malformed row made the parser drop every result of the page.
 */
class KnabenSearchPatternFuzzTest {

  private static final KnabenSearchPattern PATTERN = new KnabenSearchPattern();
  private static final SevereLogTrap SWALLOWED = SevereLogTrap.attachTo(KnabenSearchPattern.class);

  @FuzzTest
  void parseResults(FuzzedDataProvider data) {
    String responseBody = data.consumeRemainingAsString();
    SWALLOWED.clear();
    if (PATTERN.parseResults(responseBody) == null) {
      throw new IllegalStateException("parseResults returned null");
    }
    SWALLOWED.rethrowUnexplained(SevereLogTrap.NOT_THE_DOCUMENTED_JSON);
  }
}
