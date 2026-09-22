/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkNode;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkSnapshot;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.WorkloadConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkVisualizationFrameTest {

  @Test
  void selectedNodeResolvesToLatestSnapshotCounters() {
    WorkloadConfig config = new WorkloadConfig();
    config.ultrapeerCount = 2;
    config.leafCount = 30;
    config.contentItems = 10;
    config.minUplinks = 1;
    config.maxUplinks = 1;
    config.searcherFraction = 0.5;
    config.searchesPerSearcher = 2;
    config.flooderFraction = 0;
    List<NetworkSnapshot> snapshots = new ArrayList<>();
    new IceBridgeWorkloadSimulator(config).run(snapshots::add);

    NetworkNode initial = NetworkVisualizationFrame.nodeById(snapshots.get(0), 0);
    NetworkNode current =
        NetworkVisualizationFrame.nodeById(snapshots.get(snapshots.size() - 1), 0);

    assertEquals(0, initial.messages);
    assertTrue(current.messages > initial.messages);
    assertEquals("UP-0", current.label);
  }
}
