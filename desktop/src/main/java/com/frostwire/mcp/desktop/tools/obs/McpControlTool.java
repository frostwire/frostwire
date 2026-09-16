/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.obs;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.MCPStartupHook;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Locale;

/** MCP tool to control the embedded MCP server lifecycle: start, stop, restart or query status. */
public class McpControlTool implements MCPTool {

  private static final String ACTION_START = "start";
  private static final String ACTION_STOP = "stop";
  private static final String ACTION_RESTART = "restart";
  private static final String ACTION_STATUS = "status";

  @Override
  public String name() {
    return "frostwire_mcp_control";
  }

  @Override
  public String description() {
    return "Control the embedded MCP server lifecycle. action must be one of: start, stop, restart, status. Returns the resulting running state and url.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject properties = new JsonObject();

    JsonObject action = new JsonObject();
    action.addProperty("type", "string");
    JsonArray allowed = new JsonArray();
    allowed.add(ACTION_START);
    allowed.add(ACTION_STOP);
    allowed.add(ACTION_RESTART);
    allowed.add(ACTION_STATUS);
    action.add("enum", allowed);
    action.addProperty("description", "Lifecycle action to perform.");
    properties.add("action", action);

    schema.add("properties", properties);
    schema.add("required", requiredAction());
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    String action = LogsQueryTool.optString(arguments, "action");
    if (action == null || action.isBlank()) {
      out.addProperty("error", "missing required action (one of: start, stop, restart, status)");
      return out;
    }
    action = action.trim().toLowerCase(Locale.ROOT);

    try {
      switch (action) {
        case ACTION_START:
          MCPStartupHook.startServer();
          break;
        case ACTION_STOP:
          MCPStartupHook.stopServer();
          break;
        case ACTION_RESTART:
          MCPStartupHook.restartServer();
          break;
        case ACTION_STATUS:
          break;
        default:
          out.addProperty(
              "error", "invalid action '" + action + "' (one of: start, stop, restart, status)");
          return out;
      }
      out.addProperty("action", action);
      out.addProperty("running", MCPStartupHook.isRunning());
      out.addProperty("url", MCPStartupHook.getServerUrl());
    } catch (Throwable t) {
      out.addProperty("action", action);
      out.addProperty("error", String.valueOf(t));
    }
    return out;
  }

  private static JsonArray requiredAction() {
    JsonArray required = new JsonArray();
    required.add("action");
    return required;
  }
}
