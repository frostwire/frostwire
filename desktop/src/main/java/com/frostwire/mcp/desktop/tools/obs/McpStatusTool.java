/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.obs;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.MCPStartupHook;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.MCPSettings;

/**
 * MCP tool exposing the lifecycle status of the embedded MCP server: whether it is running, its
 * URL, and the static configuration backing it.
 */
public class McpStatusTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_mcp_status";
  }

  @Override
  public String description() {
    return "Report the embedded MCP server status: running, url, enabled, auto_start, host, port and tls.";
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
      out.addProperty("running", MCPStartupHook.isRunning());
      out.addProperty("url", MCPStartupHook.getServerUrl());
      out.addProperty("enabled", MCPSettings.MCP_SERVER_ENABLED.getValue());
      out.addProperty("auto_start", MCPSettings.MCP_SERVER_AUTO_START.getValue());
      out.addProperty("host", MCPSettings.MCP_SERVER_HOST.getValue());
      out.addProperty("port", MCPSettings.MCP_SERVER_PORT.getValue());
      out.addProperty("tls", MCPSettings.MCP_SERVER_TLS_ENABLED.getValue());
    } catch (Throwable t) {
      out.addProperty("error", String.valueOf(t));
    }
    return out;
  }
}
