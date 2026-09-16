/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.SettingsAdapter;
import com.frostwire.mcp.desktop.tools.relay2.HostCacheAddTool;
import com.frostwire.mcp.desktop.tools.relay2.HostCacheListTool;
import com.frostwire.mcp.desktop.tools.relay2.HostCachePingTool;
import com.frostwire.mcp.desktop.tools.settings.SettingsGetTool;
import com.frostwire.mcp.desktop.tools.settings.SettingsSetTool;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for the IceBridge host-cache MCP tools and the IceBridge settings surface. Every new
 * tool must return a JSON object from {@code execute} without throwing, even when arguments are
 * missing. The remote auth token must never be exposed or writable through MCP.
 */
class HostCacheMcpToolsSmokeTest {

  private static List<MCPTool> newHostCacheTools() {
    return List.of(new HostCacheListTool(), new HostCachePingTool(), new HostCacheAddTool());
  }

  @Test
  void everyNewToolAdvertisesMetadataAndReturnsJson() {
    for (MCPTool tool : newHostCacheTools()) {
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
  void hostCacheAddRejectsMissingArguments() {
    JsonObject noArgs = new HostCacheAddTool().execute(new JsonObject());
    assertTrue(noArgs.has("error"), "missing host/port must yield an error: " + noArgs);

    JsonObject noHost = new JsonObject();
    noHost.addProperty("port", 6888);
    JsonObject missingHost = new HostCacheAddTool().execute(noHost);
    assertTrue(missingHost.has("error"), "missing host must yield an error: " + missingHost);

    JsonObject noPort = new JsonObject();
    noPort.addProperty("host", "127.0.0.1");
    JsonObject missingPort = new HostCacheAddTool().execute(noPort);
    assertTrue(missingPort.has("error"), "missing port must yield an error: " + missingPort);
  }

  @Test
  void hostCacheListReturnsCountAndEntries() {
    JsonObject out = new HostCacheListTool().execute(new JsonObject());
    assertNotNull(out);
    assertTrue(out.has("count"), "list must report count: " + out);
    assertTrue(out.has("entries"), "list must report entries: " + out);
  }

  @Test
  void icebridgeCategoryHidesAuthToken() {
    JsonObject settings = SettingsAdapter.getCategorySettings("icebridge");
    assertFalse(settings.has("error"), "icebridge category must be known: " + settings);
    assertFalse(
        settings.has("ICEBRIDGE_REMOTE_AUTH_TOKEN"),
        "raw auth token must never be exposed: " + settings);
    assertTrue(
        settings.has("ICEBRIDGE_REMOTE_AUTH_TOKEN_SET"),
        "auth token set/unset flag must be present: " + settings);
    assertTrue(settings.has("ICEBRIDGE_ENABLED"));
    assertTrue(settings.has("ICEBRIDGE_ROLE"));
    assertTrue(settings.has("ICEBRIDGE_RUDP_PORT"));

    JsonObject viaTool = new JsonObject();
    viaTool.addProperty("category", "icebridge");
    JsonObject toolResult = new SettingsGetTool().execute(viaTool);
    assertNotNull(toolResult);
    assertFalse(toolResult.has("error"), "settings_get icebridge must succeed: " + toolResult);
    assertFalse(toolResult.has("ICEBRIDGE_REMOTE_AUTH_TOKEN"));
  }

  @Test
  void authTokenIsNotARegisteredOrWritableSetting() {
    assertFalse(
        SettingsAdapter.isKnownSetting("ICEBRIDGE_REMOTE_AUTH_TOKEN"),
        "auth token must not be a known/writable setting");
    assertTrue(SettingsAdapter.isKnownSetting("ICEBRIDGE_ROLE"));
    assertTrue(SettingsAdapter.isKnownSetting("ICEBRIDGE_ENABLED"));

    JsonObject attempt = new JsonObject();
    attempt.addProperty("key", "ICEBRIDGE_REMOTE_AUTH_TOKEN");
    attempt.addProperty("value", "secret");
    JsonObject rejected = new SettingsSetTool().execute(attempt);
    assertTrue(rejected.has("error"), "token write must be rejected: " + rejected);
  }
}
