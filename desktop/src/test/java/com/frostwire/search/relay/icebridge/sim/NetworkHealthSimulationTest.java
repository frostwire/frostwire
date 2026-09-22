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
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.WorkloadConfig;
import com.frostwire.util.Logger;
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
}
