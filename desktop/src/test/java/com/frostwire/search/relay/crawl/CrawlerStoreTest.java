/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.RelayConstants;
import java.io.File;
import java.nio.file.Path;
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
    assertEquals("10.0.0.1", endpoint.host());
    assertEquals(6881, endpoint.port());
    assertEquals(NOW_MS, endpoint.firstSeenMs());
    assertEquals(NOW_MS + 5_000, endpoint.lastSeenMs());
    assertEquals(2, endpoint.sightings());
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
    store.close();
    assertFalse(store.isOpen());

    CrawlerStore reopened = CrawlerStore.open(dbFile);
    try {
      assertTrue(reopened.isOpen());
      List<CrawlerStore.Endpoint> endpoints = reopened.endpoints(BUCKET, 10);
      assertEquals(2, endpoints.size());
      assertEquals(2, reopened.totalDistinct(1, NOW_MS));
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

  private static CrawlerStore.Endpoint findEndpoint(
      List<CrawlerStore.Endpoint> endpoints, String host, int port) {
    for (CrawlerStore.Endpoint endpoint : endpoints) {
      if (endpoint.host().equals(host) && endpoint.port() == port) {
        return endpoint;
      }
    }
    return null;
  }
}
