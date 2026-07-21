/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.peer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import org.junit.jupiter.api.Test;

class PeerRegistryLearnTest {

  @Test
  void learnedEndpointReplacesStaleRegistrationButPreservesRoleAndVersion() {
    PeerRegistry registry =
        new PeerRegistry(IceBridgeConfig.newBuilder().controlHttpPort(8081).build());
    byte[] pub = new byte[32];
    pub[0] = 7;
    registry.register(
        new PeerRecord(pub, "127.0.0.1", 6889, IceBridgeConfig.Role.BOTH, 1_000L, "1.1.0"));

    registry.learnObservedEndpoint(pub, "76.130.145.63", 6889);

    PeerRecord learned = registry.lookup(pub);
    assertEquals("76.130.145.63", learned.host());
    assertEquals(6889, learned.rudpPort());
    assertEquals(
        IceBridgeConfig.Role.BOTH, learned.role(), "learning must preserve the registered role");
    assertEquals(
        "1.1.0", learned.icebridgeVersion(), "learning must preserve the announced version");
    assertTrue(learned.lastSeenMs() > 1_000L, "learning refreshes lastSeen");
  }

  @Test
  void learnedUnknownPeerBecomesClientNeverForwarder() {
    PeerRegistry registry =
        new PeerRegistry(IceBridgeConfig.newBuilder().controlHttpPort(8081).build());
    byte[] pub = new byte[32];
    pub[0] = 9;

    registry.learnObservedEndpoint(pub, "2001:b011:bc02:7b1e:e512:8a20:c6fa:e66a", 6889);

    PeerRecord learned = registry.lookup(pub);
    assertEquals(IceBridgeConfig.Role.CLIENT, learned.role());
    assertTrue(
        registry.lookupForwarders(10).isEmpty(),
        "observation-only peers must not be used as mesh forwarders");
  }

  @Test
  void learnRejectsInvalidInput() {
    PeerRegistry registry =
        new PeerRegistry(IceBridgeConfig.newBuilder().controlHttpPort(8081).build());
    registry.learnObservedEndpoint(null, "1.2.3.4", 6889);
    registry.learnObservedEndpoint(new byte[31], "1.2.3.4", 6889);
    registry.learnObservedEndpoint(new byte[32], "", 6889);
    registry.learnObservedEndpoint(new byte[32], "1.2.3.4", 0);
    assertEquals(0, registry.size());
  }
}
