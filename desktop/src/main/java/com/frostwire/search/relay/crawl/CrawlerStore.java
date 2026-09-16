/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import com.frostwire.search.relay.RelayConstants;
import com.frostwire.util.Logger;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SQLite-backed store for time-bucketed IceBridge DHT presence observations.
 *
 * <p>Each row is one {@code (bucket_index, host, port)} endpoint observed under a heartbeat topic.
 * A repeated sighting in the same bucket bumps {@code sightings} and {@code last_seen_ms} instead
 * of inserting a new row, so {@code sightings == 1} identifies endpoints discovered in the current
 * pass.
 *
 * <p>This class only persists addresses returned by DHT lookups; it never dials or authenticates
 * any peer.
 */
public final class CrawlerStore implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(CrawlerStore.class);

  static final String TABLE = "presence";

  private static final String CREATE_TABLE_SQL =
      "CREATE TABLE IF NOT EXISTS "
          + TABLE
          + " ("
          + "bucket_index INTEGER NOT NULL, "
          + "host TEXT NOT NULL, "
          + "port INTEGER NOT NULL, "
          + "first_seen_ms INTEGER NOT NULL, "
          + "last_seen_ms INTEGER NOT NULL, "
          + "sightings INTEGER NOT NULL, "
          + "PRIMARY KEY(bucket_index, host, port)"
          + ")";

  private static final String UPSERT_SQL =
      "INSERT INTO "
          + TABLE
          + " (bucket_index, host, port, first_seen_ms, last_seen_ms, sightings) "
          + "VALUES (?, ?, ?, ?, ?, 1) "
          + "ON CONFLICT(bucket_index, host, port) DO UPDATE SET "
          + "last_seen_ms = excluded.last_seen_ms, "
          + "sightings = sightings + 1";

  static {
    try {
      // Explicit load so a non-ServiceLoader classpath still finds the driver.
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("sqlite-jdbc driver not on classpath", e);
    }
  }

  private final Connection connection;
  private final File path;
  private final AtomicBoolean open = new AtomicBoolean(false);

  private CrawlerStore(File dbFile) {
    if (dbFile == null) {
      throw new IllegalArgumentException("dbFile is null");
    }
    this.path = dbFile;
    try {
      File parent = dbFile.getAbsoluteFile().getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        throw new IllegalStateException("Could not create database directory: " + parent);
      }
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
      configurePragmas();
      initializeSchema();
      open.set(true);
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to open CrawlerStore at " + dbFile, e);
    }
  }

  public static CrawlerStore open(File dbFile) {
    return new CrawlerStore(dbFile);
  }

  public File path() {
    return path;
  }

  public boolean isOpen() {
    return open.get();
  }

  @Override
  public void close() {
    if (!open.compareAndSet(true, false)) {
      return;
    }
    try {
      connection.close();
    } catch (Throwable t) {
      LOG.warn("Error closing CrawlerStore connection", t);
    }
  }

  /** Upsert one endpoint sighting for {@code bucketIndex}. */
  public void recordSighting(long bucketIndex, String host, int port, long nowMs) {
    ensureOpen();
    if (!isValid(host, port)) {
      return;
    }
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(UPSERT_SQL)) {
        bindUpsert(ps, bucketIndex, host, port, nowMs);
        ps.executeUpdate();
      } catch (SQLException e) {
        throw new IllegalStateException(
            "recordSighting failed for " + host + ":" + port + " bucket " + bucketIndex, e);
      }
    }
  }

  /**
   * Upsert a batch of endpoints for {@code bucketIndex}. All rows are written in a single
   * transaction so a partial batch is never persisted. {@code hosts} and {@code ports} are parallel
   * lists; only the common prefix is considered.
   */
  public void recordSightings(
      long bucketIndex, List<String> hosts, List<Integer> ports, long nowMs) {
    ensureOpen();
    if (hosts == null || ports == null || hosts.isEmpty() || ports.isEmpty()) {
      return;
    }
    int n = Math.min(hosts.size(), ports.size());
    synchronized (connection) {
      boolean previousAutoCommit;
      try {
        previousAutoCommit = connection.getAutoCommit();
      } catch (SQLException e) {
        throw new IllegalStateException("recordSightings could not read autoCommit", e);
      }
      try {
        connection.setAutoCommit(false);
        try (PreparedStatement ps = connection.prepareStatement(UPSERT_SQL)) {
          for (int i = 0; i < n; i++) {
            String host = hosts.get(i);
            Integer port = ports.get(i);
            if (host == null || port == null || !isValid(host, port)) {
              continue;
            }
            bindUpsert(ps, bucketIndex, host, port, nowMs);
            ps.addBatch();
          }
          ps.executeBatch();
        }
        connection.commit();
      } catch (SQLException e) {
        try {
          connection.rollback();
        } catch (SQLException rollbackError) {
          LOG.warn("recordSightings rollback failed for bucket " + bucketIndex, rollbackError);
        }
        throw new IllegalStateException("recordSightings failed for bucket " + bucketIndex, e);
      } finally {
        try {
          connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException e) {
          LOG.warn("recordSightings failed to restore autoCommit", e);
        }
      }
    }
  }

  /**
   * Per-bucket counts for the {@code lastNBuckets} buckets ending at the bucket containing {@code
   * nowMs}, oldest first. Buckets with no rows are returned as zeros so the report always covers
   * the full window. {@code newEndpoints} counts rows seen exactly once ({@code sightings == 1}).
   */
  public List<BucketCount> countsByBucket(int lastNBuckets, long nowMs) {
    ensureOpen();
    int window = Math.max(1, lastNBuckets);
    long current = RelayConstants.heartbeatBucketIndex(nowMs);
    long first = current - (window - 1);
    Map<Long, BucketCount> byBucket = new HashMap<>();
    String sql =
        "SELECT bucket_index, COUNT(*) AS distinct_endpoints, "
            + "SUM(CASE WHEN sightings = 1 THEN 1 ELSE 0 END) AS new_endpoints "
            + "FROM "
            + TABLE
            + " WHERE bucket_index >= ? AND bucket_index <= ? "
            + "GROUP BY bucket_index";
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setLong(1, first);
        ps.setLong(2, current);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            long bucket = rs.getLong(1);
            byBucket.put(bucket, new BucketCount(bucket, rs.getInt(2), rs.getInt(3)));
          }
        }
      } catch (SQLException e) {
        throw new IllegalStateException("countsByBucket failed", e);
      }
    }
    List<BucketCount> out = new ArrayList<>(window);
    for (long bucket = first; bucket <= current; bucket++) {
      BucketCount count = byBucket.get(bucket);
      out.add(count != null ? count : new BucketCount(bucket, 0, 0));
    }
    return out;
  }

  /** Endpoints observed in one bucket, most recently seen first. */
  public List<Endpoint> endpoints(long bucketIndex, int limit) {
    ensureOpen();
    List<Endpoint> out = new ArrayList<>();
    if (limit <= 0) {
      return out;
    }
    String sql =
        "SELECT host, port, first_seen_ms, last_seen_ms, sightings FROM "
            + TABLE
            + " WHERE bucket_index = ? "
            + "ORDER BY last_seen_ms DESC, host ASC, port ASC LIMIT ?";
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setLong(1, bucketIndex);
        ps.setInt(2, limit);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            out.add(
                new Endpoint(
                    rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getInt(5)));
          }
        }
      } catch (SQLException e) {
        throw new IllegalStateException("endpoints failed for bucket " + bucketIndex, e);
      }
    }
    return out;
  }

  /** Distinct {@code (host, port)} pairs across the last {@code lastNBuckets} buckets. */
  public int totalDistinct(int lastNBuckets, long nowMs) {
    ensureOpen();
    int window = Math.max(1, lastNBuckets);
    long current = RelayConstants.heartbeatBucketIndex(nowMs);
    long first = current - (window - 1);
    String sql =
        "SELECT COUNT(*) FROM (SELECT DISTINCT host, port FROM "
            + TABLE
            + " WHERE bucket_index >= ? AND bucket_index <= ?)";
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setLong(1, first);
        ps.setLong(2, current);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return rs.getInt(1);
          }
        }
      } catch (SQLException e) {
        throw new IllegalStateException("totalDistinct failed", e);
      }
    }
    return 0;
  }

  private static void bindUpsert(
      PreparedStatement ps, long bucketIndex, String host, int port, long nowMs)
      throws SQLException {
    ps.setLong(1, bucketIndex);
    ps.setString(2, host);
    ps.setInt(3, port);
    ps.setLong(4, nowMs);
    ps.setLong(5, nowMs);
  }

  private static boolean isValid(String host, int port) {
    return host != null && !host.isBlank() && port > 0;
  }

  private void configurePragmas() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.execute("PRAGMA journal_mode = WAL");
      s.execute("PRAGMA synchronous = NORMAL");
    }
  }

  private void initializeSchema() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.execute(CREATE_TABLE_SQL);
    }
  }

  private void ensureOpen() {
    if (!open.get()) {
      throw new IllegalStateException("CrawlerStore is closed");
    }
  }

  /** Aggregated presence counts for one heartbeat bucket. */
  public record BucketCount(long bucketIndex, int distinctEndpoints, int newEndpoints) {}

  /** One observed endpoint and its first/last sighting timestamps within a bucket. */
  public record Endpoint(String host, int port, long firstSeenMs, long lastSeenMs, int sightings) {}
}
