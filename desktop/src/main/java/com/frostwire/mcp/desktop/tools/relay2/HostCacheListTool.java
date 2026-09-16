/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.icebridge.IceBridgeHostCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** MCP tool exposing the persistent IceBridge relay host cache (Settings -&gt; IceBridge). */
public final class HostCacheListTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_hostcache_list";
  }

  @Override
  public String description() {
    return "List known IceBridge relay hosts from the local host cache, including port, role, last"
        + " successful ping and consecutive failure count.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();

    JsonObject limitProp = new JsonObject();
    limitProp.addProperty("type", "integer");
    limitProp.addProperty("description", "Maximum entries to return (default 50, clamp 1..500)");
    props.add("limit", limitProp);

    schema.add("properties", props);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      int limit = RelayToolSupport.clampInt(arguments, "limit", 50, 1, 500);

      IceBridgeHostCache cache = IceBridgeHostCache.getInstance();
      if (cache == null) {
        out.addProperty("error", "IceBridge host cache is not available");
        return out;
      }

      List<IceBridgeHostCache.Entry> all = cache.getAll();
      JsonArray entries = new JsonArray();
      for (int i = 0; i < all.size() && entries.size() < limit; i++) {
        IceBridgeHostCache.Entry e = all.get(i);
        if (e == null) {
          continue;
        }
        JsonObject ej = new JsonObject();
        ej.addProperty("host", e.host);
        ej.addProperty("port", e.port);
        ej.addProperty("role", e.role);
        ej.addProperty("last_success_ms", e.lastSuccessfulPingMs);
        ej.addProperty("failures", e.consecutiveFailures);
        entries.add(ej);
      }

      out.addProperty("count", entries.size());
      out.add("entries", entries);
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
