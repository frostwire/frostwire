/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.mcp.transport.MCPTransportHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for StreamableHttpTransport to prevent regression on MCP handshake issues (initialize,
 * notifications/initialized expecting "accepted", SSE vs direct responses, content-types, no early
 * SSE data, session headers, etc.).
 *
 * <p>These cover the exact scenarios that caused "grok mcp doctor" to stall or fail with
 * "unexpected server response: expect accepted or json, got Sse(...)".
 */
class StreamableHttpTransportTest {

  private StreamableHttpTransport transport;
  private int port;
  private HttpClient httpClient;
  private String sessionId;

  private final MCPTransportHandler dummyHandler =
      req -> {
        String method = req.has("method") ? req.get("method").getAsString() : "";
        if ("initialize".equals(method)) {
          JsonObject result = new JsonObject();
          result.addProperty("protocolVersion", "2025-03-26");
          result.add("capabilities", new JsonObject());
          result.add("serverInfo", new JsonObject());
          JsonObject resp = new JsonObject();
          resp.addProperty("jsonrpc", "2.0");
          if (req.has("id")) resp.add("id", req.get("id"));
          resp.add("result", result);
          return resp;
        }
        if ("tools/list".equals(method)) {
          JsonObject toolsResult = new JsonObject();
          toolsResult.add("tools", new JsonArray()); // minimal valid
          JsonObject resp = new JsonObject();
          resp.addProperty("jsonrpc", "2.0");
          if (req.has("id")) resp.add("id", req.get("id"));
          resp.add("result", toolsResult);
          return resp;
        }
        return null; // for notifications etc.
      };

  @BeforeEach
  void setUp() throws IOException {
    port = freePort();
    transport = new StreamableHttpTransport("127.0.0.1", port, new TlsConfig());
    transport.start(dummyHandler);
    httpClient = HttpClient.newHttpClient();
    sessionId = null;
  }

  @AfterEach
  void tearDown() {
    if (transport != null) {
      transport.stop();
    }
  }

  private int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port + "/mcp";
  }

  /**
   * Opens the SSE stream to obtain Mcp-Session-Id (and verifies no early body data). Immediately
   * drains available bytes (expect none) and closes the client-side stream so the test thread does
   * not block on an open-ended chunked response, and the server side per-connection loop can exit
   * cleanly in tearDown.
   */
  private String connectSseForSession() throws Exception {
    HttpRequest getReq =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl()))
            .header("Accept", "text/event-stream")
            .GET()
            .build();

    HttpResponse<InputStream> resp =
        httpClient.send(getReq, HttpResponse.BodyHandlers.ofInputStream());

    assertEquals(200, resp.statusCode());
    assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"));
    String sid =
        resp.headers()
            .firstValue("Mcp-Session-Id")
            .orElseThrow(() -> new AssertionError("Missing Mcp-Session-Id header"));

    // No initial body data (critical: prevents clients from seeing Sse(...) as the initialize
    // response)
    InputStream is = resp.body();
    try {
      int avail = is.available();
      if (avail > 0) {
        byte[] buf = is.readNBytes(Math.min(avail, 1024));
        String early = new String(buf, StandardCharsets.UTF_8);
        assertTrue(
            early.trim().isEmpty() || early.length() < 5,
            "SSE open should send no initial data/comments: got '" + early + "'");
      }
    } finally {
      try {
        is.close();
      } catch (IOException ignored) {
      }
    }
    return sid;
  }

  @Test
  void testSseConnectReturnsSessionHeaderNoInitialData() throws Exception {
    sessionId = connectSseForSession();
    // Header + no early data already asserted inside helper. Re-assert session present.
    assertNotNull(sessionId);
    assertTrue(sessionId.startsWith("mcp-session-"));
  }

  @Test
  void testNotificationsInitializedReturnsAcceptedJsonBodyAndHeader() throws Exception {
    // First establish session with SSE GET (headers only, closed promptly to avoid blocking)
    sessionId = connectSseForSession();

    // Now send notifications/initialized POST (client may send with text/event-stream or
    // application/json)
    String initBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    HttpRequest postReq =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl()))
            .header("Mcp-Session-Id", sessionId)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream") // as real client does
            .POST(HttpRequest.BodyPublishers.ofString(initBody))
            .build();

    HttpResponse<String> postResp = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, postResp.statusCode());
    assertEquals("application/json", postResp.headers().firstValue("Content-Type").orElse(""));
    assertEquals(sessionId, postResp.headers().firstValue("Mcp-Session-Id").orElse(""));
    assertEquals(
        "\"accepted\"",
        postResp.body(),
        "Must return exactly the JSON string \"accepted\" for the initialized notification");
  }

  @Test
  void testInitializeGetsProperJsonRpcResponse() throws Exception {
    sessionId = connectSseForSession();

    String initReq =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
    HttpRequest post =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl()))
            .header("Mcp-Session-Id", sessionId)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(initReq))
            .build();

    HttpResponse<String> resp = httpClient.send(post, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, resp.statusCode());
    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
    assertEquals("2.0", json.get("jsonrpc").getAsString());
    assertEquals(1, json.get("id").getAsInt());
    assertTrue(json.has("result"));
    assertTrue(json.getAsJsonObject("result").has("protocolVersion"));
  }

  @Test
  void testToolsListAfterHandshakeGetsResponse() throws Exception {
    // Full minimal handshake
    sessionId = connectSseForSession();

    // initialize
    httpClient.send(
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl()))
            .header("Mcp-Session-Id", sessionId)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    // initialized
    httpClient.send(
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl()))
            .header("Mcp-Session-Id", sessionId)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    // tools/list
    HttpResponse<String> listResp =
        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(baseUrl()))
                .header("Mcp-Session-Id", sessionId)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(200, listResp.statusCode());
    JsonObject listJson = JsonParser.parseString(listResp.body()).getAsJsonObject();
    assertTrue(listJson.has("result"));
    assertTrue(listJson.getAsJsonObject("result").has("tools"));
  }

  @Test
  void testInitializedWithWantsSseStillGetsAcceptedOnPostBody() throws Exception {
    // Same as main test but emphasize the content-type on POST is json even if client asked sse
    sessionId = connectSseForSession();

    HttpRequest post =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl()))
            .header("Mcp-Session-Id", sessionId)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
            .build();

    HttpResponse<String> resp = httpClient.send(post, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, resp.statusCode());
    assertEquals("application/json", resp.headers().firstValue("Content-Type").orElse(""));
    assertEquals("\"accepted\"", resp.body());
  }
}
