/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import java.lang.reflect.Field;
import java.util.Arrays;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fuzzes what {@link IceBridgeClient} decodes from the IceBridge control API: the JSON {@code
 * ApiResponse} bodies of {@code /health}, {@code /metrics}, {@code /lookup}, {@code /probe}, {@code
 * /poll} (through {@code InboundMessageInfoAdapter}), {@code /route} and {@code /send}, and what
 * each public method does with them (the probe state and key, the poll frames' base64 fields, the
 * traffic counters).
 *
 * <p>Who sends it: the IceBridge daemon the client talks to, on 127.0.0.1 (a child process on
 * desktop, in-process on Android) or a remote forwarder over HTTPS ({@code USE_REMOTE}). A local
 * process that takes the control port, or a compromised forwarder, controls every byte.
 *
 * <p>No socket is opened: the harness swaps the client's {@code OkHttpClient} (a private final
 * field, set reflectively once) for one whose interceptor answers every call with HTTP 200 and the
 * fuzzed body. Every public method called here documents a failure value (false, 0, null, an empty
 * list, {@code ProbeState.NONE}) for a daemon that can't be reached or answers nonsense, so any
 * exception escaping, or an input that takes seconds, is a bug.
 */
class IceBridgeClientFuzzTest {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final byte[] PEER_PUB = new byte[32];
  private static volatile byte[] responseBody = new byte[0];
  private static final IceBridgeClient CLIENT = clientAnsweringFuzzedBody();

  static {
    Arrays.fill(PEER_PUB, (byte) 9);
  }

  @FuzzTest
  void responses(FuzzedDataProvider data) {
    int call = data.consumeInt(0, 9);
    responseBody = data.consumeRemainingAsBytes();
    switch (call) {
      case 0 -> CLIENT.health(1000);
      case 1 -> CLIENT.rudpPort(1000);
      case 2 -> CLIENT.trafficBytes(1000);
      case 3 -> CLIENT.lookup(10);
      case 4 -> {
        IceBridgeClient.ProbeOutcome outcome = CLIENT.probe("192.0.2.9", 6888, false);
        if (outcome.state() == IceBridgeClient.ProbeState.ESTABLISHED
            && (outcome.pub() == null || outcome.pub().length != 32)) {
          throw new IllegalStateException("ESTABLISHED without a 32-byte key");
        }
      }
      case 5 -> {
        for (IceBridgeClient.InboundMessage message : CLIENT.poll(64)) {
          int source = message.sourcePub().length;
          if (source != 0 && source != 32) {
            throw new IllegalStateException("poll returned a " + source + "-byte source key");
          }
        }
      }
      case 6 -> CLIENT.route(PEER_PUB, "192.0.2.9", 6888, IceBridgeConfig.Role.BOTH);
      case 7 -> CLIENT.send(PEER_PUB, MeshProtocolId.SEARCH, new byte[] {1});
      case 8 -> CLIENT.healthQuiet(1000);
      default -> CLIENT.rudpPortQuiet(1000);
    }
  }

  private static IceBridgeClient clientAnsweringFuzzedBody() {
    IceBridgeClient client = new IceBridgeClient("http://127.0.0.1:1");
    try {
      Field http = IceBridgeClient.class.getDeclaredField("http");
      http.setAccessible(true);
      OkHttpClient original = (OkHttpClient) http.get(client);
      OkHttpClient offline =
          original
              .newBuilder()
              .addInterceptor(
                  chain ->
                      new Response.Builder()
                          .request(chain.request())
                          .protocol(Protocol.HTTP_1_1)
                          .code(200)
                          .message("OK")
                          .body(ResponseBody.create(responseBody, JSON))
                          .build())
              .build();
      http.set(client, offline);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("IceBridgeClient.http not found", e);
    }
    return client;
  }
}
