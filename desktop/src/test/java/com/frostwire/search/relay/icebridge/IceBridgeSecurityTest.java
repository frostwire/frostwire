/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the IceBridge control API auth token (SEC5).
 *
 * <p>Verifies that:
 *
 * <ul>
 *   <li>/health is accessible without a token
 *   <li>All other endpoints reject requests without the correct token
 *   <li>Endpoints accept requests with the correct token
 * </ul>
 */
class IceBridgeSecurityTest {

  private ControlServer server;
  private String authToken;
  private HttpClient http;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    IdentityKeys identity = IdentityKeys.generate(0);
    IceBridgeConfig config =
        IceBridgeConfig.newBuilder()
            .controlHttpPort(freePort())
            .rudpPort(0)
            .role(IceBridgeConfig.Role.BOTH)
            .maxPeers(100)
            .peerTtlSec(120)
            .maxQpsPerKey(100.0)
            .build();
    PeerRegistry registry = new PeerRegistry(config);
    IceBridgeMetrics metrics = new IceBridgeMetrics();
    InboundMessageQueue queue = new InboundMessageQueue();
    RudpSessionManager rudp = new RudpSessionManager(identity, registry, metrics, queue);

    // Generate a real auth token (not null).
    byte[] tokenBytes = new byte[32];
    new java.security.SecureRandom().nextBytes(tokenBytes);
    authToken = com.frostwire.util.Hex.encode(tokenBytes);

    java.io.File tmpTokens = java.io.File.createTempFile("ice-test-tokens-", ".txt");
    tmpTokens.deleteOnExit();
    try (java.io.FileWriter fw = new java.io.FileWriter(tmpTokens)) {
      fw.write(authToken + "\n");
    }
    IceBridgeTokens tokens = new IceBridgeTokens(tmpTokens);
    server = new ControlServer(registry, metrics, config, rudp, queue, tokens);
    server.start();
    port = server.port();
    http = HttpClient.newHttpClient();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.close();
    }
  }

  @Test
  void healthAccessibleWithoutToken() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
  }

  @Test
  void lookupRejectedWithoutToken() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/lookup?count=10"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(401, response.statusCode(), "lookup should require auth token");
    assertTrue(
        response.body().contains("unauthorized"),
        "401 body must report unauthorized, got: " + response.body());
    com.frostwire.util.Logger.getLogger(IceBridgeSecurityTest.class)
        .info("control-api-unauthorized: " + response.body());
  }

  @Test
  void lookupAcceptedWithCorrectToken() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/lookup?count=10"))
                .header("X-IceBridge-Token", authToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), "lookup with correct token should succeed");
  }

  @Test
  void lookupRejectedWithWrongToken() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/lookup?count=10"))
                .header("X-IceBridge-Token", "wrong-token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(401, response.statusCode(), "lookup with wrong token should be rejected");
  }

  @Test
  void pollRejectedWithoutToken() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/poll?count=10"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(401, response.statusCode(), "poll should require auth token");
  }

  @Test
  void sendRejectedWithoutToken() throws Exception {
    String json =
        "{\"targetPub\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"payload\":\"dGVzdA\"}";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(401, response.statusCode(), "send should require auth token");
  }

  @Test
  void routeRejectedWithoutToken() throws Exception {
    String json =
        "{\"pub\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"host\":\"1.2.3.4\",\"rudpPort\":6889,\"role\":\"BOTH\"}";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/route"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(401, response.statusCode(), "route should require auth token");
  }

  @Test
  void metricsRejectedWithoutToken() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/metrics"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(401, response.statusCode(), "metrics should require auth token");
  }

  @Test
  void lookupRejectedWhenTokensFileEmpty() throws Exception {
    server.close();
    IdentityKeys identity = IdentityKeys.generate(0);
    IceBridgeConfig config =
        IceBridgeConfig.newBuilder()
            .controlHttpPort(freePort())
            .rudpPort(0)
            .role(IceBridgeConfig.Role.BOTH)
            .maxPeers(100)
            .peerTtlSec(120)
            .maxQpsPerKey(100.0)
            .build();
    PeerRegistry registry = new PeerRegistry(config);
    IceBridgeMetrics metrics = new IceBridgeMetrics();
    InboundMessageQueue queue = new InboundMessageQueue();
    RudpSessionManager rudp = new RudpSessionManager(identity, registry, metrics, queue);

    java.io.File emptyTokens = java.io.File.createTempFile("ice-empty-tokens-", ".txt");
    emptyTokens.deleteOnExit();
    IceBridgeTokens tokens = new IceBridgeTokens(emptyTokens);
    assertTrue(tokens.isEmpty(), "precondition: empty tokens file");

    server = new ControlServer(registry, metrics, config, rudp, queue, tokens);
    server.start();
    port = server.port();

    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/lookup?count=10"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(
        401,
        response.statusCode(),
        "lookup must be rejected when tokens file is empty and no token provided");
    assertTrue(
        response.body().contains("unauthorized"),
        "401 body must report unauthorized, got: " + response.body());
    com.frostwire.util.Logger.getLogger(IceBridgeSecurityTest.class)
        .info("control-api-unauthorized empty-tokens: " + response.body());
  }

  @Test
  void pollAcceptedWithCorrectToken() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/poll?count=10"))
                .header("X-IceBridge-Token", authToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), "poll with correct token should succeed");
  }

  private static int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  @Test
  void generateAndAddWorksWithCwdRelativeNoParentFile() {
    // Regression test: default tokens file "icebridge-tokens.txt" (and --generate-token)
    // constructs File with no directory component, so getParentFile() == null.
    // appendToFile must not NPE on mkdirs().
    String uniqueName = "icebridge-tokens-cwd-test-" + System.nanoTime() + ".txt";
    java.io.File cwdRel = new java.io.File(uniqueName);
    cwdRel.deleteOnExit();
    try {
      IceBridgeTokens tokens = new IceBridgeTokens(cwdRel);
      String token = tokens.generateAndAdd();
      assertNotNull(token, "token must be returned");
      assertEquals(64, token.length(), "token is 64 hex chars");
      assertTrue(tokens.isValid(token), "generated token must validate immediately");
      assertTrue(cwdRel.exists(), "tokens file must have been created/appended");
    } finally {
      cwdRel.delete();
    }
  }
}
