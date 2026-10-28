/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes Android's IceBridge port preference parsing: the value a user types in settings, or a
 * stale stored one. {@code parsePort} documents -1 for invalid input and otherwise a port in range
 * (zero only for rUDP); any exception or out-of-range result is a finding.
 */
class IceBridgePortPreferencesFuzzTest {

  @FuzzTest
  void parsePort(FuzzedDataProvider data) {
    boolean rudp = data.consumeBoolean();
    int port = IceBridgePortPreferences.parsePort(data.consumeRemainingAsString(), rudp);
    if (port != -1 && (port < (rudp ? 0 : 1) || port > 65535)) {
      throw new IllegalStateException("parsePort returned " + port + " (rudp=" + rudp + ")");
    }
  }
}
