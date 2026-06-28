/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.PeerDirectory;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;

/**
 * MCP tool to inspect the decentralized search / IceBridge relay stack state. Useful for live
 * testing via MCP when the desktop is running.
 */
public class RelayStatusTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_relay_status";
  }

  @Override
  public String description() {
    return "Returns readiness and basic stats for the DISTRIBUTED (IceBridge/rUDP) decentralized search stack. Includes peer count and identity presence.";
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
      SearchEngine dist = null;
      for (SearchEngine e : SearchEngine.getEngines()) {
        if ("Distributed".equals(e.getName())) {
          dist = e;
          break;
        }
      }
      boolean ready = dist != null && dist.isReady();
      out.addProperty("ready", ready);
      if (ready) {
        PeerDirectory dir = SearchEngine.getDistributedPeerDirectory();
        if (dir != null) {
          out.addProperty("peer_count", dir.size());
        }
        IdentityKeys id = SearchEngine.getDistributedIdentity();
        if (id != null) {
          out.addProperty("has_identity", true);
          byte[] pub = id.ed25519PubRaw();
          if (pub != null) {
            out.addProperty("pubkey_hex", com.frostwire.util.Hex.encode(pub));
          }
        }
        LocalIndex lidx = SearchEngine.getDistributedLocalIndex();
        out.addProperty("has_local_index", lidx != null);
      }
      out.addProperty(
          "note",
          "Distributed search uses local IceBridge daemon + DHT peer discovery + rUDP relay mesh.");
    } catch (Throwable t) {
      out.addProperty("error", String.valueOf(t));
    }
    return out;
  }
}
