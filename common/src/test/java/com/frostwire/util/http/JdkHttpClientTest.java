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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives {@link JdkHttpClient} against a loopback server; no external network is used. */
final class JdkHttpClientTest {

  private HttpServer server;
  private String base;
  private final Map<String, List<String>> lastRequestHeaders = new HashMap<>();
  private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
  private final AtomicReference<String> lastRequestMethod = new AtomicReference<>();
  private final AtomicReference<byte[]> lastRawBody = new AtomicReference<>();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/hello", ex -> reply(ex, 200, "hello world".getBytes(StandardCharsets.UTF_8)));
    server.createContext("/missing", ex -> reply(ex, 404, "nope".getBytes(StandardCharsets.UTF_8)));
    server.createContext(
        "/gzip",
        ex -> {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write("zipped body".getBytes(StandardCharsets.UTF_8));
          }
          ex.getResponseHeaders().add("Content-Encoding", "gzip");
          reply(ex, 200, bos.toByteArray());
        });
    server.createContext(
        "/redirect",
        ex -> {
          ex.getResponseHeaders().add("Location", base + "/hello");
          reply(ex, 302, new byte[0]);
        });
    server.createContext("/big", ex -> reply(ex, 200, new byte[256 * 1024]));
    server.createContext(
        "/range",
        ex -> {
          String range = ex.getRequestHeaders().getFirst("Range");
          byte[] all = "0123456789".getBytes(StandardCharsets.UTF_8);
          if (range != null && range.startsWith("bytes=")) {
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
          lastRawBody.set(body);
          reply(ex, 200, body);
        });
    server.createContext("/fail", ex -> reply(ex, 500, "boom".getBytes(StandardCharsets.UTF_8)));
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private void reply(HttpExchange ex, int code, byte[] body) throws IOException {
    synchronized (lastRequestHeaders) {
      lastRequestHeaders.clear();
      lastRequestHeaders.putAll(ex.getRequestHeaders());
    }
    lastRequestMethod.set(ex.getRequestMethod());
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
    synchronized (lastRequestHeaders) {
      for (Map.Entry<String, List<String>> e : lastRequestHeaders.entrySet()) {
        if (e.getKey().equalsIgnoreCase(name)) return e.getValue().get(0);
      }
    }
    return null;
  }

  @Test
  void getReturnsBodyAndSendsRequestHeaders() throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    Map<String, String> custom = Collections.singletonMap("X-Custom", "abc");
    String body = client.get(base + "/hello", 5000, "UA/1.0", "http://ref.example/", "k=v", custom);
    assertEquals("hello world", body);
    assertEquals("UA/1.0", header("User-Agent"));
    assertEquals("http://ref.example/", header("Referer"));
    assertEquals("k=v", header("Cookie"));
    assertEquals("abc", header("X-Custom"));
    assertEquals("hello world", client.get(base + "/hello"));
    assertEquals("hello world", client.get(base + "/hello", 5000));
    assertEquals(AbstractHttpClient.DEFAULT_USER_AGENT, header("User-Agent"));
  }

  @Test
  void getHandlesGzipRedirectsAndErrors() throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    assertEquals("zipped body", client.get(base + "/gzip"));
    assertEquals("hello world", client.get(base + "/redirect"));
    assertThrows(IOException.class, () -> client.get(base + "/missing"));
    assertThrows(IOException.class, () -> client.get("http://127.0.0.1:1/unreachable", 500));
    assertThrows(IOException.class, () -> client.get("not a uri"));
  }

  @Test
  void getBytesReturnsPayloadOrNullOnFailure() {
    JdkHttpClient client = new JdkHttpClient();
    assertArrayEquals(
        "hello world".getBytes(StandardCharsets.UTF_8), client.getBytes(base + "/hello"));
    assertEquals(256 * 1024, client.getBytes(base + "/big", 5000).length);
    assertNull(client.getBytes("http://127.0.0.1:1/unreachable", 500));
  }

  @Test
  void headReturnsStatusAndHeaders() throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    Map<String, List<String>> headers = new HashMap<>();
    assertEquals(200, client.head(base + "/hello", 5000, headers));
    assertEquals("HEAD", lastRequestMethod.get());
    List<String> xTest = null;
    for (Map.Entry<String, List<String>> e : headers.entrySet()) {
      if ("x-test".equalsIgnoreCase(e.getKey())) xTest = e.getValue();
    }
    assertEquals(Collections.singletonList("yes"), xTest);
    assertEquals(404, client.head(base + "/missing", 5000, null));
    assertThrows(IOException.class, () -> client.head("not a uri", 500, null));
  }

  @Test
  void listenerSeesHeadersDataAndCompletion() throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    List<String> events = new ArrayList<>();
    ByteArrayOutputStream received = new ByteArrayOutputStream();
    client.setListener(
        new HttpClient.HttpClientListener() {
          public void onError(HttpClient c, Throwable e) {
            events.add("error");
          }

          public void onData(HttpClient c, byte[] buffer, int offset, int length) {
            received.write(buffer, offset, length);
          }

          public void onComplete(HttpClient c) {
            events.add("complete");
          }

          public void onCancel(HttpClient c) {
            events.add("cancel");
          }

          public void onHeaders(HttpClient c, Map<String, List<String>> h) {
            events.add("headers");
          }
        });
    assertNotNull(client.getListener());
    client.get(base + "/hello");
    assertEquals(List.of("headers", "complete"), events);
    assertEquals("hello world", received.toString(StandardCharsets.UTF_8));
  }

  @Test
  void cancellingMidDownloadFiresOnCancel() throws IOException {
    JdkHttpClient client = new JdkHttpClient();
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

          @Override
          public void onComplete(HttpClient c) {
            events.add("complete");
          }
        });
    byte[] bytes = client.getBytes(base + "/big");
    assertEquals(List.of("cancel"), events);
    assertTrue(client.isCanceled());
    assertTrue(bytes.length < 256 * 1024);
    client.resetCancellation();
    assertFalse(client.isCanceled());
  }

  @Test
  void saveWritesAndResumesFiles(@TempDir File tmp) throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    File f = new File(tmp, "out.bin");
    client.save(base + "/range", f);
    assertEquals("0123456789", Files.readString(f.toPath()));

    Files.writeString(f.toPath(), "01234");
    client.save(base + "/range", f, true);
    assertEquals("bytes=5-", header("Range"));
    assertEquals("0123456789", Files.readString(f.toPath()));

    // not resuming starts over
    Files.writeString(f.toPath(), "garbage");
    client.save(base + "/range", f, false);
    assertEquals("0123456789", Files.readString(f.toPath()));
  }

  @Test
  void resumeAgainstServerWithoutRangeSupportFails(@TempDir File tmp) throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    File f = new File(tmp, "partial.bin");
    Files.writeString(f.toPath(), "01234");
    List<Throwable> errors = new ArrayList<>();
    client.setListener(
        new HttpClient.HttpClientListenerAdapter() {
          @Override
          public void onError(HttpClient c, Throwable e) {
            errors.add(e);
          }
        });
    assertThrows(
        HttpClient.RangeNotSupportedException.class, () -> client.save(base + "/norange", f, true));
    assertEquals(1, errors.size());
  }

  @Test
  void postFormSendsUrlEncodedBody() throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    Map<String, String> form = new java.util.LinkedHashMap<>();
    form.put("a", "1");
    form.put("b", "two");
    assertEquals("a=1&b=two", client.post(base + "/echo", 5000, "UA/2", form));
    assertEquals("POST", lastRequestMethod.get());
    assertEquals("application/x-www-form-urlencoded", header("Content-Type"));
    assertEquals("UA/2", header("User-Agent"));
    assertEquals("", client.post(base + "/echo", 5000, "UA/2", new HashMap<>()));
    // transport failures are swallowed and reported as an empty result
    assertEquals("", client.post("http://127.0.0.1:1/x", 500, "UA", form));
  }

  @Test
  void postContentEchoesAndHonoursContentType() throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    assertEquals("{\"k\":1}", client.post(base + "/echo", 5000, "{\"k\":1}", "application/json"));
    assertEquals("application/json", header("Content-Type"));
    assertThrows(
        HttpClient.ResponseCodeNotSupportedException.class,
        () -> client.post(base + "/fail", 5000, "x", "text/plain"));
    assertThrows(IOException.class, () -> client.post("not a uri", 500, "x", "text/plain"));
  }

  @Test
  void postContentWithAndWithoutGzip() throws IOException {
    JdkHttpClient client = new JdkHttpClient();
    assertEquals("plain text", client.post(base + "/echo", 5000, "UA/3", "plain text", false));
    assertEquals("text/plain", header("Content-Type"));
    assertEquals("UA/3", header("User-Agent"));

    // the server sees the raw (gzip) bytes and the response echoes them back
    String echoed = client.post(base + "/echo", 5000, "UA/3", "compress me", "text/csv", true);
    assertNotNull(echoed);
    byte[] sent = lastRawBody.get();
    assertEquals(0x1f, sent[0] & 0xff);
    assertEquals(0x8b, sent[1] & 0xff);
    try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(sent))) {
      assertEquals("compress me", new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertEquals("text/csv", header("Content-Type"));

    assertThrows(
        IOException.class, () -> client.post("not a uri", 500, "UA", "x", "text/plain", false));
    // a failing status is reported through onError and yields no result
    assertNull(client.post(base + "/fail", 5000, "UA", "x", false));
  }

  @Test
  void formDataBytesJoinsPairs() throws Exception {
    JdkHttpClient client = new JdkHttpClient();
    Map<String, String> form = new java.util.LinkedHashMap<>();
    form.put("x", "1");
    form.put("y", "2");
    assertEquals("x=1&y=2", new String(client.getFormDataBytes(form), StandardCharsets.UTF_8));
    assertEquals("", new String(client.getFormDataBytes(null), StandardCharsets.UTF_8));
  }
}
