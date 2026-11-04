/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.MCPServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The stdio transport reads one JSON-RPC message per line and writes one answer per line, the
 * framing MCP clients use when they launch a server as a subprocess.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class StdioTransportTest {

  private PrintWriter client;
  private BufferedReader answers;
  private StdioTransport transport;
  private MCPServer server;

  @BeforeEach
  void connect() throws IOException {
    PipedOutputStream clientOut = new PipedOutputStream();
    PipedInputStream serverIn = new PipedInputStream(clientOut);
    PipedOutputStream serverOut = new PipedOutputStream();
    PipedInputStream clientIn = new PipedInputStream(serverOut, 1 << 16);
    client = new PrintWriter(clientOut, true, StandardCharsets.UTF_8);
    answers = new BufferedReader(new InputStreamReader(clientIn, StandardCharsets.UTF_8));
    transport = new StdioTransport(serverIn, serverOut);
    server = new MCPServer("test");
    server.setTransport(transport);
    server.start();
  }

  @AfterEach
  void disconnect() {
    server.stop();
    client.close();
  }

  @Test
  void eachRequestLineGetsOneAnswerLine() throws IOException {
    assertTrue(transport.isRunning());

    client.println("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}");
    JsonObject initialized = JsonParser.parseString(answers.readLine()).getAsJsonObject();
    assertEquals(1, initialized.get("id").getAsInt());
    assertEquals(
        "frostwire",
        initialized
            .getAsJsonObject("result")
            .getAsJsonObject("serverInfo")
            .get("name")
            .getAsString());

    client.println("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}");
    assertEquals(
        2, JsonParser.parseString(answers.readLine()).getAsJsonObject().get("id").getAsInt());
  }

  @Test
  void blankLinesAndNotificationsGetNoAnswer() throws IOException {
    client.println("   ");
    client.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
    client.println("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}");

    // The first line written back answers the ping: nothing was written for the other two.
    assertEquals(
        3, JsonParser.parseString(answers.readLine()).getAsJsonObject().get("id").getAsInt());
  }

  @Test
  void malformedLinesGetAParseError() throws IOException {
    client.println("{not json");

    JsonObject error = JsonParser.parseString(answers.readLine()).getAsJsonObject();
    assertEquals("2.0", error.get("jsonrpc").getAsString());
    assertTrue(error.has("id"), "JSON-RPC requires the id, null when the request was unreadable");
    assertTrue(error.get("id").isJsonNull());
    assertEquals(-32700, error.getAsJsonObject("error").get("code").getAsInt());
    assertTrue(
        error.getAsJsonObject("error").get("message").getAsString().startsWith("Parse error: "));

    client.println("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}");
    assertEquals(
        4,
        JsonParser.parseString(answers.readLine()).getAsJsonObject().get("id").getAsInt(),
        "the transport keeps reading after a bad line");
  }

  @Test
  void notificationsAreWrittenAsLines() throws IOException {
    JsonObject params = new JsonObject();
    params.addProperty("token", "7");
    server.sendNotification("notifications/search_completed", params);

    JsonObject notification = JsonParser.parseString(answers.readLine()).getAsJsonObject();
    assertEquals("notifications/search_completed", notification.get("method").getAsString());
    assertEquals("7", notification.getAsJsonObject("params").get("token").getAsString());
  }

  @Test
  void stopEndsTheTransport() {
    server.stop();
    assertFalse(transport.isRunning());
    assertNotNull(new StdioTransport(), "the default transport uses the process's stdin/stdout");
  }
}
