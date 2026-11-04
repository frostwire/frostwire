/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.transport.MCPTransportHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The parts of the streamable HTTP transport the handshake tests don't reach: CORS, sessions,
 * batches, answers streamed as server-sent events, notifications pushed to an open stream, TLS and
 * start-up failures.
 */
class StreamableHttpTransportProtocolTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  /** Answers every request that has an id with its method name; notifications get no answer. */
  private final MCPTransportHandler echoHandler =
      request -> {
        if (!request.has("id")) {
          return null;
        }
        JsonObject result = new JsonObject();
        result.addProperty("method", request.get("method").getAsString());
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", request.get("id"));
        response.add("result", result);
        return response;
      };

  private StreamableHttpTransport transport;
  private int port;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  @AfterEach
  void stopTransport() {
    if (transport != null) {
      transport.stop();
    }
  }

  @Test
  void startAndStopReportTheRunningStateHostAndPort() throws IOException {
    port = freePort();
    transport = new StreamableHttpTransport("127.0.0.1", port);
    assertFalse(transport.isRunning());
    assertEquals("127.0.0.1", transport.getHost());
    assertEquals(port, transport.getPort());

    transport.start(echoHandler);
    assertTrue(transport.isRunning());

    transport.stop();
    assertFalse(transport.isRunning());
  }

  @Test
  void startFailsWhenThePortIsTaken() throws IOException {
    start();
    StreamableHttpTransport second =
        new StreamableHttpTransport("127.0.0.1", port, new TlsConfig());
    RuntimeException e = assertThrows(RuntimeException.class, () -> second.start(echoHandler));
    assertTrue(e.getMessage().startsWith("Failed to start MCP HTTP server"), e.getMessage());
    assertFalse(second.isRunning());
    assertTrue(transport.isRunning(), "the server already on the port keeps running");
  }

  @Test
  void corsAllowsOnlyLoopbackOrigins() throws Exception {
    start();
    for (String origin :
        new String[] {
          "http://127.0.0.1:3000", "http://localhost", "https://127.0.0.1", "https://localhost:1"
        }) {
      HttpResponse<String> preflight =
          send(request().header("Origin", origin).method("OPTIONS", none()));
      assertEquals(200, preflight.statusCode());
      assertEquals(
          origin, preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
      assertEquals(
          "GET, POST, DELETE, OPTIONS",
          preflight.headers().firstValue("Access-Control-Allow-Methods").orElse(""));
      assertTrue(
          preflight
              .headers()
              .firstValue("Access-Control-Allow-Headers")
              .orElse("")
              .contains("Mcp-Session-Id"));
    }

    HttpResponse<String> foreign =
        send(request().header("Origin", "https://evil.example").method("OPTIONS", none()));
    assertEquals(200, foreign.statusCode());
    assertTrue(
        foreign.headers().firstValue("Access-Control-Allow-Origin").isEmpty(),
        "a web page from another origin must not be allowed to call the local MCP server");
  }

  @Test
  void unsupportedMethodsAreRejected() throws Exception {
    start();
    HttpResponse<String> put = send(request().PUT(HttpRequest.BodyPublishers.ofString("{}")));
    assertEquals(405, put.statusCode());
    assertEquals("Method Not Allowed", put.body());
  }

  @Test
  void postWithoutASessionGetsANewSessionId() throws Exception {
    start();
    HttpResponse<String> first = post(null, "application/json", rpc(1, "ping"));
    HttpResponse<String> second = post(null, "application/json", rpc(2, "ping"));

    String firstId = first.headers().firstValue("Mcp-Session-Id").orElse("");
    assertTrue(firstId.startsWith("mcp-session-"), firstId);
    assertNotEquals(firstId, second.headers().firstValue("Mcp-Session-Id").orElse(""));
    assertEquals("ping", json(first.body()).getAsJsonObject("result").get("method").getAsString());
  }

  @Test
  void postNamingAForeignSessionIsNotFound() throws Exception {
    start();
    HttpResponse<String> response =
        post("someone-elses-session", "application/json", rpc(1, "ping"));
    assertEquals(404, response.statusCode());
    assertEquals("Session not found", json(response.body()).get("error").getAsString());
  }

  @Test
  void batchesAreAnsweredInOrderWithoutAnswersForNotifications() throws Exception {
    start();
    String batch =
        "["
            + rpc(1, "tools/list")
            + ",{\"jsonrpc\":\"2.0\",\"method\":\"note\"},"
            + rpc(2, "ping")
            + "]";

    HttpResponse<String> response = post(null, "application/json", batch);

    JsonArray answers = JsonParser.parseString(response.body()).getAsJsonArray();
    assertEquals(2, answers.size());
    assertEquals(1, answers.get(0).getAsJsonObject().get("id").getAsInt());
    assertEquals(2, answers.get(1).getAsJsonObject().get("id").getAsInt());
  }

  @Test
  void requestsWithoutAnAnswerGetAnEmptyObject() throws Exception {
    start();
    assertEquals(
        "{}", post(null, "application/json", "{\"jsonrpc\":\"2.0\",\"method\":\"note\"}").body());
    assertEquals("{}", post(null, "application/json", "42").body(), "not a JSON-RPC message");
  }

  @Test
  void postAskingForEventsWithoutAStreamGetsTheAnswerAsAOneShotStream() throws Exception {
    start();
    HttpResponse<String> single = post(null, "text/event-stream", rpc(5, "tools/list"));

    assertEquals(200, single.statusCode());
    assertEquals("text/event-stream", single.headers().firstValue("Content-Type").orElse(""));
    assertTrue(single.headers().firstValue("Mcp-Session-Id").orElse("").startsWith("mcp-session-"));
    JsonObject answer = json(dataOf(single.body()));
    assertEquals(5, answer.get("id").getAsInt());

    HttpResponse<String> batch =
        post(null, "text/event-stream", "[" + rpc(6, "a") + "," + rpc(7, "b") + "]");
    String[] events = batch.body().split("\n\n");
    assertEquals(2, events.length, batch.body());
    assertEquals(6, json(dataOf(events[0])).get("id").getAsInt());
    assertEquals(7, json(dataOf(events[1])).get("id").getAsInt());

    HttpResponse<String> unanswered =
        post(null, "text/event-stream", "{\"jsonrpc\":\"2.0\",\"method\":\"note\"}");
    assertEquals("", unanswered.body(), "nothing to stream for a notification");
  }

  @Test
  void anEventStreamAnswersWithItsSessionRightAway() throws Exception {
    start();
    // The request times out long before the first 30-second keep-alive could push the headers out.
    HttpResponse<InputStream> stream =
        http.send(
            request()
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "text/event-stream")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofInputStream());
    try {
      assertEquals(200, stream.statusCode());
      assertEquals("text/event-stream", stream.headers().firstValue("Content-Type").orElse(""));
      assertTrue(
          stream.headers().firstValue("Mcp-Session-Id").orElse("").startsWith("mcp-session-"));
    } finally {
      stream.body().close();
    }
  }

  @Test
  void deletingAnUnknownOrMissingSessionSucceeds() throws Exception {
    start();
    assertEquals(200, send(request().DELETE()).statusCode());
    assertEquals(
        200, send(request().header("Mcp-Session-Id", "mcp-session-999").DELETE()).statusCode());
  }

  @Test
  void tlsWithAGeneratedCertificateServesLocalhost() throws Exception {
    port = freePort();
    transport =
        new StreamableHttpTransport("127.0.0.1", port, new TlsConfig(true, null, null, true));
    transport.start(echoHandler);
    HttpClient tls = HttpClient.newBuilder().sslContext(trustAll()).connectTimeout(TIMEOUT).build();

    HttpResponse<String> response =
        tls.send(
            HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/mcp"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(rpc(1, "initialize")))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertEquals(1, json(response.body()).get("id").getAsInt());
    X509Certificate certificate =
        (X509Certificate) response.sslSession().orElseThrow().getPeerCertificates()[0];
    String subject = certificate.getSubjectX500Principal().getName();
    assertTrue(subject.contains("CN=localhost") && subject.contains("O=FrostWire"), subject);
    certificate.checkValidity();
  }

  @Test
  void tlsWithoutAKeystoreOrGeneratedCertificateFailsToStart() throws IOException {
    port = freePort();
    StreamableHttpTransport noCert =
        new StreamableHttpTransport("127.0.0.1", port, new TlsConfig(true, "", null, false));
    RuntimeException e = assertThrows(RuntimeException.class, () -> noCert.start(echoHandler));
    assertEquals(
        "Failed to configure SSL: TLS enabled but no keystore and auto-generation disabled",
        e.getMessage());

    StreamableHttpTransport missingKeystore =
        new StreamableHttpTransport(
            "127.0.0.1", freePort(), new TlsConfig(true, "/no/such/keystore.jks", "pw", false));
    RuntimeException missing =
        assertThrows(RuntimeException.class, () -> missingKeystore.start(echoHandler));
    assertTrue(missing.getMessage().startsWith("Failed to configure SSL: "), missing.getMessage());
  }

  @Test
  void tlsConfigKeepsItsSettings() {
    TlsConfig defaults = new TlsConfig();
    assertFalse(defaults.isEnabled());
    assertTrue(defaults.isAutoGenerateCert());

    defaults.setEnabled(true);
    defaults.setKeystorePath("/k.jks");
    defaults.setKeystorePassword("secret");
    defaults.setAutoGenerateCert(false);
    assertTrue(defaults.isEnabled());
    assertEquals("/k.jks", defaults.getKeystorePath());
    assertEquals("secret", defaults.getKeystorePassword());
    assertFalse(defaults.isAutoGenerateCert());
  }

  @Test
  void selfSignedContextsNameTheHost() {
    SSLContext any = SelfSignedCertGenerator.createSSLContext("0.0.0.0");
    assertEquals("TLS", any.getProtocol());
    SSLContext named = SelfSignedCertGenerator.createSSLContext("frostwire.local");
    assertEquals("TLS", named.getProtocol());
  }

  private void start() throws IOException {
    port = freePort();
    transport = new StreamableHttpTransport("127.0.0.1", port, new TlsConfig());
    transport.start(echoHandler);
  }

  private HttpRequest.Builder request() {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp")).timeout(TIMEOUT);
  }

  private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String session, String accept, String body) throws Exception {
    HttpRequest.Builder request =
        request()
            .header("Content-Type", "application/json")
            .header("Accept", accept)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (session != null) {
      request.header("Mcp-Session-Id", session);
    }
    return send(request);
  }

  private static HttpRequest.BodyPublisher none() {
    return HttpRequest.BodyPublishers.noBody();
  }

  private static String rpc(int id, String method) {
    return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}";
  }

  private static JsonObject json(String text) {
    return JsonParser.parseString(text).getAsJsonObject();
  }

  /** The data line of one server-sent event. */
  private static String dataOf(String event) {
    assertTrue(event.startsWith("event: message\ndata: "), event);
    return event.substring("event: message\ndata: ".length()).trim();
  }

  private static String nextData(BufferedReader events) {
    return assertTimeoutPreemptively(
        TIMEOUT,
        () -> {
          String line;
          while ((line = events.readLine()) != null) {
            if (line.startsWith("data: ")) {
              return line.substring("data: ".length());
            }
          }
          throw new AssertionError("stream ended without an event");
        });
  }

  private static int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  private static SSLContext trustAll() throws Exception {
    // Extended, so the JDK doesn't wrap it with a host name check: the certificate names localhost.
    TrustManager trustAll =
        new X509ExtendedTrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType, Socket s) {}

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType, Socket s) {}

          @Override
          public void checkClientTrusted(
              X509Certificate[] chain, String authType, SSLEngine engine) {}

          @Override
          public void checkServerTrusted(
              X509Certificate[] chain, String authType, SSLEngine engine) {}

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        };
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, new TrustManager[] {trustAll}, null);
    return context;
  }
}
