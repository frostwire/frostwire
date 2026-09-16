/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.MCPTool;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for the IceBridge observability/parity MCP tools: every tool must advertise a
 * name/description/schema and must return a JSON object from {@code execute} without throwing, even
 * when the relay stack is not wired (null directory/index) or arguments are missing.
 */
class IceBridgeMcpToolsSmokeTest {

  private static List<MCPTool> tools() {
    return List.of(
        new com.frostwire.mcp.desktop.tools.obs.LogsQueryTool(),
        new com.frostwire.mcp.desktop.tools.obs.LogsTailTool(),
        new com.frostwire.mcp.desktop.tools.obs.LogsClearTool(),
        new com.frostwire.mcp.desktop.tools.obs.RelayMetricsTool(),
        new com.frostwire.mcp.desktop.tools.relay2.PeersListTool(),
        new com.frostwire.mcp.desktop.tools.relay2.PeerRemoveTool(),
        new com.frostwire.mcp.desktop.tools.relay2.PeerBlockTool(),
        new com.frostwire.mcp.desktop.tools.relay2.PeerCatalogTool(),
        new com.frostwire.mcp.desktop.tools.relay2.LocalIndexListTool(),
        new com.frostwire.mcp.desktop.tools.relay2.LocalIndexSearchTool(),
        new com.frostwire.mcp.desktop.tools.relay2.DigestStatusTool());
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
  void toolsHaveUniqueNames() {
    java.util.Set<String> names = new java.util.HashSet<>();
    for (MCPTool tool : tools()) {
      assertTrue(names.add(tool.name()), "duplicate tool name: " + tool.name());
    }
    assertEquals(tools().size(), names.size());
  }

  @Test
  void logToolsRoundTripThroughTheSharedLog() {
    com.frostwire.search.relay.event.IceBridgeEventLog log =
        com.frostwire.search.relay.event.IceBridgeEventLog.instance();
    log.clear();
    log.setEnabled(true);

    MCPTool query = new com.frostwire.mcp.desktop.tools.obs.LogsQueryTool();
    com.frostwire.search.relay.event.IceBridgeEvents.search("aa", "hello miami");
    JsonObject result = query.execute(new JsonObject());
    assertTrue(
        result.get("count").getAsInt() >= 1, "query tool should see the emitted event: " + result);

    JsonObject cleared =
        new com.frostwire.mcp.desktop.tools.obs.LogsClearTool().execute(new JsonObject());
    assertTrue(cleared.get("cleared").getAsBoolean());
    assertEquals(0, log.size());
  }
}
