/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.event.IceBridgeEvent;
import com.frostwire.search.relay.event.IceBridgeEventLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reading a remote IceBridge relay's event log through MCP, and the local event log tools. The
 * remote relay is a local HTTP server; the relay settings are put back after each test.
 */
class RemoteLogsQueryToolTest {

  private HttpServer relay;
  private final AtomicReference<String> query = new AtomicReference<>();
  private final AtomicReference<String> token = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String body = "{\"data\":[]}";
  private boolean useRemote;
  private String remoteUrl;
  private String authToken;

  @BeforeEach
  void startRelay() throws IOException {
    useRemote = SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.getValue();
    remoteUrl = SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.getValue();
    authToken = SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.getValue();
    relay = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    relay.createContext(
        "/events",
        exchange -> {
          query.set(exchange.getRequestURI().getRawQuery());
          token.set(exchange.getRequestHeaders().getFirst("X-IceBridge-Token"));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    relay.start();
    SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.setValue(true);
    SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.setValue(
        "http://127.0.0.1:" + relay.getAddress().getPort() + "//");
    SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.setValue("");
  }

  @AfterEach
  void stopRelay() {
    relay.stop(0);
    SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.setValue(useRemote);
    SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.setValue(remoteUrl);
    SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.setValue(authToken);
  }

  @Test
  void eventsFromTheRemoteRelayAreReturnedWithTheFiltersPassedOn() {
    body =
        "{\"data\":["
            + "{\"timestampMs\":1000,\"level\":\"warn\",\"category\":\"forward\","
            + "\"peerPub\":\"abcdef0123456789\",\"message\":\"forwarded\"},"
            + "{\"ts\":2000,\"peer\":\"short\",\"category\":\"nonsense\",\"message\":\"old fields\"},"
            + "42,"
            + "{\"timestampMs\":null,\"level\":null,\"message\":7}"
            + "]}";
    JsonObject args = new JsonObject();
    args.addProperty("level", "warn");
    args.addProperty("category", "forward,peer");
    args.addProperty("q", "a b&c");
    args.addProperty("peer", "abc");
    args.addProperty("since", 500);
    args.addProperty("limit", 5);

    JsonObject out = new RemoteLogsQueryTool().execute(args);

    assertEquals(
        "level=warn&category=forward%2Cpeer&q=a+b%26c&peer=abc&since=500&limit=5", query.get());
    assertNull(token.get(), "no token configured, no token sent");
    assertEquals(3, out.get("count").getAsInt(), "non-object entries are skipped");
    JsonArray events = out.getAsJsonArray("events");
    JsonObject first = events.get(0).getAsJsonObject();
    assertEquals(1000, first.get("ts").getAsLong());
    assertEquals("WARN", first.get("level").getAsString());
    assertEquals("FORWARD", first.get("category").getAsString());
    assertEquals("abcdef012345", first.get("peer").getAsString(), "peers are shortened");
    assertEquals("forwarded", first.get("message").getAsString());
    JsonObject second = events.get(1).getAsJsonObject();
    assertEquals(2000, second.get("ts").getAsLong(), "older relays send ts");
    assertEquals("short", second.get("peer").getAsString(), "older relays send peer");
    assertEquals("GENERAL", second.get("category").getAsString());
    JsonObject third = events.get(2).getAsJsonObject();
    assertEquals(0, third.get("ts").getAsLong());
    assertEquals("DEBUG", third.get("level").getAsString());
    assertEquals("7", third.get("message").getAsString());
  }

  @Test
  void theAuthTokenIsSentWhenConfigured() {
    SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.setValue("  s3cret ");

    JsonObject out = new RemoteLogsQueryTool().execute(new JsonObject());

    assertEquals("s3cret", token.get());
    assertEquals("limit=100", query.get(), "the default limit, no filters");
    assertEquals(0, out.get("count").getAsInt());
  }

  @Test
  void aRelayErrorIsReported() {
    status = 500;
    JsonObject out = new RemoteLogsQueryTool().execute(new JsonObject());
    assertEquals("remote relay request failed", out.get("error").getAsString());
    assertTrue(out.get("use_remote").getAsBoolean());

    status = 200;
    body = "not json";
    assertTrue(new RemoteLogsQueryTool().execute(new JsonObject()).has("error"));
  }

  @Test
  void anUnconfiguredRelayIsReported() {
    SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.setValue(false);
    JsonObject off = new RemoteLogsQueryTool().execute(new JsonObject());
    assertEquals("remote relay not configured", off.get("error").getAsString());
    assertFalse(off.get("use_remote").getAsBoolean());

    SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.setValue(true);
    SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.setValue("   ");
    assertEquals(
        "remote relay not configured",
        new RemoteLogsQueryTool().execute(new JsonObject()).get("error").getAsString());
    assertNull(query.get(), "nothing was asked of the relay");
  }

  @Test
  void urlsAreBuiltWithClampedLimits() {
    JsonObject tooMany = new JsonObject();
    tooMany.addProperty("limit", 5000);
    tooMany.addProperty("since", 0);
    tooMany.addProperty("q", "  ");
    assertEquals(
        "https://relay/events?limit=1000", RemoteLogsQueryTool.buildUrl("https://relay/", tooMany));

    JsonObject tooFew = new JsonObject();
    tooFew.addProperty("limit", -3);
    tooFew.addProperty("since", "not a number");
    assertEquals(
        "https://relay/events?limit=1", RemoteLogsQueryTool.buildUrl("https://relay", tooFew));
  }

  @Test
  void bodiesWithoutAnEventArrayHaveNoEvents() {
    assertTrue(RemoteLogsQueryTool.parseEvents(null).isEmpty());
    assertTrue(RemoteLogsQueryTool.parseEvents(" ").isEmpty());
    assertTrue(RemoteLogsQueryTool.parseEvents("[1,2]").isEmpty());
    assertTrue(RemoteLogsQueryTool.parseEvents("{\"data\":{}}").isEmpty());
    assertTrue(RemoteLogsQueryTool.parseEvents("{\"other\":[]}").isEmpty());
  }

  @Test
  void theLocalLogIsQueriedTailedAndCleared() {
    IceBridgeEventLog log = IceBridgeEventLog.instance();
    boolean enabled = log.isEnabled();
    try {
      log.setEnabled(true);
      log.clear();
      log.record(
          new IceBridgeEvent(
              System.currentTimeMillis(),
              IceBridgeEvent.Level.ERROR,
              IceBridgeEvent.Category.RELAY,
              "peer-one",
              "relay down"));
      log.record(
          new IceBridgeEvent(
              System.currentTimeMillis(),
              IceBridgeEvent.Level.INFO,
              IceBridgeEvent.Category.SEARCH,
              "peer-two",
              "search sent"));

      JsonObject errors = new JsonObject();
      errors.addProperty("level", "error");
      errors.add("since", null);
      JsonObject found = new LogsQueryTool().execute(errors);
      assertEquals(1, found.get("count").getAsInt());
      assertEquals(
          "relay down",
          found.getAsJsonArray("events").get(0).getAsJsonObject().get("message").getAsString());

      JsonObject badArgs = new JsonObject();
      badArgs.add("level", new JsonArray());
      badArgs.add("limit", new JsonObject());
      assertEquals(
          2,
          new LogsQueryTool().execute(badArgs).get("count").getAsInt(),
          "bad filters are ignored");

      JsonObject cleared = new LogsClearTool().execute(new JsonObject());
      assertTrue(cleared.get("cleared").getAsBoolean());
      assertEquals(0, cleared.get("size").getAsInt());
      assertEquals(0, log.size());
      assertEquals(0, new LogsTailTool().execute(new JsonObject()).getAsJsonArray("events").size());
    } finally {
      log.clear();
      log.setEnabled(enabled);
    }
  }

  @Test
  void eventsConvertToJsonSkippingNulls() {
    assertEquals(0, LogsQueryTool.toJsonArray(null).size());
    IceBridgeEvent event =
        new IceBridgeEvent(5, IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.PEER, "p", "m");
    java.util.ArrayList<IceBridgeEvent> events = new java.util.ArrayList<>(List.of(event));
    events.add(null);
    assertEquals(1, LogsQueryTool.toJsonArray(events).size());
  }
}
