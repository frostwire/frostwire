/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.desktop.tools.obs.McpControlTool;
import com.frostwire.mcp.desktop.tools.obs.McpStatusTool;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limegroup.gnutella.settings.MCPSettings;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Starting, stopping and restarting the embedded MCP server, directly and through the
 * frostwire_mcp_control and frostwire_mcp_status tools. The server listens on a free loopback port
 * without TLS; the MCP settings are put back afterwards.
 */
class MCPStartupHookTest {

  @TempDir Path engineHome;

  private EngineContext engine;
  private boolean enabled;
  private boolean autoStart;
  private String host;
  private int port;
  private boolean tls;

  @BeforeEach
  void useAFreeLoopbackPort() throws IOException {
    enabled = MCPSettings.MCP_SERVER_ENABLED.getValue();
    autoStart = MCPSettings.MCP_SERVER_AUTO_START.getValue();
    host = MCPSettings.MCP_SERVER_HOST.getValue();
    port = MCPSettings.MCP_SERVER_PORT.getValue();
    tls = MCPSettings.MCP_SERVER_TLS_ENABLED.getValue();
    MCPSettings.MCP_SERVER_HOST.setValue("127.0.0.1");
    MCPSettings.MCP_SERVER_PORT.setValue(freePort());
    MCPSettings.MCP_SERVER_TLS_ENABLED.setValue(false);
    // Starting the server hooks its notifications into the engine, which needs startup's context.
    engine = EngineContext.install(engineHome.toFile());
  }

  @AfterEach
  void stopAndRestore() {
    MCPStartupHook.stopServer();
    engine.close();
    MCPSettings.MCP_SERVER_ENABLED.setValue(enabled);
    MCPSettings.MCP_SERVER_AUTO_START.setValue(autoStart);
    MCPSettings.MCP_SERVER_HOST.setValue(host);
    MCPSettings.MCP_SERVER_PORT.setValue(port);
    MCPSettings.MCP_SERVER_TLS_ENABLED.setValue(tls);
  }

  @Test
  void startedServerAnswersAgentsAtItsUrl() throws Exception {
    MCPStartupHook.startServer();
    assertTrue(MCPStartupHook.isRunning());
    MCPStartupHook.startServer(); // already running: nothing happens
    assertTrue(MCPStartupHook.isRunning());

    String url = MCPStartupHook.getServerUrl();
    assertEquals("http://127.0.0.1:" + MCPSettings.MCP_SERVER_PORT.getValue() + "/mcp", url);
    assertEquals(
        "frostwire", initialize(url).getAsJsonObject("serverInfo").get("name").getAsString());

    MCPStartupHook.stopServer();
    assertFalse(MCPStartupHook.isRunning());
    MCPStartupHook.stopServer(); // not running: nothing happens
  }

  @Test
  void urlUsesHttpsWhenTlsIsOn() {
    MCPSettings.MCP_SERVER_TLS_ENABLED.setValue(true);
    assertEquals(
        "https://127.0.0.1:" + MCPSettings.MCP_SERVER_PORT.getValue() + "/mcp",
        MCPStartupHook.getServerUrl());
  }

  @Test
  void initializeStartsTheServerOnlyWhenEnabled() {
    MCPSettings.MCP_SERVER_ENABLED.setValue(false);
    MCPSettings.MCP_SERVER_AUTO_START.setValue(false);
    MCPStartupHook.initialize();
    assertFalse(MCPStartupHook.isRunning());

    MCPSettings.MCP_SERVER_AUTO_START.setValue(true);
    MCPStartupHook.initialize();
    assertTrue(MCPStartupHook.isRunning());
  }

