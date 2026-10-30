/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1396 (found by ControlHandlerFuzzTest): a request path with a malformed
 * percent escape threw IllegalArgumentException out of ControlHandler before the token check, so
 * Netty logged a stack trace and closed the connection without a response.
 *
 * <p>The compatibility fixtures below cover the other side of the boundary: well-formed escapes
 * still decode to the same path, the token check still applies to every decoded path but /health,
 * and routes still dispatch.
 */
class ControlHandlerMalformedPathTest {

  private static final String TOKEN = "fixture-token-1396";

  @Test
  void aMalformedPathEscapeGetsA400() throws Exception {
    for (String uri : new String[] {"/%", "/%zz", "%"}) {
      EmbeddedChannel channel = new EmbeddedChannel(handler());

      channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri));

      FullHttpResponse response = channel.readOutbound();
      assertNotNull(response, "no response for " + uri);
      assertEquals(HttpResponseStatus.BAD_REQUEST, response.status(), uri);
      assertTrue(
          response.content().toString(StandardCharsets.UTF_8).contains("malformed request path"),
          uri);
      response.release();
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void aMalformedPathEscapeGetsA400WithAValidTokenToo() throws Exception {
    Reply reply = send(HttpMethod.GET, "/%zz", TOKEN);
    assertEquals(HttpResponseStatus.BAD_REQUEST, reply.status);
    assertEquals("malformed request path", reply.error());
  }

  // --- Compatibility fixtures: well-formed escapes decode to the same path as before. ---
  // An unknown endpoint echoes the decoded path, so the error text shows what the decoder saw.

  @Test
  void wellFormedEscapesDecodeToTheSamePath() throws Exception {
    String[][] cases = {
      {"/he%20llo", "/he llo"},
      {"/a%2Fb", "/a/b"},
      {"/a%2fb", "/a/b"},
      {"/100%25", "/100%"},
      {"/caf%C3%A9", "/café"},
      {"/%E2%82%AC", "/€"},
      {"/%F0%9F%98%80", "/😀"},
      {"/a+b", "/a+b"},
      {"/plain", "/plain"},
      {"/", "/"},
    };
    for (String[] c : cases) {
      Reply reply = send(HttpMethod.GET, c[0], TOKEN);
      assertEquals(HttpResponseStatus.BAD_REQUEST, reply.status, c[0]);
      assertEquals("unknown endpoint: GET " + c[1], reply.error(), c[0]);
    }
  }

  @Test
  void anEscapedRouteNameDispatchesToThatRoute() throws Exception {
    Reply metrics = send(HttpMethod.GET, "/%6Detrics", TOKEN);
    assertEquals(HttpResponseStatus.OK, metrics.status);
    assertTrue(metrics.json.get("ok").getAsBoolean());
    assertTrue(metrics.json.get("data").isJsonObject());

    // "/%68ealth" decodes to "/health", which needs no token.
    Reply health = send(HttpMethod.GET, "/%68ealth", null);
    assertEquals(HttpResponseStatus.OK, health.status);
    assertEquals("ok", health.json.get("data").getAsString());
  }

  @Test
  void aQueryStringIsNotPartOfThePath() throws Exception {
    Reply health = send(HttpMethod.GET, "/health?x=1&y=%20", null);
    assertEquals(HttpResponseStatus.OK, health.status);
    assertEquals("ok", health.json.get("data").getAsString());

    Reply lookup = send(HttpMethod.GET, "/lookup?count=5&forwarders=0", TOKEN);
    assertEquals(HttpResponseStatus.OK, lookup.status);
    assertTrue(lookup.json.get("ok").getAsBoolean());
    assertEquals(0, lookup.json.get("data").getAsJsonArray().size());

    // An escaped '?' stays in the path.
    Reply escapedQuestionMark = send(HttpMethod.GET, "/health%3Fx=1", TOKEN);
    assertEquals(HttpResponseStatus.BAD_REQUEST, escapedQuestionMark.status);
    assertEquals("unknown endpoint: GET /health?x=1", escapedQuestionMark.error());
  }

