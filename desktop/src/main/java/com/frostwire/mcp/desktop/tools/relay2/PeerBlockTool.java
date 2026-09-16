/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.PeerDirectory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;

/** MCP tool to flag a peer as spam in the IceBridge peer directory. */
public final class PeerBlockTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_peer_block";
  }

  @Override
  public String description() {
    return "Mark a peer as spam in the distributed PeerDirectory by its 64-character hex ed25519 pubkey so future trust queries rank it negatively.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();

    JsonObject pubProp = new JsonObject();
    pubProp.addProperty("type", "string");
    pubProp.addProperty(
        "description", "64-character hex ed25519 pubkey of the peer to mark as spam");
    props.add("pub", pubProp);

    schema.add("properties", props);

    JsonArray required = new JsonArray();
    required.add("pub");
    schema.add("required", required);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      String hex = RelayToolSupport.stringArg(arguments, "pub");
      if (hex == null || hex.isEmpty()) {
        out.addProperty("error", "Missing required parameter: pub");
        return out;
      }

      byte[] pub;
      try {
        pub = RelayToolSupport.parsePub32(hex);
      } catch (IllegalArgumentException e) {
        out.addProperty("error", e.getMessage());
        return out;
      }

      PeerDirectory dir = SearchEngine.getDistributedPeerDirectory();
      if (dir == null) {
        out.addProperty("error", "PeerDirectory is not available (relay stack not wired)");
        return out;
      }

      dir.markSpam(pub);
      out.addProperty("blocked", true);
      out.addProperty("pub", hex);
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
