/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.KeyPairGenerator;
import org.junit.jupiter.api.Test;

class IdentityKeysProviderIsolationTest {

  @Test
  void identityInitializationDoesNotOverrideDefaultXdhProvider() throws Exception {
    String providerBefore = KeyPairGenerator.getInstance("XDH").getProvider().getName();

    IdentityKeys.generate();

    assertEquals(
        providerBefore,
        KeyPairGenerator.getInstance("XDH").getProvider().getName(),
        "Identity crypto must not change JVM-wide provider precedence used by JSSE");
  }
}
