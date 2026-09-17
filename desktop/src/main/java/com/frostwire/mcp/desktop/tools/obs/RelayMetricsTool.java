/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.obs;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.event.IceBridgeEventLog;
import com.frostwire.util.Hex;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;

/**
 * MCP tool that aggregates IceBridge / distributed-search relay metrics: peer directory health,
 * identity presence, local index presence and event-log state. Never throws; reports failures via
 * an {@code error} property.
 */
public class RelayMetricsTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_relay_metrics";
  }

  @Override
  public String description() {
    return "Returns aggregated IceBridge/distributed-search relay metrics: readiness, peer directory size, live peers, peers with digest, event log size/enabled, identity and local index presence.";
  }

  @Override
  public JsonObject inputSchema() {
    // no args
    return new JsonObject();
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      IceBridgeEventLog log = IceBridgeEventLog.instance();

      PeerDirectory dir = null;
      boolean hasTransport = false;
      try {
        dir = SearchEngine.getDistributedPeerDirectory();
        hasTransport = SearchEngine.getDistributedSearchTransport() != null;
      } catch (Throwable ignore) {
        // Leave dir null / hasTransport false.
      }

      out.addProperty("distributed_ready", dir != null && hasTransport);
      out.addProperty("peer_directory_size", dir != null ? dir.size() : 0);
      out.addProperty("live_peers", dir != null ? dir.liveCount() : 0);
      out.addProperty("peers_with_digest", dir != null ? dir.digestCount() : 0);
      out.addProperty("log_size", log.size());
      out.addProperty("log_enabled", log.isEnabled());

      IdentityKeys identity = null;
      try {
        identity = SearchEngine.getDistributedIdentity();
      } catch (Throwable ignore) {
        // Leave identity null.
      }
      if (identity != null) {
        out.addProperty("has_identity", true);
        byte[] pub = identity.ed25519PubRaw();
        if (pub != null) {
          out.addProperty("pubkey_hex", Hex.encode(pub));
        }
      }

      boolean hasLocalIndex = false;
      try {
        hasLocalIndex = SearchEngine.getDistributedLocalIndex() != null;
      } catch (Throwable ignore) {
        // Leave hasLocalIndex false.
      }
      out.addProperty("has_local_index", hasLocalIndex);

      try {
        com.frostwire.search.relay.icebridge.IceBridgeMetrics metrics =
            SearchEngine.getDistributedTransportMetrics();
        if (metrics != null) {
          out.addProperty("transport_poll_runs", metrics.transportPollRunsCount());
          out.addProperty("transport_poll_errors", metrics.transportPollErrorsCount());
          out.addProperty("transport_drain_batches", metrics.transportPollDrainBatchesCount());
          out.addProperty("transport_messages_drained", metrics.transportMessagesDrainedCount());
          out.addProperty("transport_request_queue_depth", metrics.requestWorkQueueDepthGauge());
          out.addProperty("transport_request_rejected", metrics.requestWorkRejectedCount());
          out.addProperty("transport_verify_failures", metrics.verifyFailuresCount());
        }
      } catch (Throwable ignore) {
        // Transport metrics are optional diagnostics.
      }
    } catch (Throwable t) {
      out.addProperty("error", String.valueOf(t));
    }
    return out;
  }
}
