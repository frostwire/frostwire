/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.obs;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.event.IceBridgeEventLog;
import com.google.gson.JsonObject;

/** MCP tool to clear the in-memory IceBridge / distributed-search event log. */
public class LogsClearTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_logs_clear";
  }

  @Override
  public String description() {
    return "Clear the in-memory IceBridge/distributed-search event log. Returns the remaining size (0 on success).";
  }

  @Override
  public JsonObject inputSchema() {
    // No arguments, but MCP requires an object schema: clients reject a tool without one.
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", new JsonObject());
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      IceBridgeEventLog log = IceBridgeEventLog.instance();
      log.clear();
      out.addProperty("cleared", true);
      out.addProperty("size", log.size());
    } catch (Throwable t) {
      out.addProperty("error", String.valueOf(t));
    }
    return out;
  }
}
