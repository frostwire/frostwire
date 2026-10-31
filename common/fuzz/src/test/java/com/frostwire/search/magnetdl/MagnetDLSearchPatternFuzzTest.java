/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.magnetdl;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.SevereLogTrap;

/**
 * Fuzzes the MagnetDL (apibay-style) API response parser. The JSON comes from the MagnetDL API host
 * over the network, so it is untrusted. {@code parseResults} declares no exceptions and must return
 * a (possibly empty) list for any body. A throw, or a body that takes seconds to parse and ties up
 * a search thread, is a finding. So is an exception the parser catches around the whole body and
 * logs at {@code SEVERE} ({@link SevereLogTrap}), unless the body simply is not the JSON the API
 * documents: Gson rejects it, or {@code parseSearchResults} rejects its shape or nesting on purpose
 * with an {@code IllegalStateException}.
 */
class MagnetDLSearchPatternFuzzTest {

  private static final MagnetDLSearchPattern PATTERN = new MagnetDLSearchPattern();
  private static final SevereLogTrap SWALLOWED =
      SevereLogTrap.attachTo(MagnetDLSearchPattern.class);

  @FuzzTest
  void parseResults(FuzzedDataProvider data) {
    String responseBody = data.consumeRemainingAsString();
    SWALLOWED.clear();
    if (PATTERN.parseResults(responseBody) == null) {
      throw new IllegalStateException("parseResults returned null");
    }
    SWALLOWED.rethrowUnexplained(
        t -> SevereLogTrap.NOT_THE_DOCUMENTED_JSON.test(t) || isShapeRejection(t));
  }

  private static boolean isShapeRejection(Throwable t) {
    StackTraceElement[] stack = t.getStackTrace();
    return t instanceof IllegalStateException
        && stack.length > 0
        && stack[0].getClassName().equals(MagnetDLSearchPattern.class.getName())
        && stack[0].getMethodName().equals("parseSearchResults");
  }
}
