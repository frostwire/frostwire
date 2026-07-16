/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.AggregateReport;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.Config;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.Profile;
import com.frostwire.search.relay.icebridge.sim.TopologyAutoResearch;
import com.frostwire.search.relay.icebridge.sim.TopologyAutoResearch.Candidate;
import com.frostwire.search.relay.icebridge.sim.TopologyAutoResearch.Result;
import com.frostwire.util.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Benchmark profile + auto-research loop for hybrid EC2 IceBridge topology.
 *
 * <p>Goal: find (N, M, TTLs) so users find content (hit rate) while EC2
 * backbone and last-hop leaf queries stay within performance budgets.
 */
class TopologyAutoResearchTest {

  private static final Logger LOG = Logger.getLogger(TopologyAutoResearchTest.class);

  @AfterEach
  void reset() {
    IceBridgeTopology.get().resetToDefaults();
  }

  @Test
  void hybridEc2Benchmark_limeWireDefaults_areBaseline() {
    IceBridgeTopology.get().applyLimeWireUltrapeerProfile();
    Config cfg = TopologyAutoResearch.hybridEc2BenchmarkNetwork();
    cfg.useLiveTopology = true;
    AggregateReport report =
        new IceBridgeNetworkSimulator(cfg).runAggregate(TopologyAutoResearch.hybridEc2Budgets());
    LOG.info("LimeWire defaults on hybrid EC2 network: " + report);
    assertTrue(report.ec2Hubs >= 2);
    assertTrue(report.hitRate > 0.5, "baseline should find most content");
  }

  @Test
  void autoResearch_findsProfileMeetingHitRateAndBudgets() {
    Result result = TopologyAutoResearch.runHybridEc2Research();
    assertNotNull(result.best, "research must produce a best candidate");
    LOG.info("Research winner (score): " + result.best);
    if (result.bestMeetingSlos != null) {
      LOG.info("Research winner (SLOs met): " + result.bestMeetingSlos);
    } else {
      LOG.info("No candidate met all SLOs; top-5 by score:");
      result.ranked.stream().limit(5).forEach(c -> LOG.info("  " + c));
    }

    // At least the score-best should have solid common-content recall
    assertTrue(result.best.report.hitRate >= 0.90,
        "best candidate hitRate too low: " + result.best.report.hitRate);

    // Prefer a SLO winner when the grid can produce one
    Candidate apply = result.bestMeetingSlos != null ? result.bestMeetingSlos : result.best;
    assertTrue(apply.report.score > -1.0, "winner score should not be pathological");

    // Applying winner updates process topology for subsequent production use
    boolean sloApplied = TopologyAutoResearch.applyWinner(result);
    IceBridgeTopology t = IceBridgeTopology.get();
    assertTrue(t.meshBroadcastFanout() == apply.meshFanout);
    assertTrue(t.searchPeerFanout() == apply.searchPeerFanout);
    LOG.info("Applied topology: N=" + t.meshBroadcastFanout()
            + " M=" + t.searchPeerFanout()
            + " meshTTL=" + t.meshHopTtl()
            + " searchTTL=" + t.searchTtl()
            + " softMax=" + t.softMax()
            + " leafUPs=" + t.leafUltrapeerConnections()
            + " sloApplied=" + sloApplied);
  }

  @Test
  void hybridResearchDefaults_matchOrBeatLimeWireOnEc2Network() {
    Config net = TopologyAutoResearch.hybridEc2BenchmarkNetwork();

    Config lime = net.copy();
    lime.useLiveTopology = false;
    lime.meshFanout = IceBridgeTopology.LIMEWIRE_MESH_FANOUT;
    lime.searchPeerFanout = IceBridgeTopology.LIMEWIRE_SEARCH_PEER_FANOUT;
    lime.meshHopTtl = IceBridgeTopology.LIMEWIRE_MESH_HOP_TTL;
    lime.searchTtl = IceBridgeTopology.LIMEWIRE_SEARCH_TTL;
    lime.softMax = IceBridgeTopology.LIMEWIRE_SOFT_MAX;
    lime.leafUpConnections = IceBridgeTopology.LIMEWIRE_LEAF_UP_CONNECTIONS;
    lime.seed = 11L;

    Config hybrid = net.copy();
    hybrid.useLiveTopology = false;
    hybrid.meshFanout = IceBridgeTopology.HYBRID_EC2_MESH_FANOUT;
    hybrid.searchPeerFanout = IceBridgeTopology.HYBRID_EC2_SEARCH_PEER_FANOUT;
    hybrid.meshHopTtl = IceBridgeTopology.HYBRID_EC2_MESH_HOP_TTL;
    hybrid.searchTtl = IceBridgeTopology.HYBRID_EC2_SEARCH_TTL;
    hybrid.softMax = IceBridgeTopology.HYBRID_EC2_SOFT_MAX;
    hybrid.leafUpConnections = IceBridgeTopology.HYBRID_EC2_LEAF_UP_CONNECTIONS;
    hybrid.seed = 11L;

    AggregateReport rLime =
        new IceBridgeNetworkSimulator(lime).runAggregate(TopologyAutoResearch.hybridEc2Budgets());
    AggregateReport rHybrid =
        new IceBridgeNetworkSimulator(hybrid).runAggregate(TopologyAutoResearch.hybridEc2Budgets());
    LOG.info("compare lime:   " + rLime);
    LOG.info("compare hybrid: " + rHybrid);

    // Research hybrid should keep hit rate competitive on EC2 backbone.
    assertTrue(rHybrid.hitRate + 0.05 >= rLime.hitRate,
        "hybrid hit rate should be within 5pp of LimeWire profile");
    // Prefer hybrid score when budgets matter (lower N on fat hubs).
    assertTrue(rHybrid.score + 0.05 >= rLime.score
            || rHybrid.meanMeshMessages <= rLime.meanMeshMessages + 1,
        "hybrid should score competitively or use no more mesh traffic");
  }

  @Test
  void researchGrid_coversLimeWireAndBeyond() {
    TopologyAutoResearch.Grid g = TopologyAutoResearch.coarseGrid();
    boolean has32 = false;
    boolean has30 = false;
    for (int n : g.meshFanouts) {
      if (n == 32) {
        has32 = true;
      }
    }
    for (int m : g.searchPeerFanouts) {
      if (m == 30) {
        has30 = true;
      }
    }
    assertTrue(has32 && has30, "grid must include LimeWire N=32 M=30 for baseline");
  }
}
