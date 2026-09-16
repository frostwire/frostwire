/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.obs;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.event.IceBridgeEvent;
import com.frostwire.search.relay.event.IceBridgeEventLog;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * MCP tool to tail the most recent IceBridge / distributed-search events. Handy for watching a live
 * desktop over MCP.
 */
public class LogsTailTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_logs_tail";
  }

  @Override
  public String description() {
    return "Return the most recent IceBridge/distributed-search events, newest first (default 100, clamped to 1-1000).";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject properties = new JsonObject();

    JsonObject limit = new JsonObject();
    limit.addProperty("type", "integer");
    limit.addProperty(
        "description", "Number of most recent events, clamped to 1-1000 (default 100).");
    properties.add("limit", limit);

    schema.add("properties", properties);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      int limit =
          LogsQueryTool.clampLimit(
              LogsQueryTool.optLong(arguments, "limit", LogsQueryTool.DEFAULT_LIMIT));
      List<IceBridgeEvent> events = IceBridgeEventLog.instance().tail(limit);
      out.addProperty("count", events.size());
      out.add("events", LogsQueryTool.toJsonArray(events));
    } catch (Throwable t) {
      out.addProperty("error", String.valueOf(t));
    }
    return out;
  }
}
