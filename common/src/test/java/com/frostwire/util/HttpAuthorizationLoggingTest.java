/*
 *     Copyright (c) 2026, FrostWire(R). All rights reserved.
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpAuthorizationLoggingTest {
  @TempDir Path directory;

  @Test
  void failingHttpRequestsDoNotLogOrThrowAuthorizationQueries() {
    assertDoesNotThrow(
        () -> {
          HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
          server.createContext("/resolver", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
          });
          StringBuilder messages = new StringBuilder();
          java.util.logging.Logger logger = java.util.logging.Logger.getLogger("OkHttpClientWrapper");
          Handler handler = new Handler() {
            public void publish(LogRecord record) { messages.append(record.getMessage()).append('\n'); }
            public void flush() {}
            public void close() {}
          };
          logger.addHandler(handler);
          server.start();
          try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/resolver?track_authorization=distinctive-fake-auth&Signature=fake-signature";
            var client = HttpClientFactory.newInstance(HttpClientFactory.HttpContext.DOWNLOAD);
            assertNull(client.get(url, 3000));
            assertNull(client.getBytes(url, 3000));
            IOException error = assertThrows(IOException.class,
                () -> client.save(url, directory.resolve("audio.m4a").toFile(), false));
            assertFalse(error.getMessage().contains("distinctive-fake-auth"));
            assertFalse(error.getMessage().contains("fake-signature"));
            assertTrue(messages.toString().contains("403"));
            assertFalse(messages.toString().contains("distinctive-fake-auth"));
            assertFalse(messages.toString().contains("fake-signature"));
          } finally {
            server.stop(0);
            logger.removeHandler(handler);
          }
        });
  }
}
