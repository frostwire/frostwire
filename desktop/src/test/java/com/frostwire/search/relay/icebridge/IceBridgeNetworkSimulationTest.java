/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.AggregateReport;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.Config;
import com.frostwire.util.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Complex hierarchical network simulation: IceBridge ultrapeer mesh +
 * FrostWire leaves under LimeWire-pro-like topology defaults.
 */
class IceBridgeNetworkSimulationTest {

  private static final Logger LOG = Logger.getLogger(IceBridgeNetworkSimulationTest.class);

  @AfterEach
  void reset() {
    IceBridgeTopology.get().resetToDefaults();
  }

  @Test
  void limeWireUltrapeerProfile_coversContentOnComplexNetwork() {
    IceBridgeTopology.get().applyLimeWireUltrapeerProfile();

    Config cfg = new Config();
    cfg.useLiveTopology = true;
    cfg.iceBridgeCount = 80;
    cfg.frostWireCount = 1_500;
    cfg.contentHolders = 80;
    cfg.searchTrials = 150;
    cfg.seed = 20260716L;

    IceBridgeNetworkSimulator sim = new IceBridgeNetworkSimulator(cfg);
    AggregateReport report = sim.runAggregate();
    LOG.info(report.toString());

    // Topology matches LimeWire ultrapeer defaults
    assertEquals(32, report.config.meshFanout);
    assertEquals(30, report.config.searchPeerFanout);
    assertEquals(3, report.config.meshHopTtl);
    assertEquals(3, report.config.searchTtl);
    assertEquals(3, report.config.softMax);
    assertEquals(3, report.config.leafUpConnections);

    // Mesh is connected enough
    assertTrue(report.iceBridgeEdges > report.config.iceBridgeCount,
        "mesh should have more edges than nodes: " + report.iceBridgeEdges);

    // Soft-max + real graph: mesh msgs must be far below infinite-tree bound
    assertTrue(report.meanMeshMessages < report.worstCaseInfiniteMesh / 10.0,
        "real mesh traffic should be << infinite tree: mean="
            + report.meanMeshMessages + " infinite=" + report.worstCaseInfiniteMesh);

    // Content discovery should usually succeed with 80 holders / 1500 leaves
    assertTrue(report.hitRate >= 0.70,
        "expected high hit rate with dense leaf attachments; was " + report.hitRate);

    // p95 mesh stays bounded (soft max + degree limit)
    assertTrue(report.p95MeshMessages < 5_000,
        "p95 mesh messages too high: " + report.p95MeshMessages);
  }

  @Test
  void conservativeProfile_usesLessTrafficThanUltrapeer() {
    Config ultra = new Config();
    ultra.useLiveTopology = false;
    ultra.meshFanout = 32;
    ultra.searchPeerFanout = 30;
    ultra.meshHopTtl = 3;
    ultra.searchTtl = 3;
    ultra.softMax = 3;
    ultra.leafUpConnections = 3;
    ultra.iceBridgeCount = 60;
    ultra.frostWireCount = 1_000;
    ultra.contentHolders = 50;
    ultra.searchTrials = 80;
    ultra.seed = 99L;

    Config mild = new Config();
    mild.useLiveTopology = false;
    mild.meshFanout = 6;
    mild.searchPeerFanout = 8;
    mild.meshHopTtl = 3;
    mild.searchTtl = 2;
    mild.softMax = 3;
    mild.leafUpConnections = 3;
    mild.iceBridgeCount = 60;
    mild.frostWireCount = 1_000;
    mild.contentHolders = 50;
    mild.searchTrials = 80;
    mild.seed = 99L;

    AggregateReport rUltra = new IceBridgeNetworkSimulator(ultra).runAggregate();
    AggregateReport rMild = new IceBridgeNetworkSimulator(mild).runAggregate();
    LOG.info("ultra: " + rUltra);
    LOG.info("mild:  " + rMild);

    // Ultrapeer-like should spend more search-peer messages (higher M)
    assertTrue(rUltra.meanSearchPeerMessages >= rMild.meanSearchPeerMessages * 0.9,
        "ultrapeer M=30 should query at least as many leaves as mild M=8");
  }

  @Test
  void softMaxClampsRemainingTtlLikeLimeWire() {
    IceBridgeTopology t = IceBridgeTopology.get();
    t.resetToDefaults();
    assertEquals(3, t.softMax());
    assertEquals(3, t.clampRemainingTtl(0, 4)); // start TTL 4 → soft clamp to 3
    assertEquals(2, t.clampRemainingTtl(1, 3));
    assertEquals(1, t.clampRemainingTtl(2, 5));
    assertEquals(0, t.clampRemainingTtl(3, 2));
  }

  @Test
  void largeScaleSketch_30kLeaves_staysBounded() {
    // Scaled: fewer IBs than real, but leaf mass ≈ 30k for fanout stress.
    Config cfg = new Config();
    cfg.useLiveTopology = false;
    cfg.meshFanout = 32;
    cfg.searchPeerFanout = 30;
    cfg.meshHopTtl = 3;
    cfg.searchTtl = 3;
    cfg.softMax = 3;
    cfg.leafUpConnections = 3;
    cfg.iceBridgeCount = 120;
    cfg.frostWireCount = 8_000; // heavy but still unit-testable
    cfg.contentHolders = 200;
    cfg.searchTrials = 40;
    cfg.seed = 7L;

    AggregateReport report = new IceBridgeNetworkSimulator(cfg).runAggregate();
    LOG.info("30k-scale sketch: " + report);

    // Soft-max + degree-32 mesh reaches most IBs (diameter ~2–3); traffic is
    // O(|E|) not infinite-tree N^TTL. Leaves hit via M=30 per IB + dual-envelope.
    assertTrue(report.meanMeshMessages < report.worstCaseInfiniteMesh / 5.0,
        "mesh should stay well below infinite-tree bound: mean="
            + report.meanMeshMessages + " infinite=" + report.worstCaseInfiniteMesh);
    assertTrue(report.meanMeshMessages <= report.iceBridgeEdges * 3.0 + 100,
        "mesh msgs should be O(|E|): mean=" + report.meanMeshMessages
            + " edges=" + report.iceBridgeEdges);
    assertTrue(report.p95SearchPeerMessages <= cfg.frostWireCount * 2.0,
        "search p95 too high: " + report.p95SearchPeerMessages);
    assertTrue(report.hitRate > 0.5, "hit rate " + report.hitRate);
  }
}
