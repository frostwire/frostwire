/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

class IceBridgeNettyMemoryAccessTest {

  @Test
  void modernJdksUseNettysSupportedNonUnsafeMemoryPath() {
    if (Runtime.version().feature() >= 25) {
      assertFalse(
          PlatformDependent.hasUnsafe(),
          "Netty must use its supported memory path; deprecated Unsafe memory methods are being removed");
    }
  }
}
