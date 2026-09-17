/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The relay metrics tool must expose the DISTRIBUTED transport's pipeline counters when wired. */
class RelayMetricsToolTest {

  @AfterEach
  void clearMetrics() {
    SearchEngine.setDistributedTransportMetrics(null);
  }

  @Test
  void reportsTransportPipelineCounters() {
    IceBridgeMetrics metrics = new IceBridgeMetrics();
    metrics.incrementTransportPollRuns(1);
    metrics.incrementTransportPollErrors(1);
    metrics.incrementTransportPollDrainBatches(1);
    metrics.incrementTransportMessagesDrained(1);
    metrics.incrementRequestWorkRejected(1);
    metrics.incrementVerifyFailures(1);
    metrics.setRequestWorkQueueDepthGauge(7);
    SearchEngine.setDistributedTransportMetrics(metrics);

    JsonObject out = new RelayMetricsTool().execute(new JsonObject());

    assertEquals(1, out.get("transport_poll_runs").getAsLong());
    assertEquals(1, out.get("transport_poll_errors").getAsLong());
    assertEquals(1, out.get("transport_drain_batches").getAsLong());
    assertEquals(1, out.get("transport_messages_drained").getAsLong());
    assertEquals(1, out.get("transport_request_rejected").getAsLong());
    assertEquals(1, out.get("transport_verify_failures").getAsLong());
    assertEquals(7, out.get("transport_request_queue_depth").getAsLong());
  }

  @Test
  void omitsTransportCountersWhenNotWired() {
    SearchEngine.setDistributedTransportMetrics(null);
    JsonObject out = new RelayMetricsTool().execute(new JsonObject());
    assertFalse(out.has("transport_poll_runs"));
    assertTrue(out.has("distributed_ready"));
  }
}
