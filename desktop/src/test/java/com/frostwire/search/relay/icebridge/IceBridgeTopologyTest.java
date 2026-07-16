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
 * Topology defaults, hard caps, and amplification sketches for ~30k peers.
 */
class IceBridgeTopologyTest {

  private static final int NETWORK_SIZE = 30_000;

  @AfterEach
  void reset() {
    IceBridgeTopology.get().resetToDefaults();
  }

  @Test
  void defaultsMatchGnutellaInspiredNandM() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.resetToDefaults();
    assertEquals(6, t.meshBroadcastFanout());
    assertEquals(8, t.searchPeerFanout());
    assertEquals(3, t.meshHopTtl());
    assertEquals(2, t.searchTtl());
  }

  @Test
  void applyRemoteClampsToHardCaps() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.applyRemote(100, 100, 100, 100);
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
    assertEquals(6, t.meshBroadcastFanout());
    assertEquals(10, t.searchPeerFanout());
    assertEquals(3, t.meshHopTtl());
  }

  @Test
  void defaultAmplificationIsSafeFor30kNetwork() {
    int n = IceBridgeTopology.DEFAULT_MESH_BROADCAST_FANOUT;
    int m = IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT;
    int meshTtl = IceBridgeTopology.DEFAULT_MESH_HOP_TTL;
    int searchTtl = IceBridgeTopology.DEFAULT_SEARCH_TTL;

    long meshPkts = IceBridgeTopology.worstCaseMeshPackets(n, meshTtl);
    long searchPeers = IceBridgeTopology.worstCaseSearchPeers(m, searchTtl);

    // N=6, TTL=3 → 6 + 36 + 216 = 258 mesh packets (before loop skip)
    assertEquals(6 + 36 + 216, meshPkts);
    // M=8, TTL=2 → 8 + 8*7 = 64 search peers
    assertEquals(8 + 56, searchPeers);

    // Must stay far below full-network floods
    assertTrue(meshPkts < NETWORK_SIZE / 10,
        "mesh flood should be << 10% of 30k peers: " + meshPkts);
    assertTrue(searchPeers < NETWORK_SIZE / 100,
        "search fanout should be << 1% of 30k peers: " + searchPeers);
  }

  @Test
  void highFanoutIsDangerous() {
    // Illustrates why remote config is hard-capped.
    long wild = IceBridgeTopology.worstCaseMeshPackets(16, 5);
    assertTrue(wild > NETWORK_SIZE,
        "N=16 TTL=5 would exceed 30k: " + wild);
  }

  /**
   * Iterative sketch: pick (N,M) pairs and keep those under traffic budgets
   * suitable for a 30k-peer network. Documents the design trade-off; not a
   * live simulator of topology graphs (that comes later with real graphs).
   */
  @Test
  void iterativeBudgetScanKeepsDefaultsNearParetoFront() {
    final long maxMeshPkts = 500; // budget per unknown-target flood
    final long maxSearchPeers = 200; // budget per query (path-pruned worst case)

    int bestN = 1;
    int bestM = 1;
    long bestScore = 0;

    for (int n = 2; n <= IceBridgeTopology.MAX_MESH_BROADCAST_FANOUT; n++) {
      for (int m = 2; m <= IceBridgeTopology.MAX_SEARCH_PEER_FANOUT; m++) {
        long mesh = IceBridgeTopology.worstCaseMeshPackets(n, 3);
        long search = IceBridgeTopology.worstCaseSearchPeers(m, 2);
        if (mesh > maxMeshPkts || search > maxSearchPeers) {
          continue;
        }
        // Prefer higher coverage under budget (N*M as crude coverage proxy)
        long score = (long) n * m;
        if (score > bestScore) {
          bestScore = score;
          bestN = n;
          bestM = m;
        }
      }
    }

    // Defaults N=6 M=8 should be near the best budgeted coverage
    assertTrue(bestN >= 6 && bestM >= 8,
        "expected defaults near front; best was N=" + bestN + " M=" + bestM);
    long defaultMesh =
        IceBridgeTopology.worstCaseMeshPackets(
            IceBridgeTopology.DEFAULT_MESH_BROADCAST_FANOUT, 3);
    long defaultSearch =
        IceBridgeTopology.worstCaseSearchPeers(
            IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT, 2);
    assertTrue(defaultMesh <= maxMeshPkts, "default mesh " + defaultMesh);
    assertTrue(defaultSearch <= maxSearchPeers, "default search " + defaultSearch);
  }
}
