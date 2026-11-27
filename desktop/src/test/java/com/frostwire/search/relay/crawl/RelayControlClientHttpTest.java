/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.http.JdkHttpClient;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The relay control client and the crawler's catalog pass against a loopback relay. */
final class RelayControlClientHttpTest {

  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private HttpServer server;
  private String base;
  private final AtomicReference<String> lastToken = new AtomicReference<>();
  private final AtomicReference<String> lastQuery = new AtomicReference<>();
  private volatile String torrentBody;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/lookup",
        ex -> {
          lastToken.set(ex.getRequestHeaders().getFirst("X-IceBridge-Token"));
          lastQuery.set(ex.getRequestURI().getRawQuery());
          reply(ex, "{\"ok\":true,\"data\":[{\"pub\":\"AAECAwQ\"},{\"pub\":\"BQYHCAk\"}]}");
        });
    server.createContext(
        "/catalog",
        ex -> {
          lastQuery.set(ex.getRequestURI().getRawQuery());
          boolean first = ex.getRequestURI().getRawQuery().contains("pub=AAECAwQ");
          reply(
              ex,
              first
                  ? "{\"ok\":true,\"data\":[{\"ih\":\""
                      + HASH
                      + "\",\"name\":\"Ubuntu\",\"s\":100,\"fc\":2,\"pub\":\"AAECAwQ\"}]}"
                  : "{\"ok\":false}");
        });
    server.createContext(
        "/torrent",
        ex -> {
          lastQuery.set(ex.getRequestURI().getRawQuery());
          reply(ex, torrentBody);
        });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private static void reply(com.sun.net.httpserver.HttpExchange ex, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(200, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }

  private RelayControlClient client(String token) {
    return new RelayControlClient(new JdkHttpClient(), base + "/", token);
  }

  @Test
  void constructorRejectsBlankUrlAndMissingHttpClient() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RelayControlClient(new JdkHttpClient(), "  ", "t"));
    assertThrows(
        IllegalArgumentException.class, () -> new RelayControlClient(null, "http://x", "t"));
  }

  @Test
  void lookupSendsTheTokenAndParsesKeys() {
    assertEquals(List.of("AAECAwQ", "BQYHCAk"), client(" secret ").lookupPeerPubs(5));
    assertEquals("secret", lastToken.get());
    assertEquals("count=100", lastQuery.get());
    client("").lookupPeerPubs(5);
    assertNull(lastToken.get());
  }

  @Test
  void catalogEncodesThePublicKeyAndSkipsBlankOnes() {
    List<RelayControlClient.CatalogEntry> entries = client("").fetchCatalog("AAECAwQ", 3);
    assertEquals(1, entries.size());
    assertEquals("Ubuntu", entries.get(0).name());
    assertEquals("pub=AAECAwQ&timeoutMs=3000", lastQuery.get());
    assertTrue(client("").fetchCatalog("other", 3).isEmpty());
    assertTrue(client("").fetchCatalog(" ", 3).isEmpty());
    assertTrue(client("").fetchCatalog(null, 3).isEmpty());
  }

  @Test
  void fetchTorrentDecodesTheBase64Payload() {
    byte[] torrent = "d4:name3:fooe".getBytes(StandardCharsets.UTF_8);
    torrentBody =
        "{\"ok\":true,\"data\":{\"data_b64\":\""
            + Base64.getEncoder().encodeToString(torrent)
            + "\"}}";
    assertArrayEquals(torrent, client("").fetchTorrent(HASH, "AAECAwQ", 2000));
    assertEquals("ih=" + HASH + "&pub=AAECAwQ&timeoutMs=2000", lastQuery.get());

    torrentBody = "{\"ok\":true,\"data\":{\"data_b64\":\"!!!notbase64\"}}";
    assertNull(client("").fetchTorrent(HASH, "AAECAwQ", 2000));
    torrentBody = "{\"ok\":true,\"data\":{}}";
    assertNull(client("").fetchTorrent(HASH, "AAECAwQ", 2000));
    torrentBody = "{\"ok\":true,\"data\":{\"data_b64\":\"\"}}";
    assertNull(client("").fetchTorrent(HASH, "AAECAwQ", 2000));
    torrentBody = "{\"ok\":false}";
    assertNull(client("").fetchTorrent(HASH, "AAECAwQ", 2000));
    assertNull(client("").fetchTorrent(null, "AAECAwQ", 2000));
    assertNull(client("").fetchTorrent(HASH, " ", 2000));
  }

  @Test
  void oversizedResponsesAreRejected() {
    String huge = "x".repeat(RelayControlClient.MAX_TORRENT_RESPONSE_BYTES + 1);
    torrentBody = "{\"ok\":true,\"data\":{\"data_b64\":\"" + huge + "\"}}";
    assertNull(client("").fetchTorrent(HASH, "AAECAwQ", 5000));
  }

  @Test
  void unreachableRelayYieldsEmptyResults() {
    RelayControlClient dead = new RelayControlClient(new JdkHttpClient(), "http://127.0.0.1:1", "");
    assertTrue(dead.lookupPeerPubs(1).isEmpty());
    assertTrue(dead.fetchCatalog("AAECAwQ", 1).isEmpty());
    assertNull(dead.fetchTorrent(HASH, "AAECAwQ", 500));
  }

  @Test
  void catalogPassStoresEveryReachableTorrent(@TempDir File tmp) {
    try (CrawlerStore store = CrawlerStore.open(new File(tmp, "crawl.db"))) {
      IceBridgeCrawler.crawlCatalog(store, client(""), 3, 1_700_000_000_000L);
      assertEquals(1, store.torrentCount());
      assertEquals(1, store.totalTorrentSightings());
      IceBridgeCrawler.crawlCatalog(store, client(""), 3, 1_700_000_100_000L);
      assertEquals(1, store.torrentCount());
      assertEquals(2, store.totalTorrentSightings());
      assertEquals("Ubuntu", store.topTorrents(1).get(0).name());
    }
  }

  @Test
  void catalogPassSurvivesAnUnreachableRelay(@TempDir File tmp) {
    try (CrawlerStore store = CrawlerStore.open(new File(tmp, "crawl.db"))) {
      IceBridgeCrawler.crawlCatalog(
          store, new RelayControlClient(new JdkHttpClient(), "http://127.0.0.1:1", ""), 1, 1L);
      assertEquals(0, store.torrentCount());
    }
  }
}
