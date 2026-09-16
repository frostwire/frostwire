/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.util.Hex;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.util.List;

/** MCP tool exposing the IceBridge peer directory (Settings -&gt; IceBridge) as read-only JSON. */
public final class PeersListTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_peers_list";
  }

  @Override
  public String description() {
    return "List trusted IceBridge peers from the distributed PeerDirectory, including endpoints, trust, capabilities and whether each peer announced an index digest.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();

    JsonObject limitProp = new JsonObject();
    limitProp.addProperty("type", "integer");
    limitProp.addProperty("description", "Maximum peers to return (default 50, clamp 1..500)");
    props.add("limit", limitProp);

    JsonObject digestProp = new JsonObject();
    digestProp.addProperty("type", "boolean");
    digestProp.addProperty(
        "description", "Only include peers that announced an index digest (default false)");
    props.add("with_digest_only", digestProp);

    schema.add("properties", props);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      int limit = RelayToolSupport.clampInt(arguments, "limit", 50, 1, 500);
      boolean withDigestOnly = RelayToolSupport.boolArg(arguments, "with_digest_only", false);

      PeerDirectory dir = SearchEngine.getDistributedPeerDirectory();
      if (dir == null) {
        out.addProperty("error", "PeerDirectory is not available (relay stack not wired)");
        return out;
      }

      out.addProperty("total", dir.size());
      out.addProperty("live", dir.liveCount());
      out.addProperty("with_digest", dir.digestCount());

      List<PeerDirectory.PeerInfo> top = dir.topByTrustVerified(limit);
      JsonArray peers = new JsonArray();
      for (PeerDirectory.PeerInfo p : top) {
        byte[] pub = p.peerPub();
        boolean hasDigest = dir.hasIndexDigest(pub);
        if (withDigestOnly && !hasDigest) {
          continue;
        }
        JsonObject pj = new JsonObject();
        pj.addProperty("pub", Hex.encode(pub));
        pj.addProperty("host", p.hostname());
        pj.addProperty("rudp_port", p.rudpPort());
        pj.addProperty("utp_port", p.utpPort());
        pj.addProperty("verified", p.isVerified());
        pj.addProperty("spam", p.isSpam());
        pj.addProperty("endorsers", p.endorserCount());
        pj.addProperty("last_updated_ms", p.lastUpdatedMs());
        pj.addProperty("icebridge_version", p.icebridgeVersion());
        pj.addProperty("has_digest", hasDigest);
        pj.addProperty("capabilities", p.capabilities());
        peers.add(pj);
      }
      out.add("peers", peers);
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
