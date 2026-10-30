/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import com.google.gson.JsonNull;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
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
 */
class ControlHandlerMalformedPathTest {

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
