/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.peer.PeerRecord;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import com.google.gson.JsonNull;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Fuzzes the IceBridge control API: {@link ControlHandler} reading one HTTP request (an endpoint, a
 * query string and a body) in a Netty {@link EmbeddedChannel}, with no socket. The request carries
 * a valid token, so the fuzzer reaches every endpoint's JSON and query parsing: the {@code
 * RegisterRequest}, {@code ConsumerRequest}, {@code RouteRequest}, {@code ProbeRequest} and {@code
 * SendRequest} bodies and the {@code count}, {@code pub}, {@code level}, {@code category}, {@code
 * since}, {@code limit}, {@code ih} and {@code timeoutMs} parameters. The rUDP session manager has
 * no channel, so nothing is sent.
 *
 * <p>Who sends it: {@link ControlServer} binds to 127.0.0.1 only, so the sender is a co-located
 * process: the FrostWire client holding a token, or, before the token check, any local process (the
 * request path is decoded first). That makes this a lower priority than the mesh decoders, not out
 * of scope.
 *
 * <p>The handler answers 400 for a {@code JsonParseException} or {@code IllegalArgumentException}
 * (documented: "invalid request") and turns any other {@code RuntimeException} into a 500 "internal
 * error", which it logs; an {@code Error} or an exception outside its {@code try} goes to {@code
 * exceptionCaught}, which logs it and drops the connection. Either log is a finding: the harness
 * rethrows the logged exception so the report shows where it came from. A 500 without a logged
 * exception, or an input that takes seconds, is a finding too.
 */
class ControlHandlerFuzzTest {

  private static final String TOKEN = "fuzz-control-token";
  private static final String[][] ENDPOINTS = {
    {"POST", "/register"},
    {"POST", "/consumer"},
    {"POST", "/route"},
    {"POST", "/probe"},
    {"POST", "/send"},
    {"GET", "/lookup"},
    {"GET", "/poll"},
    {"GET", "/metrics"},
    {"GET", "/events"},
    {"GET", "/catalog"},
    {"GET", "/torrent"},
    {"GET", "/health"},
  };

  /** A peer every registry starts with, so {@code /send} and {@code /route} meet a known key. */
  private static final byte[] KNOWN_PUB = new byte[32];

  private static final IdentityKeys IDENTITY = identity();
  private static final IceBridgeConfig CONFIG =
      new IceBridgeConfig(
          "127.0.0.1", 0, 1, IceBridgeConfig.Role.BOTH, null, 1000, 3600, 1000.0, false);
  private static final IceBridgeTokens TOKENS = tokens();
  private static final CapturedFailure FAILURE = new CapturedFailure();
  // Held so the configured logger is not collected before ControlHandler takes it.
  private static final Logger HANDLER_LOG = Logger.getLogger(ControlHandler.class.getSimpleName());

  static {
    Arrays.fill(KNOWN_PUB, (byte) 7);
    // The handler logs the exceptions it swallows at WARNING; the fuzz tasks set logging to
    // SEVERE, so lower only this logger and keep its records away from the console.
    HANDLER_LOG.setLevel(Level.WARNING);
    HANDLER_LOG.setUseParentHandlers(false);
    HANDLER_LOG.addHandler(FAILURE);
  }

  @FuzzTest
  void handle(FuzzedDataProvider data) {
    int endpoint = data.consumeInt(0, ENDPOINTS.length);
    // The first line is the query string (or, for the last choice, the whole URI), the rest the
    // body.
    byte[] rest = data.consumeRemainingAsBytes();
    int newline = indexOf(rest, (byte) '\n');
    String query = new String(rest, 0, newline, StandardCharsets.UTF_8);
    byte[] body = Arrays.copyOfRange(rest, Math.min(rest.length, newline + 1), rest.length);

    HttpMethod method;
    String uri;
    if (endpoint == ENDPOINTS.length) {
      method = HttpMethod.GET;
      uri = query;
    } else {
      method = HttpMethod.valueOf(ENDPOINTS[endpoint][0]);
      uri = query.isEmpty() ? ENDPOINTS[endpoint][1] : ENDPOINTS[endpoint][1] + "?" + query;
    }
    DefaultFullHttpRequest request;
    try {
      request =
          new DefaultFullHttpRequest(
              HttpVersion.HTTP_1_1, method, uri, Unpooled.wrappedBuffer(body));
    } catch (IllegalArgumentException notARequestLine) {
      // Netty refuses a URI with spaces or control characters; its HTTP decoder never builds one.
      return;
    }
    request.headers().set("X-IceBridge-Token", TOKEN);

    PeerRegistry registry = new PeerRegistry(CONFIG);
    registry.register(
        new PeerRecord(
            KNOWN_PUB, "192.0.2.7", 6888, IceBridgeConfig.Role.BOTH, System.currentTimeMillis()));
    IceBridgeMetrics metrics = new IceBridgeMetrics();
    InboundMessageQueue queue = new InboundMessageQueue();
    RudpSessionManager rudp = new RudpSessionManager(IDENTITY, registry, metrics, queue);
    EmbeddedChannel channel =
        new EmbeddedChannel(
            new ControlHandler(
                registry,
                metrics,
                CONFIG,
                rudp,
                queue,
                TOKENS,
                (pub, timeoutMs) -> JsonNull.INSTANCE,
                (infoHash, pub, timeoutMs) -> JsonNull.INSTANCE));
    FAILURE.thrown = null;
    try {
      channel.writeInbound(request);
      channel.checkException();
      rethrowLogged();
      FullHttpResponse response = channel.readOutbound();
      if (response != null) {
        try {
          if (response.status().equals(HttpResponseStatus.INTERNAL_SERVER_ERROR)) {
            throw new IllegalStateException("500 for " + method + " " + uri);
          }
        } finally {
          response.release();
        }
      }
    } finally {
      channel.finishAndReleaseAll();
      rudp.shutdown();
    }
  }

  private static void rethrowLogged() {
    Throwable thrown = FAILURE.thrown;
    if (thrown instanceof RuntimeException e) {
      throw e;
    }
    if (thrown instanceof Error e) {
      throw e;
    }
    if (thrown != null) {
      throw new IllegalStateException("control handler logged " + thrown, thrown);
    }
  }

  private static int indexOf(byte[] bytes, byte value) {
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == value) {
        return i;
      }
    }
    return bytes.length;
  }

  private static IdentityKeys identity() {
    try {
      return IdentityKeys.fromSeed(new byte[32]);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static IceBridgeTokens tokens() {
    // A file that never exists: the token lives in memory only.
    IceBridgeTokens tokens =
        new IceBridgeTokens(new File(System.getProperty("java.io.tmpdir"), "no-such-tokens-file"));
    tokens.addRuntimeToken(TOKEN);
    return tokens;
  }

  /** Remembers the exception of the last WARNING the handler logged. */
  private static final class CapturedFailure extends Handler {
    volatile Throwable thrown;

    @Override
    public void publish(LogRecord record) {
      if (record.getThrown() != null) {
        thrown = record.getThrown();
      }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
