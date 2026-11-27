/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.ThreadPool;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives {@link OkHttpClientWrapper} against a loopback server; no external network is used. */
final class OkHttpClientWrapperLoopbackTest {

  private HttpServer server;
  private ThreadPool pool;
  private OkHttpClientWrapper client;
  private String base;
  private final Map<String, List<String>> lastHeaders = new HashMap<>();
  private final AtomicReference<String> lastMethod = new AtomicReference<>();
  private final AtomicReference<byte[]> lastBody = new AtomicReference<>();

  @BeforeEach
  void start() throws IOException {
    pool = new ThreadPool("okhttp-loopback", 2, new LinkedBlockingQueue<>(), true);
    client = new OkHttpClientWrapper(pool, true);
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/hello", ex -> reply(ex, 200, "hello world".getBytes(StandardCharsets.UTF_8)));
    server.createContext("/missing", ex -> reply(ex, 404, "nope".getBytes(StandardCharsets.UTF_8)));
    server.createContext("/fail", ex -> reply(ex, 500, "boom".getBytes(StandardCharsets.UTF_8)));
    server.createContext("/big", ex -> reply(ex, 200, new byte[200 * 1024]));
    server.createContext(
        "/range",
        ex -> {
          String range = ex.getRequestHeaders().getFirst("Range");
          byte[] all = "0123456789".getBytes(StandardCharsets.UTF_8);
          if (range != null) {
            int start = Integer.parseInt(range.substring(6, range.indexOf('-')));
            ex.getResponseHeaders().add("Content-Range", "bytes " + start + "-9/10");
            byte[] tail = new byte[all.length - start];
            System.arraycopy(all, start, tail, 0, tail.length);
            reply(ex, 206, tail);
          } else {
            reply(ex, 200, all);
          }
        });
    server.createContext(
        "/norange", ex -> reply(ex, 200, "0123456789".getBytes(StandardCharsets.UTF_8)));
    server.createContext(
        "/echo",
        ex -> {
          byte[] body = ex.getRequestBody().readAllBytes();
          lastBody.set(body);
          if ("gzip".equals(ex.getRequestHeaders().getFirst("Content-Encoding"))) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
              body = in.readAllBytes();
            }
          }
          reply(ex, 200, body);
        });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
    pool.shutdownNow();
  }

  private void reply(HttpExchange ex, int code, byte[] body) throws IOException {
    synchronized (lastHeaders) {
      lastHeaders.clear();
      lastHeaders.putAll(ex.getRequestHeaders());
    }
    lastMethod.set(ex.getRequestMethod());
    if ("HEAD".equals(ex.getRequestMethod())) {
      ex.getResponseHeaders().add("X-Test", "yes");
      ex.sendResponseHeaders(code, -1);
      ex.close();
      return;
    }
    ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      try (OutputStream out = ex.getResponseBody()) {
        out.write(body);
      }
    }
    ex.close();
  }

  private String header(String name) {
    synchronized (lastHeaders) {
      for (Map.Entry<String, List<String>> e : lastHeaders.entrySet()) {
        if (e.getKey().equalsIgnoreCase(name)) return e.getValue().get(0);
      }
    }
    return null;
  }

  @Test
  void getSendsHeadersAndReturnsTheBody() throws IOException {
    Map<String, String> custom = Collections.singletonMap("X-Custom", "abc");
    assertEquals(
        "hello world", client.get(base + "/hello", 5000, "UA/1", "http://ref/", "k=v", custom));
    assertEquals("UA/1", header("User-Agent"));
    assertEquals("http://ref/", header("Referer"));
    assertEquals("k=v", header("Cookie"));
    assertEquals("abc", header("X-Custom"));
    assertEquals("hello world", client.get(base + "/hello"));
  }

  @Test
  void errorStatusesAndTransportFailures() throws IOException {
    assertNull(client.get(base + "/missing"));
    assertNull(client.get(base + "/fail"));
    assertThrows(IOException.class, () -> client.get("http://127.0.0.1:1/x", 500));
  }

  @Test
  void getBytesReturnsPayloadOrNull() {
    assertArrayEquals(
        "hello world".getBytes(StandardCharsets.UTF_8), client.getBytes(base + "/hello"));
    assertEquals(200 * 1024, client.getBytes(base + "/big").length);
    assertNull(client.getBytes(base + "/missing"));
    assertNull(client.getBytes("http://127.0.0.1:1/x", 500));
  }

  @Test
  void headReturnsStatusAndHeaders() throws IOException {
    Map<String, List<String>> headers = new HashMap<>();
    assertEquals(200, client.head(base + "/hello", 5000, headers));
    assertEquals("HEAD", lastMethod.get());
    assertTrue(headers.containsKey("x-test"));
    assertEquals(404, client.head(base + "/missing", 5000, null));
  }

  @Test
  void saveWritesResumesAndReportsFailures(@TempDir File tmp) throws IOException {
    File f = new File(tmp, "out.bin");
    List<String> events = new ArrayList<>();
    client.setListener(
        new HttpClient.HttpClientListenerAdapter() {
          @Override
          public void onComplete(HttpClient c) {
            events.add("complete");
          }

          @Override
          public void onHeaders(HttpClient c, Map<String, List<String>> h) {
            events.add("headers");
          }
        });
    client.save(base + "/range", f);
    assertEquals("0123456789", Files.readString(f.toPath()));
    assertEquals(List.of("headers", "complete"), events);

    Files.writeString(f.toPath(), "01234");
    client.save(base + "/range", f, true);
    assertEquals("bytes=5-", header("Range"));
    assertEquals("0123456789", Files.readString(f.toPath()));

    Files.writeString(f.toPath(), "01234");
    assertThrows(
        HttpClient.RangeNotSupportedException.class, () -> client.save(base + "/norange", f, true));

    File failed = new File(tmp, "failed.bin");
    assertThrows(IOException.class, () -> client.save(base + "/missing", failed));
    assertFalse(failed.exists(), "a failed download leaves no empty file behind");

    Map<String, String> extra = Collections.singletonMap("X-Extra", "1");
    client.save(base + "/range", new File(tmp, "extra.bin"), false, extra);
    assertEquals("1", header("X-Extra"));
  }

  @Test
  void cancellationStopsTheDownload(@TempDir File tmp) throws IOException {
    List<String> events = new ArrayList<>();
    client.setListener(
        new HttpClient.HttpClientListenerAdapter() {
          @Override
          public void onData(HttpClient c, byte[] buffer, int offset, int length) {
            c.cancel();
          }

          @Override
          public void onCancel(HttpClient c) {
            events.add("cancel");
          }
        });
    File f = new File(tmp, "big.bin");
    client.save(base + "/big", f);
    assertEquals(List.of("cancel"), events);
    assertTrue(f.length() < 200 * 1024);
  }

  @Test
  void postsFormsAndContent() throws IOException {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("a", "1");
    form.put("b", "two");
    assertEquals("a=1&b=two", client.post(base + "/echo", 5000, "UA/2", form));
    assertEquals("POST", lastMethod.get());
    assertTrue(header("Content-Type").startsWith("application/x-www-form-urlencoded"));

    assertEquals("{\"k\":1}", client.post(base + "/echo", 5000, "{\"k\":1}", "application/json"));
    assertTrue(header("Content-Type").startsWith("application/json"));

    assertEquals("plain", client.post(base + "/echo", 5000, "UA/3", "plain", false));
    assertEquals("compress me", client.post(base + "/echo", 5000, "UA/3", "compress me", true));
    byte[] sent = lastBody.get();
    assertEquals(0x1f, sent[0] & 0xff);
    assertEquals(0x8b, sent[1] & 0xff);
    assertEquals("gzip", header("Content-Encoding"));
  }

  @Test
  void postToAFailingServerThrows() {
    assertThrows(
        HttpClient.ResponseCodeNotSupportedException.class,
        () -> client.post(base + "/fail", 5000, "x", "text/plain"));
    assertThrows(
        IOException.class, () -> client.post("http://127.0.0.1:1/x", 500, "x", "text/plain"));
  }

  @Test
  void staticHelpersConfigureClients() {
    assertTrue(OkHttpClientWrapper.newOkHttpClient(pool).build().pingIntervalMillis() > 0);
    OkHttpClientWrapper.cancelAllRequests();
  }
}
