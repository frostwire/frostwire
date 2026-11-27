/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** JdkHttpClient.post(..., gzip) must declare the encoding it uses. */
class JdkHttpClientGzipPostTest {
  private HttpServer server;
  private final AtomicReference<Headers> seenHeaders = new AtomicReference<>();
  private final AtomicReference<byte[]> seenBody = new AtomicReference<>();
  private String base;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          seenHeaders.set(exchange.getRequestHeaders());
          seenBody.set(exchange.getRequestBody().readAllBytes());
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

  @Test
  void gzipPostDeclaresContentEncoding() throws IOException {
    new JdkHttpClient().post(base, 5000, "UA/1", "hello=world", "text/plain", true);

    assertEquals("gzip", seenHeaders.get().getFirst("Content-Encoding"));
    try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(seenBody.get()))) {
      assertEquals("hello=world", new String(gz.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  void plainPostHasNoContentEncoding() throws IOException {
    new JdkHttpClient().post(base, 5000, "UA/1", "hello=world", "text/plain", false);

    assertNull(seenHeaders.get().getFirst("Content-Encoding"));
    assertEquals("hello=world", new String(seenBody.get(), StandardCharsets.UTF_8));
  }
}