  @Test
  void aMalformedEscapeInTheQueryStillGetsInvalidRequest() throws Exception {
    // The query decoders were already inside the try that answers 400; unchanged by the fix.
    Reply reply = send(HttpMethod.GET, "/lookup?count=%zz", TOKEN);
    assertEquals(HttpResponseStatus.BAD_REQUEST, reply.status);
    assertEquals("invalid request", reply.error());
  }

  @Test
  void theTokenCheckStillAppliesToWellFormedPaths() throws Exception {
    for (String uri :
        new String[] {"/metrics", "/%6Detrics", "/lookup?count=1", "/caf%C3%A9", "/a%2Fb", "/"}) {
      Reply missing = send(HttpMethod.GET, uri, null);
      assertEquals(HttpResponseStatus.UNAUTHORIZED, missing.status, uri);
      assertEquals("unauthorized", missing.error(), uri);

      Reply wrong = send(HttpMethod.GET, uri, "not-the-token");
      assertEquals(HttpResponseStatus.UNAUTHORIZED, wrong.status, uri);
      assertEquals("unauthorized", wrong.error(), uri);
    }
    // "/health" escaped differently is a different path, so it needs the token.
    Reply notHealth = send(HttpMethod.GET, "/health%2F", null);
    assertEquals(HttpResponseStatus.UNAUTHORIZED, notHealth.status);
  }

  @Test
  void aPostRouteStillDispatches() throws Exception {
    // /consumer with no body answers its own validation error, proving the route ran.
    Reply reply = send(HttpMethod.POST, "/%63onsumer", TOKEN);
    assertEquals(HttpResponseStatus.BAD_REQUEST, reply.status);
    assertEquals("pub and enabled required", reply.error());
    assertFalse(reply.json.get("ok").getAsBoolean());
  }

  private static final class Reply {
    final HttpResponseStatus status;
    final JsonObject json;

    Reply(HttpResponseStatus status, JsonObject json) {
      this.status = status;
      this.json = json;
    }

    String error() {
      return json.get("error").getAsString();
    }
  }

  private static Reply send(HttpMethod method, String uri, String token) throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel(handler());
    FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);
    if (token != null) {
      request.headers().set("X-IceBridge-Token", token);
    }
    channel.writeInbound(request);
    FullHttpResponse response = channel.readOutbound();
    assertNotNull(response, "no response for " + method + " " + uri);
    try {
      return new Reply(
          response.status(),
          JsonParser.parseString(response.content().toString(StandardCharsets.UTF_8))
              .getAsJsonObject());
    } finally {
      response.release();
      channel.finishAndReleaseAll();
    }
  }

  private static ControlHandler handler() throws Exception {
    IceBridgeConfig config =
        new IceBridgeConfig(
            "127.0.0.1", 0, 1, IceBridgeConfig.Role.BOTH, null, 1000, 3600, 1000.0, false);
    PeerRegistry registry = new PeerRegistry(config);
    IceBridgeMetrics metrics = new IceBridgeMetrics();
    InboundMessageQueue queue = new InboundMessageQueue();
    RudpSessionManager rudp =
        new RudpSessionManager(IdentityKeys.fromSeed(new byte[32]), registry, metrics, queue);
    IceBridgeTokens tokens =
        new IceBridgeTokens(new File(System.getProperty("java.io.tmpdir"), "no-such-tokens-file"));
    tokens.addRuntimeToken(TOKEN);
    return new ControlHandler(
        registry,
        metrics,
        config,
        rudp,
        queue,
        tokens,
        (pub, timeoutMs) -> JsonNull.INSTANCE,
        (infoHash, pub, timeoutMs) -> JsonNull.INSTANCE);
  }
}
