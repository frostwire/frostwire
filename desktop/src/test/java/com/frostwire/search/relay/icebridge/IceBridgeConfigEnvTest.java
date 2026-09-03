/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Standalone FORWARDER hubs (e.g. EC2) default to the fat hub topology for
 * unset env keys; leaves keep the lean compiled defaults. Explicit env
 * always wins. Uses system properties because config env lookup falls
 * back to them when real environment variables are absent.
 */
class IceBridgeConfigEnvTest {
  private static final String[] MANAGED_KEYS = {
    "ICEBRIDGE_ROLE",
    "ICEBRIDGE_MESH_FANOUT",
    "ICEBRIDGE_SEARCH_PEER_FANOUT",
    "ICEBRIDGE_MESH_HOP_TTL",
    "ICEBRIDGE_SEARCH_TTL"
  };

  @AfterEach
  void reset() {
    for (String key : MANAGED_KEYS) {
      System.clearProperty(key);
    }
    IceBridgeTopology.get().resetToDefaults();
  }

  @Test
  void forwarderRoleDefaultsToHubTopology() {
    IceBridgeTopology.get().resetToDefaults();
    System.setProperty("ICEBRIDGE_ROLE", "FORWARDER");

    IceBridgeConfig.fromEnv();

    IceBridgeTopology topo = IceBridgeTopology.get();
    assertEquals(IceBridgeTopology.HYBRID_EC2_MESH_FANOUT, topo.meshBroadcastFanout());
    assertEquals(IceBridgeTopology.HYBRID_EC2_SEARCH_PEER_FANOUT, topo.searchPeerFanout());
    assertEquals(IceBridgeTopology.HYBRID_EC2_MESH_HOP_TTL, topo.meshHopTtl());
    assertEquals(IceBridgeTopology.HYBRID_EC2_SEARCH_TTL, topo.searchTtl());
  }

  @Test
  void explicitEnvBeatsHubTopology() {
    IceBridgeTopology.get().resetToDefaults();
    System.setProperty("ICEBRIDGE_ROLE", "FORWARDER");
    System.setProperty("ICEBRIDGE_MESH_FANOUT", "6");

    IceBridgeConfig.fromEnv();

    IceBridgeTopology topo = IceBridgeTopology.get();
    assertEquals(6, topo.meshBroadcastFanout());
    assertEquals(IceBridgeTopology.HYBRID_EC2_SEARCH_PEER_FANOUT, topo.searchPeerFanout());
  }

  @Test
  void leafRoleKeepsLeanTopology() {
    IceBridgeTopology.get().resetToDefaults();
    System.setProperty("ICEBRIDGE_ROLE", "BOTH");

    IceBridgeConfig.fromEnv();

    IceBridgeTopology topo = IceBridgeTopology.get();
    assertEquals(IceBridgeTopology.DEFAULT_MESH_BROADCAST_FANOUT, topo.meshBroadcastFanout());
    assertEquals(IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT, topo.searchPeerFanout());
    assertEquals(IceBridgeTopology.DEFAULT_SEARCH_TTL, topo.searchTtl());
  }
}
