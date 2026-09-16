/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.RelayConstants;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrawlerStoreTest {

  private static final long NOW_MS = 1_700_000_000_000L;
  private static final long BUCKET = RelayConstants.heartbeatBucketIndex(NOW_MS);

  @TempDir Path tempDir;

  private File dbFile;
  private CrawlerStore store;

  @BeforeEach
  void setUp() {
    dbFile = tempDir.resolve("crawl.db").toFile();
    store = CrawlerStore.open(dbFile);
  }

  @AfterEach
  void tearDown() {
    if (store != null && store.isOpen()) {
      store.close();
    }
  }

  @Test
  void upsertIncrementsSightingsAndUpdatesLastSeen() {
    store.recordSighting(BUCKET, "10.0.0.1", 6881, NOW_MS);
    store.recordSighting(BUCKET, "10.0.0.1", 6881, NOW_MS + 5_000);

    List<CrawlerStore.Endpoint> endpoints = store.endpoints(BUCKET, 10);

    assertEquals(1, endpoints.size());
    CrawlerStore.Endpoint endpoint = endpoints.get(0);
    assertEquals(CrawlerStore.endpointHash("10.0.0.1", 6881), endpoint.endpointHash());
    assertEquals(NOW_MS, endpoint.firstSeenMs());
    assertEquals(NOW_MS + 5_000, endpoint.lastSeenMs());
    assertEquals(2, endpoint.sightings());
  }

  @Test
  void storedPresenceIsHashedAndNeverContainsTheRawHost() throws Exception {
    store.recordSighting(BUCKET, "1.2.3.4", 6888, NOW_MS);

    CrawlerStore.Endpoint endpoint = store.endpoints(BUCKET, 10).get(0);
    assertNotEquals("1.2.3.4:6888", endpoint.endpointHash());
    assertFalse(endpoint.endpointHash().contains("1.2.3.4"), endpoint.endpointHash());
    assertFalse(endpoint.endpointHash().contains("6888"), endpoint.endpointHash());
    assertTrue(endpoint.endpointHash().matches("[0-9a-f]{16}"), endpoint.endpointHash());

    // What physically landed in the column must be the hash, not the address.
    String raw = readRawEndpointHash(dbFile, BUCKET);
    assertEquals(CrawlerStore.endpointHash("1.2.3.4", 6888), raw);
    assertNotEquals("1.2.3.4:6888", raw);
  }

  @Test
  void countsByBucketDistinguishesDistinctFromNew() {
    store.recordSighting(BUCKET, "10.0.0.1", 1, NOW_MS);
    store.recordSighting(BUCKET, "10.0.0.1", 1, NOW_MS + 1_000);
    store.recordSighting(BUCKET, "10.0.0.2", 2, NOW_MS);

    List<CrawlerStore.BucketCount> counts = store.countsByBucket(1, NOW_MS);

    assertEquals(1, counts.size());
    assertEquals(BUCKET, counts.get(0).bucketIndex());
    assertEquals(2, counts.get(0).distinctEndpoints());
    assertEquals(1, counts.get(0).newEndpoints());
  }

  @Test
  void countsByBucketZeroFillsEmptyBucketsInWindow() {
    store.recordSighting(BUCKET, "10.0.0.1", 1, NOW_MS);

    List<CrawlerStore.BucketCount> counts = store.countsByBucket(3, NOW_MS);

    assertEquals(3, counts.size());
    assertEquals(BUCKET - 2, counts.get(0).bucketIndex());
    assertEquals(0, counts.get(0).distinctEndpoints());
    assertEquals(0, counts.get(0).newEndpoints());
    assertEquals(BUCKET - 1, counts.get(1).bucketIndex());
    assertEquals(0, counts.get(1).distinctEndpoints());
    assertEquals(BUCKET, counts.get(2).bucketIndex());
    assertEquals(1, counts.get(2).distinctEndpoints());
    assertEquals(1, counts.get(2).newEndpoints());
  }

  @Test
  void ignoresBlankHostAndNonPositivePort() {
    store.recordSighting(BUCKET, null, 6881, NOW_MS);
    store.recordSighting(BUCKET, "", 6881, NOW_MS);
    store.recordSighting(BUCKET, "   ", 6881, NOW_MS);
    store.recordSighting(BUCKET, "10.0.0.1", 0, NOW_MS);
    store.recordSighting(BUCKET, "10.0.0.1", -1, NOW_MS);

    assertTrue(store.endpoints(BUCKET, 10).isEmpty());
    assertEquals(0, store.totalDistinct(1, NOW_MS));

    store.recordSightings(BUCKET, List.of("", "10.0.0.2"), List.of(6881, 0), NOW_MS);
    assertEquals(0, store.endpoints(BUCKET, 10).size());
  }

  @Test
  void reopenPreservesRows() {
    store.recordSighting(BUCKET, "10.0.0.1", 6881, NOW_MS);
    store.recordSighting(BUCKET, "10.0.0.2", 6882, NOW_MS);
    store.recordTorrent("ih-1", "Name", 10L, 2, "pub", NOW_MS);
    store.close();
    assertFalse(store.isOpen());

    CrawlerStore reopened = CrawlerStore.open(dbFile);
    try {
      assertTrue(reopened.isOpen());
      List<CrawlerStore.Endpoint> endpoints = reopened.endpoints(BUCKET, 10);
      assertEquals(2, endpoints.size());
      assertEquals(2, reopened.totalDistinct(1, NOW_MS));
      assertEquals(1, reopened.torrentCount());
      assertEquals("ih-1", reopened.torrents(10).get(0).infohash());
    } finally {
      reopened.close();
    }
  }

  @Test
  void batchWritesAllRowsInOneTransaction() {
    store.recordSightings(
        BUCKET, List.of("10.0.0.1", "10.0.0.2", "10.0.0.1"), List.of(6881, 6882, 6881), NOW_MS);

    List<CrawlerStore.Endpoint> endpoints = store.endpoints(BUCKET, 10);
    assertEquals(2, endpoints.size());
    CrawlerStore.Endpoint first = findEndpoint(endpoints, "10.0.0.1", 6881);
    assertNotNull(first);
    assertEquals(2, first.sightings());
    CrawlerStore.Endpoint second = findEndpoint(endpoints, "10.0.0.2", 6882);
    assertNotNull(second);
    assertEquals(1, second.sightings());

    List<CrawlerStore.BucketCount> counts = store.countsByBucket(1, NOW_MS);
    assertEquals(1, counts.size());
    assertEquals(2, counts.get(0).distinctEndpoints());
    assertEquals(1, counts.get(0).newEndpoints());
  }

  @Test
  void torrentUpsertIncrementsSeenCountAndUpdatesMetadataAndLastSeen() {
    store.recordTorrent("ih-1", "First", 100L, 1, "pub-a", NOW_MS);
    store.recordTorrent("ih-1", "Second", 200L, 3, "pub-b", NOW_MS + 10_000);

    List<CrawlerStore.Torrent> torrents = store.torrents(10);
    assertEquals(1, torrents.size());
    CrawlerStore.Torrent torrent = torrents.get(0);
    assertEquals("ih-1", torrent.infohash());
    assertEquals("Second", torrent.name());
    assertEquals(200L, torrent.sizeBytes());
    assertEquals(3, torrent.files());
    assertEquals("pub-b", torrent.publisherPeerId());
    assertEquals(NOW_MS, torrent.firstSeenMs());
    assertEquals(NOW_MS + 10_000, torrent.lastSeenMs());
    assertEquals(2, torrent.seenCount());
    assertEquals(1, store.torrentCount());
    assertEquals(2L, store.totalTorrentSightings());
  }

  @Test
  void topTorrentsOrdersBySeenCount() {
    store.recordTorrent("ih-a", "A", 1L, 1, "pub", NOW_MS);
    store.recordTorrent("ih-b", "B", 1L, 1, "pub", NOW_MS);
    store.recordTorrent("ih-b", "B", 1L, 1, "pub", NOW_MS + 1);
    store.recordTorrent("ih-b", "B", 1L, 1, "pub", NOW_MS + 2);

    List<CrawlerStore.Torrent> top = store.topTorrents(10);

    assertEquals(2, top.size());
    assertEquals("ih-b", top.get(0).infohash());
    assertEquals(3, top.get(0).seenCount());
    assertEquals("ih-a", top.get(1).infohash());
    assertEquals(1, top.get(1).seenCount());
  }

  @Test
  void v1SchemaIsMigratedToAnonymousV2WithDocumentedDataLoss() throws Exception {
    File v1File = tempDir.resolve("crawl-v1.db").toFile();
    createV1Database(v1File);
    assertEquals(0, readUserVersion(v1File));

    CrawlerStore migrated = CrawlerStore.open(v1File);
    try {
      assertEquals(2, readUserVersion(v1File));
      // v1 rows contain raw addresses and are intentionally dropped.
      assertTrue(migrated.endpoints(BUCKET, 10).isEmpty());
      // New writes are hashed under the v2 schema.
      migrated.recordSighting(BUCKET, "1.2.3.4", 6888, NOW_MS);
      assertEquals(CrawlerStore.endpointHash("1.2.3.4", 6888), readRawEndpointHash(v1File, BUCKET));
      // The torrents table exists and works after migration.
      migrated.recordTorrent("ih-migrated", "Name", 5L, 1, "pub", NOW_MS);
      assertEquals(1, migrated.torrentCount());
    } finally {
      migrated.close();
    }
  }

  private static CrawlerStore.Endpoint findEndpoint(
      List<CrawlerStore.Endpoint> endpoints, String host, int port) {
    String hash = CrawlerStore.endpointHash(host, port);
    for (CrawlerStore.Endpoint endpoint : endpoints) {
      if (endpoint.endpointHash().equals(hash)) {
        return endpoint;
      }
    }
    return null;
  }

  private static String readRawEndpointHash(File file, long bucket) throws Exception {
    try (Connection conn = openRawConnection(file);
        PreparedStatement ps =
            conn.prepareStatement("SELECT endpoint_hash FROM presence WHERE bucket_index = ?")) {
      ps.setLong(1, bucket);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private static int readUserVersion(File file) throws Exception {
    Class.forName("org.sqlite.JDBC");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        Statement s = conn.createStatement();
        ResultSet rs = s.executeQuery("PRAGMA user_version")) {
      return rs.next() ? rs.getInt(1) : -1;
    }
  }

  private static void createV1Database(File file) throws Exception {
    Class.forName("org.sqlite.JDBC");
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        Statement s = conn.createStatement()) {
      s.execute(
          "CREATE TABLE presence (bucket_index INTEGER NOT NULL, host TEXT NOT NULL, "
              + "port INTEGER NOT NULL, first_seen_ms INTEGER NOT NULL, "
              + "last_seen_ms INTEGER NOT NULL, sightings INTEGER NOT NULL, "
              + "PRIMARY KEY(bucket_index, host, port))");
      s.execute(
          "INSERT INTO presence (bucket_index, host, port, first_seen_ms, last_seen_ms, sightings) "
              + "VALUES ("
              + BUCKET
              + ", '1.2.3.4', 6888, "
              + NOW_MS
              + ", "
              + NOW_MS
              + ", 1)");
    }
  }

  private static Connection openRawConnection(File file) throws Exception {
    Class.forName("org.sqlite.JDBC");
    return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
  }
}
