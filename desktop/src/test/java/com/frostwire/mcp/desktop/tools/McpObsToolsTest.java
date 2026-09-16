/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.tools.obs.McpControlTool;
import com.frostwire.mcp.desktop.tools.obs.McpStatusTool;
import com.frostwire.mcp.desktop.tools.obs.RemoteLogsQueryTool;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for the remote-logs and MCP lifecycle observability tools. Every tool must return a
 * JSON object from {@code execute} without throwing even when the remote relay is not configured or
 * arguments are missing.
 */
class McpObsToolsTest {

  private static List<MCPTool> tools() {
    return List.of(new RemoteLogsQueryTool(), new McpStatusTool(), new McpControlTool());
  }

  @Test
  void everyToolAdvertisesMetadataAndReturnsJson() {
    for (MCPTool tool : tools()) {
      assertNotNull(tool.name(), "name");
      assertTrue(tool.name().startsWith("frostwire_"), "tool name prefix: " + tool.name());
      assertNotNull(tool.description(), "description for " + tool.name());
      assertNotNull(tool.inputSchema(), "inputSchema for " + tool.name());
      JsonObject out = tool.execute(new JsonObject());
      assertNotNull(out, tool.name() + " returned null");
      assertTrue(
          out.has("error") || out.size() > 0,
          tool.name() + " must return data or an explicit error");
    }
  }

  @Test
  void mcpControlWithoutActionReturnsError() {
    JsonObject out = new McpControlTool().execute(new JsonObject());
    assertNotNull(out);
    assertTrue(out.has("error"), "expected an explicit error, got: " + out);
    assertTrue(
        out.get("error").getAsString().contains("action"),
        "error should mention the action argument: " + out);
  }

  @Test
  void mcpControlRejectsUnknownAction() {
    JsonObject arguments = new JsonObject();
    arguments.addProperty("action", "explode");
    JsonObject out = new McpControlTool().execute(arguments);
    assertNotNull(out);
    assertTrue(out.has("error"), "expected an explicit error, got: " + out);
  }
}
