/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.control.RegisterRequest;
import org.junit.jupiter.api.Test;

class RegisterRequestVersionTest {

  @Test
  void canonicalStringOmitsVersionWhenBlank() {
    RegisterRequest req = new RegisterRequest();
    req.pub = "abcd";
    req.host = "example.com";
    req.rudpPort = 6889;
    req.role = IceBridgeConfig.Role.BOTH;
    req.timestamp = 1000L;
    req.icebridgeVersion = null;
    String c = req.canonicalString();
    assertFalse(c.contains("1.1.0"));
    assertTrue(c.endsWith("|1000"));
  }

  @Test
  void canonicalStringAppendsLengthPrefixedVersion() {
    RegisterRequest req = new RegisterRequest();
    req.pub = "abcd";
    req.host = "example.com";
    req.rudpPort = 6889;
    req.role = IceBridgeConfig.Role.FORWARDER;
    req.timestamp = 1000L;
    req.icebridgeVersion = IceBridgeConstants.SOFTWARE_VERSION;
    String c = req.canonicalString();
    String expectedSuffix =
        "|" + IceBridgeConstants.SOFTWARE_VERSION.length() + ":" + IceBridgeConstants.SOFTWARE_VERSION;
    assertTrue(c.endsWith(expectedSuffix), c);
    assertEquals(
        "4:abcd|11:example.com|6889|FORWARDER|1000" + expectedSuffix, c);
  }
}
