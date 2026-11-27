/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What JdkHttpClient.save(...) really puts on the wire. */
class JdkHttpClientSaveHeadersTest {
  private HttpServer server;
  private final AtomicReference<Headers> seenHeaders = new AtomicReference<>();
  private String base;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          seenHeaders.set(exchange.getRequestHeaders());
          byte[] reply = "payload".getBytes(StandardCharsets.UTF_8);
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
  void saveSendsReferrerAsRefererAndHonorsExtraHeaders(@TempDir File tmp) throws IOException {
    File out = new File(tmp, "out.bin");

    new JdkHttpClient()
        .save(base, out, false, 5000, "UA/1", "http://ref/", Map.of("X-Extra", "yes"));

    assertEquals("http://ref/", seenHeaders.get().getFirst("Referer"));
    assertNull(seenHeaders.get().getFirst("Cookie"), "referrer must not be sent as a cookie");
    assertEquals("yes", seenHeaders.get().getFirst("X-Extra"));
    assertEquals("UA/1", seenHeaders.get().getFirst("User-Agent"));
    assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(out.toPath()));
  }

  @Test
  void saveWithoutExtraHeadersStillSendsTheReferer(@TempDir File tmp) throws IOException {
    File out = new File(tmp, "out.bin");

    new JdkHttpClient().save(base, out, false, 5000, "UA/1", "http://ref/");

    assertEquals("http://ref/", seenHeaders.get().getFirst("Referer"));
    assertNull(seenHeaders.get().getFirst("Cookie"));
  }
}
