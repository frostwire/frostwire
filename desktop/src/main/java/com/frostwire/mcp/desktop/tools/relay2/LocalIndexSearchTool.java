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

/** MCP tool searching this node's local distributed-search index. */
public final class LocalIndexSearchTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_local_index_search";
  }

  @Override
  public String description() {
    return "Search this node's local distributed-search index for torrents matching a query.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();

    JsonObject queryProp = new JsonObject();
    queryProp.addProperty("type", "string");
    queryProp.addProperty("description", "Search query (non-empty)");
    props.add("q", queryProp);

    JsonObject limitProp = new JsonObject();
    limitProp.addProperty("type", "integer");
    limitProp.addProperty("description", "Maximum results to return (default 50, clamp 1..200)");
    props.add("limit", limitProp);

    schema.add("properties", props);

    JsonArray required = new JsonArray();
    required.add("q");
    schema.add("required", required);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      String query = RelayToolSupport.stringArg(arguments, "q");
      if (query == null || query.trim().isEmpty()) {
        out.addProperty("error", "Missing required parameter: q");
        return out;
      }

      int limit = RelayToolSupport.clampInt(arguments, "limit", 50, 1, 200);

      LocalIndex index = SearchEngine.getDistributedLocalIndex();
      if (index == null) {
        out.addProperty("error", "LocalIndex is not available (relay stack not wired)");
        return out;
      }

      List<LocalSharedTorrent> matches = index.search(query, limit);
      JsonArray results = new JsonArray();
      if (matches != null) {
        for (LocalSharedTorrent t : matches) {
          results.add(RelayToolSupport.torrentToJson(t));
        }
      }

      out.addProperty("query", query);
      out.addProperty("count", results.size());
      out.add("results", results);
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
