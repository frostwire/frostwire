/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.PeerDirectory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.util.List;

/**
 * MCP tool to inspect peers/relayers discovered via DHT for the distributed search. Helps verify
 * that desktop finds IceBridge relayers and other peers via DHT topics.
 */
public class DiscoveredPeersTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_discovered_peers";
  }

  @Override
  public String description() {
    return "Returns top verified peers/relayers from the PeerDirectory (discovered via DHT peers + relays topics). Includes hostname, ports, verified status. Use to test DHT discovery of IceBridge nodes and relayers.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", new JsonObject()); // limit optional?
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      boolean ready = false;
      PeerDirectory dir = null;
      try {
        dir = SearchEngine.getDistributedPeerDirectory();
        ready = (dir != null) && SearchEngine.getDistributedSearchTransport() != null;
      } catch (Exception ignore) {
      }
      out.addProperty("distributed_ready", ready);
      if (dir != null) {
        out.addProperty("total_peers", dir.size());
        List<PeerDirectory.PeerInfo> top = dir.topByTrustVerified(20);
        JsonArray peers = new JsonArray();
        for (PeerDirectory.PeerInfo p : top) {
          JsonObject pj = new JsonObject();
          pj.addProperty("hostname", p.hostname());
          pj.addProperty("utp_port", p.utpPort());
          pj.addProperty("rudp_port", p.rudpPort());
          pj.addProperty("verified", p.isVerified());
          pj.addProperty("endorser_count", p.endorserCount());
          peers.add(pj);
        }
        out.add("top_verified_peers", peers);
        out.addProperty(
            "note",
            "Peers discovered via DHT (frostwire-peers-v1 + frostwire-relays-v1). Relayers have role in their IdentityRecord.");
      } else {
        out.addProperty("note", "Distributed peer directory not yet wired (start desktop fully).");
      }
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
