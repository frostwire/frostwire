/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Form posts must be application/x-www-form-urlencoded: keys and values are percent-encoded. */
class FormDataEncodingTest {
  private HttpServer server;
  private final AtomicReference<String> seenBody = new AtomicReference<>();
  private String base;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          seenBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] reply = "ok".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, reply.length);
          exchange.getResponseBody().write(reply);
          exchange.close();
        });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort() + "/x";
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private static Map<String, String> data() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("q", "a&b=c d");
    form.put("na me", "café 100%+");
    form.put("plain", "x");
    return form;
  }

  private static void assertDecodesBack(String body) {
    String[] pairs = body.split("&", -1);
    assertEquals(3, pairs.length, "reserved characters in values must not add parameters: " + body);
    Map<String, String> back = new LinkedHashMap<>();
    for (String pair : pairs) {
      String[] kv = pair.split("=", -1);
      assertEquals(2, kv.length, "exactly one '=' per pair: " + pair);
      back.put(
          URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
          URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
    }
    assertEquals(data(), back);
  }

  @Test
  void getFormDataBytesEncodesKeysAndValues() throws IOException {
    byte[] bytes = new JdkHttpClient().getFormDataBytes(data());

    assertDecodesBack(new String(bytes, StandardCharsets.UTF_8));
  }

  @Test
  void getFormDataBytesOfNothingIsEmpty() throws IOException {
    assertEquals(0, new JdkHttpClient().getFormDataBytes(null).length);
    assertEquals(0, new JdkHttpClient().getFormDataBytes(Map.of()).length);
  }

  @Test
  void jdkClientSendsAnEncodedBody() {
    new JdkHttpClient().post(base, 5000, "UA/1", data());

    assertDecodesBack(seenBody.get());
  }

  @Test
  void okHttpClientSendsAnEncodedBody() throws IOException {
    new OkHttpClientWrapper(null).post(base, 5000, "UA/1", data());

    assertDecodesBack(seenBody.get());
  }
}
