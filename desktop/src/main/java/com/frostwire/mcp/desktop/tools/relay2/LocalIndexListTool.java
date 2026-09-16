/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.util.List;

/** MCP tool listing every torrent in this node's local shared index. */
public final class LocalIndexListTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_local_index_list";
  }

  @Override
  public String description() {
    return "List all torrents in this node's local distributed-search index (settings pane: local shared catalog), newest first.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();

    JsonObject limitProp = new JsonObject();
    limitProp.addProperty("type", "integer");
    limitProp.addProperty("description", "Maximum torrents to return (default 100, clamp 1..1000)");
    props.add("limit", limitProp);

    schema.add("properties", props);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      int limit = RelayToolSupport.clampInt(arguments, "limit", 100, 1, 1000);

      LocalIndex index = SearchEngine.getDistributedLocalIndex();
      if (index == null) {
        out.addProperty("error", "LocalIndex is not available (relay stack not wired)");
        return out;
      }

      List<LocalSharedTorrent> all = index.listAll();
      JsonArray torrents = new JsonArray();
      for (int i = 0; i < all.size() && i < limit; i++) {
        torrents.add(RelayToolSupport.torrentToJson(all.get(i)));
      }

      out.addProperty("count", torrents.size());
      out.add("torrents", torrents);
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
