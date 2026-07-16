/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Topology defaults (LimeWire ultrapeer profile), hard caps, soft-max.
 */
class IceBridgeTopologyTest {

  @AfterEach
  void reset() {
    IceBridgeTopology.get().resetToDefaults();
  }

  @Test
  void defaultsMatchLimeWireUltrapeer() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.resetToDefaults();
    // ConnectionSettings.NUM_CONNECTIONS = 32
    assertEquals(32, t.meshBroadcastFanout());
    // UltrapeerSettings.MAX_LEAVES = 30
    assertEquals(30, t.searchPeerFanout());
    // ConnectionSettings.SOFT_MAX = 3
    assertEquals(3, t.meshHopTtl());
    assertEquals(3, t.searchTtl());
    assertEquals(3, t.softMax());
    // Leaf preferred ultrapeers = 3
    assertEquals(3, t.leafUltrapeerConnections());
  }

  @Test
  void hardCapsMatchLimeWireRemoteCeilings() {
    assertEquals(96, IceBridgeTopology.MAX_MESH_BROADCAST_FANOUT);
    assertEquals(96, IceBridgeTopology.MAX_SEARCH_PEER_FANOUT);
    assertEquals(6, IceBridgeTopology.MAX_MESH_HOP_TTL);
    assertEquals(6, IceBridgeTopology.MAX_SEARCH_TTL);
  }

  @Test
  void applyRemoteClampsToHardCaps() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.applyRemote(1000, 1000, 100, 100);
    assertEquals(IceBridgeTopology.MAX_MESH_BROADCAST_FANOUT, t.meshBroadcastFanout());
    assertEquals(IceBridgeTopology.MAX_SEARCH_PEER_FANOUT, t.searchPeerFanout());
    assertEquals(IceBridgeTopology.MAX_MESH_HOP_TTL, t.meshHopTtl());
    assertEquals(IceBridgeTopology.MAX_SEARCH_TTL, t.searchTtl());
  }

  @Test
  void applyRemoteZeroLeavesFieldUnchanged() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.resetToDefaults();
    t.applyRemote(0, 10, 0, 0);
    assertEquals(32, t.meshBroadcastFanout());
    assertEquals(10, t.searchPeerFanout());
    assertEquals(3, t.meshHopTtl());
  }

  @Test
  void infiniteTreeWorstCaseIsHuge_softMaxAndGraphMustContainIt() {
    // Documents why soft-max + finite mesh matter: pure tree N=32 TTL=3
    // exceeds a 30k-peer network.
    long wild = IceBridgeTopology.worstCaseMeshPackets(32, 3);
    assertTrue(wild > 30_000, "infinite tree N=32 TTL=3 = " + wild);
  }

  @Test
  void applyLimeWireUltrapeerProfileRestoresDefaults() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.applyRemote(6, 8, 2, 2);
    t.applyLimeWireUltrapeerProfile();
    assertEquals(32, t.meshBroadcastFanout());
    assertEquals(30, t.searchPeerFanout());
    assertEquals(3, t.softMax());
  }
}
