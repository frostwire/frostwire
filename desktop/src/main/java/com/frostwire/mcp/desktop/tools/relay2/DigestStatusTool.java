/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.PeerDirectory;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;

/** MCP tool summarizing the IceBridge peer directory and local-index readiness. */
public final class DigestStatusTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_digest_status";
  }

  @Override
  public String description() {
    return "Summarize the distributed peer directory and local index: peer counts, how many peers announced an index digest, and local index size.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", new JsonObject());
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      PeerDirectory dir = SearchEngine.getDistributedPeerDirectory();
      LocalIndex index = SearchEngine.getDistributedLocalIndex();

      out.addProperty("peer_directory_size", dir != null ? dir.size() : 0);
      out.addProperty("live", dir != null ? dir.liveCount() : 0);
      out.addProperty("peers_with_digest", dir != null ? dir.digestCount() : 0);
      out.addProperty("has_local_index", index != null);
      out.addProperty("local_index_size", index != null ? index.size() : 0);
      out.addProperty(
          "note",
          "peers_with_digest counts verified, live peers that announced an index digest; those peers are selected first for holder-aware search forwarding.");
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
