/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.HealthBudgets;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkHealthReport;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkSnapshot;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.WorkloadConfig;
import com.frostwire.util.Logger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkHealthSimulationTest {

  private static final Logger LOG = Logger.getLogger(NetworkHealthSimulationTest.class);

  @Test
  void twentyUltrapeersKeepMixedTrafficWithinHealthBudgets() {
    WorkloadConfig config = new WorkloadConfig();
    NetworkHealthReport report = new IceBridgeWorkloadSimulator(config).run();
    HealthBudgets budgets = new HealthBudgets();
    LOG.info(report.toString());

    assertEquals(20, report.ultrapeers);
    assertEquals(1_000, report.leaves);
    assertEquals(200, report.searchers);
    assertEquals(10, report.flooders);
    assertEquals(2_000, report.findableSearches);
    assertEquals(2_000, report.nonFindableSearches);
    assertEquals(1_000, report.floodAttempts);
    assertTrue(report.findableHitRate >= budgets.minFindableHitRate, report.toString());
    assertTrue(
        report.nonFindableP95Messages <= budgets.maxNonFindableP95Messages, report.toString());
    assertTrue(report.floodAdmissionRatio <= budgets.maxFloodAdmissionRatio, report.toString());
    assertTrue(report.amplificationRatio <= budgets.maxAmplificationRatio, report.toString());
    assertTrue(report.hottestHubShare <= budgets.maxHottestHubShare, report.toString());
    assertTrue(
        report.duplicateDeliveryRatio <= budgets.maxDuplicateDeliveryRatio, report.toString());
    assertTrue(report.meets(budgets), report.toString());
  }

  @Test
  void floodersAttachToSixteenDifferentHubsWhileHonestLeavesStaySmall() {
    WorkloadConfig config = new WorkloadConfig();
    List<NetworkSnapshot> snapshots = new ArrayList<>();

    NetworkHealthReport report = new IceBridgeWorkloadSimulator(config).run(snapshots::add);
    NetworkSnapshot initial = snapshots.get(0);

    assertEquals(10, initial.nodes.stream().filter(node -> node.flooder).count());
    assertTrue(
        initial.nodes.stream()
            .filter(node -> node.flooder)
            .allMatch(node -> node.connections == 16));
    assertTrue(
        initial.nodes.stream()
            .filter(node -> node.type == IceBridgeWorkloadSimulator.NodeType.LEAF && !node.flooder)
            .allMatch(node -> node.connections >= 3 && node.connections <= 6));
    assertEquals(16, report.config.flooderUplinks);
  }

  @Test
  void recallDependsOnDigestAvailabilityRatherThanKnowingTheTarget() {
    WorkloadConfig available = new WorkloadConfig();
    available.ultrapeerCount = 4;
    available.leafCount = 200;
    available.contentItems = 100;
    available.minUplinks = 1;
    available.maxUplinks = 1;
    available.maxHoldersPerItem = 1;
    available.searchPeerFanout = 4;
    available.holderBudget = 1;
    available.searcherFraction = 0.5;
    available.flooderFraction = 0;
    available.searchesPerSearcher = 4;
    available.digestCoverageFraction = 1;

    NetworkHealthReport withDigests = new IceBridgeWorkloadSimulator(available).run();
    available.digestCoverageFraction = 0;
    NetworkHealthReport withoutDigests = new IceBridgeWorkloadSimulator(available).run();

    assertTrue(
        withDigests.findableHitRate > withoutDigests.findableHitRate + 0.3,
        "Recall must fall when digest announcements disappear: "
            + withDigests.findableHitRate
            + " vs "
            + withoutDigests.findableHitRate);
  }

  @Test
  void independentHubsSendDuplicateWireRequestsBeforeReceiverDeduplicates() {
    WorkloadConfig config = new WorkloadConfig();
    config.ultrapeerCount = 4;
    config.leafCount = 100;
    config.contentItems = 20;
    config.minUplinks = 1;
    config.maxUplinks = 1;
    config.searchPeerFanout = 4;
    config.holderBudget = 1;
    config.searcherFraction = 0.1;
    config.flooderFraction = 0;
    config.searchesPerSearcher = 2;
    NetworkHealthReport report = new IceBridgeWorkloadSimulator(config).run();

    assertTrue(
        report.duplicateHubDeliveries > 0,
        "Independent senders should deliver repeat requests before receiver deduplication");
    assertTrue(report.hubForwardMessages > report.findableSearches + report.nonFindableSearches);
  }

  @Test
  void sampledTraceShowsOnlySentHopsAndOrdersResponsesAfterRequests() {
    WorkloadConfig config = new WorkloadConfig();
    config.ultrapeerCount = 2;
    config.leafCount = 40;
    config.contentItems = 10;
    config.minUplinks = 1;
    config.maxUplinks = 1;
    config.flooderFraction = 0.025;
    config.flooderBurst = 100;
    config.searcherFraction = 0.1;
    config.searchesPerSearcher = 2;
    List<NetworkSnapshot> snapshots = new ArrayList<>();

    new IceBridgeWorkloadSimulator(config).run(snapshots::add);

    NetworkSnapshot deniedFlood =
        snapshots.stream()
            .filter(snapshot -> snapshot.completedSearches == 50)
            .findFirst()
            .orElseThrow();
    assertTrue(
        deniedFlood.activity.stream().allMatch(hop -> hop.step == 0),
        "A denied request can only reach its entry uplink");
    assertTrue(
        deniedFlood.activity.stream()
            .allMatch(hop -> hop.kind == IceBridgeWorkloadSimulator.ActivityHop.Kind.FLOOD),
        "Flood traces must be labeled as attack traffic");
    List<IceBridgeWorkloadSimulator.ActivityHop.Kind> kinds =
        snapshots.stream()
            .flatMap(snapshot -> snapshot.activity.stream())
            .map(hop -> hop.kind)
            .toList();
    assertTrue(kinds.contains(IceBridgeWorkloadSimulator.ActivityHop.Kind.REQUEST));
    assertTrue(
        kinds.contains(IceBridgeWorkloadSimulator.ActivityHop.Kind.RESPONSE),
        "Result traffic must be visible as responses traveling back");
    assertTrue(
        snapshots.stream()
            .flatMap(snapshot -> snapshot.activity.stream())
            .filter(hop -> hop.kind == IceBridgeWorkloadSimulator.ActivityHop.Kind.RESPONSE)
            .allMatch(hop -> hop.step >= 2));
    assertTrue(snapshots.stream().allMatch(snapshot -> snapshot.activity.size() <= 48));
  }

  @Test
  void consecutiveRunsOnOneSimulatorHaveIdenticalOutcomes() {
    WorkloadConfig config = new WorkloadConfig();
    config.searchesPerSearcher = 2;
    IceBridgeWorkloadSimulator simulator = new IceBridgeWorkloadSimulator(config);

    NetworkHealthReport first = simulator.run();
    NetworkHealthReport second = simulator.run();

    assertEquals(first.findableHitRate, second.findableHitRate);
    assertEquals(first.totalSearchMessages, second.totalSearchMessages);
    assertEquals(first.duplicateHubDeliveries, second.duplicateHubDeliveries);
    assertEquals(first.floodAdmitted, second.floodAdmitted);
  }
}