  @Test
  void startFailsCleanlyWhenThePortIsTaken() throws IOException {
    try (ServerSocket taken = new ServerSocket()) {
      taken.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
      MCPSettings.MCP_SERVER_PORT.setValue(taken.getLocalPort());

      Exception e = assertThrows(Exception.class, MCPStartupHook::startServer);
      assertTrue(e.getMessage().startsWith("Failed to start MCP server: "), e.getMessage());
      assertFalse(MCPStartupHook.isRunning(), "a failed start leaves no server behind");

      MCPSettings.MCP_SERVER_ENABLED.setValue(true);
      MCPStartupHook.initialize(); // logs the failure instead of throwing
      assertFalse(MCPStartupHook.isRunning());
    }
  }

  @Test
  void controlToolStartsRestartsAndStopsTheServer() throws Exception {
    McpControlTool control = new McpControlTool();

    JsonObject status = control.execute(action("STATUS"));
    assertEquals("status", status.get("action").getAsString());
    assertFalse(status.get("running").getAsBoolean());
    assertEquals(MCPStartupHook.getServerUrl(), status.get("url").getAsString());

    JsonObject started = control.execute(action("start"));
    assertTrue(started.get("running").getAsBoolean());
    assertEquals(
        "frostwire",
        initialize(MCPStartupHook.getServerUrl())
            .getAsJsonObject("serverInfo")
            .get("name")
            .getAsString());

    JsonObject restarted = control.execute(action(" restart "));
    assertEquals("restart", restarted.get("action").getAsString());
    assertTrue(restarted.get("running").getAsBoolean());
    assertEquals(
        "frostwire",
        initialize(MCPStartupHook.getServerUrl())
            .getAsJsonObject("serverInfo")
            .get("name")
            .getAsString());

    JsonObject stopped = control.execute(action("stop"));
    assertFalse(stopped.get("running").getAsBoolean());
  }

  @Test
  void controlToolReportsAFailedStart() throws IOException {
    try (ServerSocket taken = new ServerSocket()) {
      taken.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
      MCPSettings.MCP_SERVER_PORT.setValue(taken.getLocalPort());

      JsonObject out = new McpControlTool().execute(action("start"));

      assertEquals("start", out.get("action").getAsString());
      assertTrue(
          out.get("error").getAsString().contains("Failed to start MCP server"), out.toString());
    }
  }

  @Test
  void restartWaitsForThePortAndReportsWhenItStaysTaken() throws IOException {
    try (ServerSocket taken = new ServerSocket()) {
      taken.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
      MCPSettings.MCP_SERVER_PORT.setValue(taken.getLocalPort());

      long start = System.nanoTime();
      JsonObject out = new McpControlTool().execute(action("restart"));

      assertTrue(
          System.nanoTime() - start >= Duration.ofSeconds(3).toNanos(),
          "it gives the port three seconds to come free");
      assertEquals("restart", out.get("action").getAsString());
      assertTrue(
          out.get("error").getAsString().contains("Failed to start MCP server"), out.toString());
      assertFalse(MCPStartupHook.isRunning());
    }
  }

  @Test
  void statusToolReportsTheServerSettings() throws Exception {
    MCPSettings.MCP_SERVER_ENABLED.setValue(true);
    MCPSettings.MCP_SERVER_AUTO_START.setValue(false);
    MCPStartupHook.startServer();

    JsonObject status = new McpStatusTool().execute(new JsonObject());

    assertTrue(status.get("running").getAsBoolean());
    assertEquals(MCPStartupHook.getServerUrl(), status.get("url").getAsString());
    assertTrue(status.get("enabled").getAsBoolean());
    assertFalse(status.get("auto_start").getAsBoolean());
    assertEquals("127.0.0.1", status.get("host").getAsString());
    assertEquals(MCPSettings.MCP_SERVER_PORT.getValue(), status.get("port").getAsInt());
    assertFalse(status.get("tls").getAsBoolean());
  }

  private static JsonObject action(String action) {
    JsonObject args = new JsonObject();
    args.addProperty("action", action);
    return args;
  }

  private static JsonObject initialize(String url) throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    return JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
  }

  private static int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
