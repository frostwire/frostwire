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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * SQLite-backed, anonymous store for time-bucketed IceBridge presence observations and the
 * shared-torrent catalog advertised through relay control APIs.
 *
 * <p>Presence rows are keyed by a truncated SHA-1 of {@code "host:port"}; the raw host/IP is
 * <b>never</b> persisted, only the 16-hex-character {@code endpoint_hash}. A repeated sighting in
 * the same bucket bumps {@code sightings} and {@code last_seen_ms} instead of inserting a new row,
 * so {@code sightings == 1} identifies endpoints discovered in the current pass.
 *
 * <p>Catalog rows are keyed by {@code infohash} and carry only metadata plus the publishing peer's
 * public key ({@code publisher_peer_id}); again, no address is ever stored.
 *
 * <p>This class only persists data returned by DHT lookups and relay control APIs; it never dials
 * or authenticates any peer directly.
 */
public final class CrawlerStore implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(CrawlerStore.class);

  static final String TABLE = "presence";
  static final String TORRENTS_TABLE = "torrents";
  static final int SCHEMA_VERSION = 2;
  static final int ENDPOINT_HASH_HEX_CHARS = 16;

  private static final String CREATE_PRESENCE_SQL =
      "CREATE TABLE IF NOT EXISTS "
          + TABLE
          + " ("
          + "bucket_index INTEGER NOT NULL, "
          + "endpoint_hash TEXT NOT NULL, "
          + "first_seen_ms INTEGER NOT NULL, "
          + "last_seen_ms INTEGER NOT NULL, "
          + "sightings INTEGER NOT NULL, "
          + "PRIMARY KEY(bucket_index, endpoint_hash)"
          + ")";

  private static final String CREATE_TORRENTS_SQL =
      "CREATE TABLE IF NOT EXISTS "
          + TORRENTS_TABLE
          + " ("
          + "infohash TEXT PRIMARY KEY, "
          + "name TEXT NOT NULL, "
          + "size_bytes INTEGER NOT NULL, "
          + "files INTEGER NOT NULL, "
          + "publisher_peer_id TEXT NOT NULL, "
          + "first_seen_ms INTEGER NOT NULL, "
          + "last_seen_ms INTEGER NOT NULL, "
          + "seen_count INTEGER NOT NULL"
          + ")";

  private static final String UPSERT_PRESENCE_SQL =
      "INSERT INTO "
          + TABLE
          + " (bucket_index, endpoint_hash, first_seen_ms, last_seen_ms, sightings) "
          + "VALUES (?, ?, ?, ?, 1) "
          + "ON CONFLICT(bucket_index, endpoint_hash) DO UPDATE SET "
          + "last_seen_ms = excluded.last_seen_ms, "
          + "sightings = sightings + 1";

  private static final String UPSERT_TORRENT_SQL =
      "INSERT INTO "
          + TORRENTS_TABLE
          + " (infohash, name, size_bytes, files, publisher_peer_id, first_seen_ms, last_seen_ms, "
          + "seen_count) VALUES (?, ?, ?, ?, ?, ?, ?, 1) "
          + "ON CONFLICT(infohash) DO UPDATE SET "
          + "name = excluded.name, "
          + "size_bytes = excluded.size_bytes, "
          + "files = excluded.files, "
          + "publisher_peer_id = excluded.publisher_peer_id, "
          + "last_seen_ms = excluded.last_seen_ms, "
          + "seen_count = seen_count + 1";

  private static final String TORRENTS_PAGE_SQL =
      "SELECT infohash, name, size_bytes, files, publisher_peer_id, first_seen_ms, last_seen_ms, "
          + "seen_count FROM "
          + TORRENTS_TABLE
          + " ORDER BY last_seen_ms DESC, infohash ASC LIMIT ?";

  private static final String TOP_TORRENTS_SQL =
      "SELECT infohash, name, size_bytes, files, publisher_peer_id, first_seen_ms, last_seen_ms, "
          + "seen_count FROM "
          + TORRENTS_TABLE
          + " ORDER BY seen_count DESC, last_seen_ms DESC, infohash ASC LIMIT ?";

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

  /**
   * Truncated SHA-1 hex (16 lowercase hex characters) of {@code "host:port"}.
   *
   * <p>Deliberately lossy and non-reversible enough for anonymized counting; the raw address must
   * never be persisted or logged by callers.
   */
  static String endpointHash(String host, int port) {
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      byte[] digest = sha1.digest((host + ":" + port).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(ENDPOINT_HASH_HEX_CHARS);
      for (int i = 0; i < ENDPOINT_HASH_HEX_CHARS / 2; i++) {
        hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
        hex.append(Character.forDigit(digest[i] & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 unavailable", e);
    }
  }

  /** Upsert one endpoint sighting for {@code bucketIndex}; the host is hashed and never stored. */
  public void recordSighting(long bucketIndex, String host, int port, long nowMs) {
    ensureOpen();
    if (!isValid(host, port)) {
      return;
    }
    upsertEndpointHash(bucketIndex, endpointHash(host, port), nowMs);
  }

  /**
   * Upsert a batch of endpoints for {@code bucketIndex}. All rows are written in a single
   * transaction so a partial batch is never persisted. {@code hosts} and {@code ports} are parallel
   * lists; only the common prefix is considered. Each host is hashed before storage.
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
        try (PreparedStatement ps = connection.prepareStatement(UPSERT_PRESENCE_SQL)) {
          for (int i = 0; i < n; i++) {
            String host = hosts.get(i);
            Integer port = ports.get(i);
            if (host == null || port == null || !isValid(host, port)) {
              continue;
            }
            bindPresence(ps, bucketIndex, endpointHash(host, port), nowMs);
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
   * Upsert one catalog torrent. On conflict the metadata and {@code last_seen_ms} are refreshed and
   * {@code seen_count} is incremented; {@code first_seen_ms} is preserved. The publisher is a peer
   * public key, never an address.
   */
  public void recordTorrent(
      String infohash, String name, long sizeBytes, int files, String publisherPeerId, long nowMs) {
    ensureOpen();
    if (infohash == null || infohash.isBlank()) {
      return;
    }
    String safeName = name == null ? "" : name;
    String safePublisher = publisherPeerId == null ? "" : publisherPeerId;
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(UPSERT_TORRENT_SQL)) {
        ps.setString(1, infohash.trim());
        ps.setString(2, safeName);
        ps.setLong(3, Math.max(0L, sizeBytes));
        ps.setInt(4, Math.max(0, files));
        ps.setString(5, safePublisher);
        ps.setLong(6, nowMs);
        ps.setLong(7, nowMs);
        ps.executeUpdate();
      } catch (SQLException e) {
        throw new IllegalStateException("recordTorrent failed", e);
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

  /**
   * Endpoints observed in one bucket, most recently seen first. Hosts are exposed only as hashes.
   */
  public List<Endpoint> endpoints(long bucketIndex, int limit) {
    ensureOpen();
    List<Endpoint> out = new ArrayList<>();
    if (limit <= 0) {
      return out;
    }
    String sql =
        "SELECT endpoint_hash, first_seen_ms, last_seen_ms, sightings FROM "
            + TABLE
            + " WHERE bucket_index = ? "
            + "ORDER BY last_seen_ms DESC, endpoint_hash ASC LIMIT ?";
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setLong(1, bucketIndex);
        ps.setInt(2, limit);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            out.add(new Endpoint(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getInt(4)));
          }
        }
      } catch (SQLException e) {
        throw new IllegalStateException("endpoints failed for bucket " + bucketIndex, e);
      }
    }
    return out;
  }

  /** Distinct endpoint hashes across the last {@code lastNBuckets} buckets. */
  public int totalDistinct(int lastNBuckets, long nowMs) {
    ensureOpen();
    int window = Math.max(1, lastNBuckets);
    long current = RelayConstants.heartbeatBucketIndex(nowMs);
    long first = current - (window - 1);
    String sql =
        "SELECT COUNT(*) FROM (SELECT DISTINCT endpoint_hash FROM "
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

  /** Catalog torrents most recently seen first. */
  public List<Torrent> torrents(int limit) {
    ensureOpen();
    return queryTorrents(TORRENTS_PAGE_SQL, limit);
  }

  /** Catalog torrents most frequently advertised first. */
  public List<Torrent> topTorrents(int limit) {
    ensureOpen();
    return queryTorrents(TOP_TORRENTS_SQL, limit);
  }

  /** Number of distinct catalog infohashes stored. */
  public int torrentCount() {
    ensureOpen();
    synchronized (connection) {
      try (Statement s = connection.createStatement();
          ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + TORRENTS_TABLE)) {
        if (rs.next()) {
          return rs.getInt(1);
        }
      } catch (SQLException e) {
        throw new IllegalStateException("torrentCount failed", e);
      }
    }
    return 0;
  }

  /** Sum of {@code seen_count} across all stored catalog torrents. */
  public long totalTorrentSightings() {
    ensureOpen();
    synchronized (connection) {
      try (Statement s = connection.createStatement();
          ResultSet rs =
              s.executeQuery("SELECT COALESCE(SUM(seen_count), 0) FROM " + TORRENTS_TABLE)) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      } catch (SQLException e) {
        throw new IllegalStateException("totalTorrentSightings failed", e);
      }
    }
    return 0L;
  }

  private List<Torrent> queryTorrents(String sql, int limit) {
    List<Torrent> out = new ArrayList<>();
    if (limit <= 0) {
      return out;
    }
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setInt(1, limit);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            out.add(
                new Torrent(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getLong(3),
                    rs.getInt(4),
                    rs.getString(5),
                    rs.getLong(6),
                    rs.getLong(7),
                    rs.getInt(8)));
          }
        }
      } catch (SQLException e) {
        throw new IllegalStateException("torrent query failed", e);
      }
    }
    return out;
  }

  private void upsertEndpointHash(long bucketIndex, String endpointHash, long nowMs) {
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(UPSERT_PRESENCE_SQL)) {
        bindPresence(ps, bucketIndex, endpointHash, nowMs);
        ps.executeUpdate();
      } catch (SQLException e) {
        throw new IllegalStateException("recordSighting failed for bucket " + bucketIndex, e);
      }
    }
  }

  private static void bindPresence(
      PreparedStatement ps, long bucketIndex, String endpointHash, long nowMs) throws SQLException {
    ps.setLong(1, bucketIndex);
    ps.setString(2, endpointHash);
    ps.setLong(3, nowMs);
    ps.setLong(4, nowMs);
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

  /**
   * Bring the database up to {@link #SCHEMA_VERSION}.
   *
   * <p>Databases written by the v1 crawler stored raw {@code host}/{@code port} columns. Because
   * those values are PII, the v1 {@code presence} table is dropped and recreated rather than
   * migrated in place: this is a documented, intentional data loss that anonymizes the store. V2+
   * databases are left untouched and only re-validated for missing tables.
   */
  private void initializeSchema() throws SQLException {
    int version = readUserVersion();
    if (version >= SCHEMA_VERSION) {
      createTables();
      return;
    }
    boolean legacyPresence = presenceHasLegacyHostColumn();
    synchronized (connection) {
      boolean previousAutoCommit = true;
      try {
        previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        if (legacyPresence) {
          LOG.warn(
              "CrawlerStore v1 schema detected; dropping raw-address presence table "
                  + "(v1 data loss) and migrating to anonymous v2 schema");
          try (Statement s = connection.createStatement()) {
            s.execute("DROP TABLE IF EXISTS " + TABLE);
          }
        }
        createTables();
        try (Statement s = connection.createStatement()) {
          s.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
        connection.commit();
      } catch (SQLException e) {
        try {
          connection.rollback();
        } catch (SQLException rollbackError) {
          LOG.warn("Schema migration rollback failed", rollbackError);
        }
        throw new IllegalStateException("CrawlerStore schema migration failed", e);
      } finally {
        try {
          connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException e) {
          LOG.warn("Schema migration failed to restore autoCommit", e);
        }
      }
    }
  }

  private void createTables() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.execute(CREATE_PRESENCE_SQL);
      s.execute(CREATE_TORRENTS_SQL);
    }
  }

  private int readUserVersion() throws SQLException {
    try (Statement s = connection.createStatement();
        ResultSet rs = s.executeQuery("PRAGMA user_version")) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  private boolean presenceHasLegacyHostColumn() throws SQLException {
    String sql = "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?";
    synchronized (connection) {
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, TABLE);
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) {
            return false;
          }
          String ddl = rs.getString(1);
          return ddl != null && ddl.toLowerCase(java.util.Locale.US).contains("host");
        }
      }
    }
  }

  private void ensureOpen() {
    if (!open.get()) {
      throw new IllegalStateException("CrawlerStore is closed");
    }
  }

  /** Aggregated presence counts for one heartbeat bucket. */
  public record BucketCount(long bucketIndex, int distinctEndpoints, int newEndpoints) {}

  /** One observed endpoint as an anonymous hash plus its first/last sighting timestamps. */
  public record Endpoint(String endpointHash, long firstSeenMs, long lastSeenMs, int sightings) {}

  /** One catalog torrent and its aggregated advertisement metadata. */
  public record Torrent(
      String infohash,
      String name,
      long sizeBytes,
      int files,
      String publisherPeerId,
      long firstSeenMs,
      long lastSeenMs,
      int seenCount) {}
}
