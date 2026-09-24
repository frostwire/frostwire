/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class LoopbackNetworkSimulationTest {

  @TempDir Path workDirectory;

  @Test
  @Timeout(90)
  void realServersExchangeSignedSearchAndReplyAcrossLoopbackPorts() throws Exception {
    LoopbackNetworkSimulator.Report report = new LoopbackNetworkSimulator().run(3, workDirectory);

    assertEquals(3, report.nodes());
    assertEquals(1, report.findableHits());
    assertEquals(0, report.unfindableHits());
    assertTrue(report.packetsIn() > 0);
    assertTrue(report.packetsOut() > 0);
    assertTrue(report.bytesOut() > 0);
    assertTrue(report.uniquePorts());
    assertTrue(report.digestsKnown() >= 1, "holder digest must arrive through the real transport");
    assertTrue(report.findableMillis() < 5_000, "findable search must finish on verified reply");
    assertTrue(report.unfindableMillis() < 5_000, "miss must finish on verified empty finals");
    assertTrue(LoopbackSimulationMain.html(report).contains("production request parsing"));
  }

  @Test
  @Timeout(120)
  void twentyFiveRealNodesBindIndependentlyAndProcessSearchTraffic() throws Exception {
    LoopbackNetworkSimulator.Report report = new LoopbackNetworkSimulator().run(25, workDirectory);

    assertEquals(25, report.nodes());
    assertEquals(1, report.findableHits());
    assertEquals(0, report.unfindableHits());
    assertTrue(report.uniquePorts());
    assertEquals(1, report.digestsKnown());
    assertTrue(report.packetsIn() > 200);
    assertTrue(report.packetsOut() > 200);
  }
}
