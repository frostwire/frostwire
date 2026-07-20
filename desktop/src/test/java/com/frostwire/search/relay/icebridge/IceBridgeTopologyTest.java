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

/** Topology defaults (hybrid EC2 research profile), hard caps, soft-max. */
class IceBridgeTopologyTest {

  @AfterEach
  void reset() {
    IceBridgeTopology.get().resetToDefaults();
  }

  @Test
  void defaultsMatchHybridEc2Research() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.resetToDefaults();
    // TopologyAutoResearch winner on hybrid EC2 benchmark (common random numbers)
    assertEquals(16, t.meshBroadcastFanout());
    assertEquals(30, t.searchPeerFanout());
    assertEquals(3, t.meshHopTtl());
    assertEquals(3, t.searchTtl());
    assertEquals(3, t.softMax());
    assertEquals(3, t.leafUltrapeerConnections());
  }

  @Test
  void limeWireProfileMatchesHistoricalUltrapeer() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.applyLimeWireUltrapeerProfile();
    assertEquals(32, t.meshBroadcastFanout());
    assertEquals(30, t.searchPeerFanout());
    assertEquals(3, t.softMax());
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
    assertEquals(16, t.meshBroadcastFanout());
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
  void applyHybridEc2ProfileRestoresDefaults() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.applyRemote(6, 8, 2, 2);
    t.applyHybridEc2Profile();
    assertEquals(16, t.meshBroadcastFanout());
    assertEquals(30, t.searchPeerFanout());
    assertEquals(3, t.softMax());
  }
}
