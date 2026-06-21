/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SQLite persistence for the local node's karma chain and peer karma aggregate scores.
 *
 * <p>Shares the same database file as {@link LocalIndexTable} (WAL mode allows multiple
 * connections). Adds two new tables:
 *
 * <ul>
 *   <li>{@code karma_chain} — append-only chain entries
 *   <li>{@code peer_karma} — aggregate scores per peer for fast lookup
 * </ul>
 */
public final class KarmaChainTable implements KarmaChainStore {

  private static final Logger LOG = Logger.getLogger(KarmaChainTable.class);

  static final String CHAIN_TABLE = "karma_chain";
  static final String PEER_TABLE = "peer_karma";

  private static final String CREATE_CHAIN_TABLE_SQL =
      "CREATE TABLE IF NOT EXISTS "
          + CHAIN_TABLE
          + " ("
          + "seq INTEGER PRIMARY KEY, "
          + "kind TEXT NOT NULL, "
          + "prev_hash TEXT NOT NULL, "
          + "endorser_pub TEXT NOT NULL, "
          + "timestamp INTEGER NOT NULL, "
          + "block_height INTEGER NOT NULL, "
          + "block_hash TEXT NOT NULL, "
          + "epoch INTEGER, "
          + "energy REAL, "
          + "peer_pub TEXT, "
          + "info_hash TEXT, "
          + "score_delta INTEGER, "
          + "signature TEXT NOT NULL, "
          + "canonical_bytes BLOB NOT NULL"
          + ")";

  private static final String CREATE_PEER_TABLE_SQL =
      "CREATE TABLE IF NOT EXISTS "
          + PEER_TABLE
          + " ("
          + "peer_pub TEXT PRIMARY KEY, "
          + "total_score INTEGER NOT NULL DEFAULT 0, "
          + "endorsement_count INTEGER NOT NULL DEFAULT 0, "
          + "last_endorsed_at INTEGER NOT NULL DEFAULT 0"
          + ")";

  private static final String CREATE_INDEX_EPOCH_SQL =
      "CREATE INDEX IF NOT EXISTS idx_" + CHAIN_TABLE + "_epoch ON " + CHAIN_TABLE + " (epoch)";
  private static final String CREATE_INDEX_BLOCK_SQL =
      "CREATE INDEX IF NOT EXISTS idx_"
          + CHAIN_TABLE
          + "_block ON "
          + CHAIN_TABLE
          + " (block_height)";
  private static final String CREATE_INDEX_SCORE_SQL =
      "CREATE INDEX IF NOT EXISTS idx_" + PEER_TABLE + "_score ON " + PEER_TABLE + " (total_score)";

  private final Connection connection;
  private final File path;
  private final AtomicBoolean open = new AtomicBoolean(false);

  private KarmaChainTable(File dbFile) {
    this.path = dbFile;
    try {
      File parent = dbFile.getAbsoluteFile().getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        throw new IllegalStateException("Could not create database directory: " + parent);
      }
      String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
      this.connection = DriverManager.getConnection(url, "SA", "");
      configurePragmas();
      initializeSchema();
      open.set(true);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to open KarmaChainTable at " + dbFile, e);
    }
  }

  public static KarmaChainTable open(File dbFile) {
    return new KarmaChainTable(dbFile);
  }

  public File path() {
    return path;
  }

  public boolean isOpen() {
    return open.get();
  }

  public void close() {
    if (!open.compareAndSet(true, false)) {
      return;
    }
    try {
      connection.close();
    } catch (Throwable t) {
      LOG.warn("Error closing connection", t);
    }
  }

  /**
   * Append a chain entry. Also updates the peer_karma aggregate table if the entry is an
   * endorsement.
   */
  public void append(KarmaChainEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry is null");
    }
    ensureOpen();

    // Compute the epoch from block height for endorsements (they don't
    // carry an explicit epoch field). This enables epoch-range queries.
    long epoch =
        entry.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT
            ? entry.epoch()
            : KarmaConstants.epochForHeight(entry.blockHeight());

    synchronized (connection) {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "INSERT OR REPLACE INTO "
                  + CHAIN_TABLE
                  + " ("
                  + "seq, kind, prev_hash, endorser_pub, timestamp, "
                  + "block_height, block_hash, epoch, energy, "
                  + "peer_pub, info_hash, score_delta, signature, "
                  + "canonical_bytes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
        ps.setLong(1, entry.seq());
        ps.setString(2, entry.kind().code());
        ps.setString(3, Hex.encode(entry.prevHash()));
        ps.setString(4, Base64.getEncoder().withoutPadding().encodeToString(entry.endorserPub()));
        ps.setLong(5, entry.timestamp());
        ps.setLong(6, entry.blockHeight());
        ps.setString(7, Hex.encode(entry.blockHash()));
        ps.setLong(8, epoch);
        if (entry.energy() != null) {
          ps.setDouble(9, entry.energy());
        } else {
          ps.setNull(9, Types.REAL);
        }
        if (entry.peerPub() != null) {
          ps.setString(10, Base64.getEncoder().withoutPadding().encodeToString(entry.peerPub()));
        } else {
          ps.setNull(10, Types.VARCHAR);
        }
        if (entry.infoHash() != null) {
          ps.setString(11, Hex.encode(entry.infoHash()));
        } else {
          ps.setNull(11, Types.VARCHAR);
        }
        if (entry.scoreDelta() != null) {
          ps.setInt(12, entry.scoreDelta());
        } else {
          ps.setNull(12, Types.INTEGER);
        }
        ps.setString(13, Base64.getEncoder().withoutPadding().encodeToString(entry.signature()));
        ps.setBytes(14, entry.canonicalBytes());
        ps.executeUpdate();
      } catch (SQLException e) {
        throw new IllegalStateException("Failed to append karma entry seq=" + entry.seq(), e);
      }
    }

    // Update peer aggregate if this is an endorsement
    if (entry.kind() == KarmaChainEntry.Kind.ENDORSEMENT
        && entry.peerPub() != null
        && entry.scoreDelta() != null) {
      updatePeerKarma(entry.peerPub(), entry.scoreDelta(), entry.timestamp());
    }
  }

  /**
   * Load the full chain into a KarmaChain. Used on startup. Returns an empty chain if no entries
   * are persisted.
   */
  public KarmaChain loadChain(byte[] ownerPub) {
    if (ownerPub == null || ownerPub.length != 32) {
      throw new IllegalArgumentException("ownerPub must be 32 bytes");
    }
    ensureOpen();
    List<KarmaChainEntry> loaded = new ArrayList<>();
    synchronized (connection) {
      try (Statement s = connection.createStatement();
          ResultSet rs =
              s.executeQuery(
                  "SELECT kind, prev_hash, endorser_pub, timestamp, "
                      + "block_height, block_hash, epoch, energy, "
                      + "peer_pub, info_hash, score_delta, signature "
                      + "FROM "
                      + CHAIN_TABLE
                      + " ORDER BY seq ASC")) {
        while (rs.next()) {
          int idx = 1;
          try {
            KarmaChainEntry.Kind kind =
                "EC".equals(rs.getString(idx++))
                    ? KarmaChainEntry.Kind.EPOCH_COMMITMENT
                    : KarmaChainEntry.Kind.ENDORSEMENT;
            byte[] prevHash = Hex.decode(rs.getString(idx++));
            byte[] endorserPub = Base64.getDecoder().decode(rs.getString(idx++));
            long timestamp = rs.getLong(idx++);
            long blockHeight = rs.getLong(idx++);
            byte[] blockHash = Hex.decode(rs.getString(idx++));
            long epoch = rs.getLong(idx++);
            boolean epochWasNull = rs.wasNull();
            double energy = rs.getDouble(idx++);
            boolean energyWasNull = rs.wasNull();
            String peerPubStr = rs.getString(idx++);
            byte[] peerPub = peerPubStr != null ? Base64.getDecoder().decode(peerPubStr) : null;
            String infoHashStr = rs.getString(idx++);
            byte[] infoHash = infoHashStr != null ? Hex.decode(infoHashStr) : null;
            int scoreDelta = rs.getInt(idx++);
            boolean scoreDeltaWasNull = rs.wasNull();
            byte[] signature = Base64.getDecoder().decode(rs.getString(idx++));

            KarmaChainEntry entry =
                KarmaChainEntry.fromStoredFields(
                    kind,
                    prevHash,
                    loaded.size(),
                    endorserPub,
                    timestamp,
                    blockHeight,
                    blockHash,
                    epochWasNull ? null : epoch,
                    energyWasNull ? null : energy,
                    peerPub,
                    infoHash,
                    scoreDeltaWasNull ? null : scoreDelta,
                    signature);
            loaded.add(entry);
          } catch (Throwable t) {
            LOG.warn("Skipping corrupt karma chain entry during load", t);
          }
        }
      } catch (SQLException e) {
        throw new IllegalStateException("Failed to load karma chain", e);
      }
    }
    if (loaded.isEmpty()) {
      return new KarmaChain(ownerPub);
    }
    KarmaChain chain = KarmaChain.load(ownerPub, loaded);
    if (chain == null) {
      LOG.warn("Persisted karma chain failed verification; starting fresh");
      return new KarmaChain(ownerPub);
    }
    return chain;
  }

  /** Get the aggregate karma score for a peer. Returns 0 if the peer has no endorsements. */
  public long getPeerKarma(byte[] peerPub) {
    if (peerPub == null) return 0;
    ensureOpen();
    synchronized (connection) {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "SELECT total_score FROM " + PEER_TABLE + " WHERE peer_pub = ?")) {
        ps.setString(1, Base64.getEncoder().withoutPadding().encodeToString(peerPub));
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return rs.getLong(1);
          }
        }
      } catch (SQLException e) {
        LOG.warn("Failed to read peer karma", e);
      }
    }
    return 0;
  }

  /** Get all peers sorted by total_score descending. */
  public List<PeerKarmaScore> getTopPeers(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }
    ensureOpen();
    List<PeerKarmaScore> out = new ArrayList<>();
    synchronized (connection) {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "SELECT peer_pub, total_score, endorsement_count, last_endorsed_at "
                  + "FROM "
                  + PEER_TABLE
                  + " ORDER BY total_score DESC LIMIT ?")) {
        ps.setInt(1, limit);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            out.add(
                new PeerKarmaScore(
                    Base64.getDecoder().decode(rs.getString(1)),
                    rs.getLong(2),
                    rs.getInt(3),
                    rs.getLong(4)));
          }
        }
      } catch (SQLException e) {
        LOG.warn("Failed to read top peers", e);
      }
    }
    return out;
  }

  /**
   * Count endorsements by a given endorser in a given epoch. Used to verify karma chain energy
   * budgets.
   */
  public int endorsementCountInEpoch(byte[] endorserPub, long epoch) {
    if (endorserPub == null) return 0;
    ensureOpen();
    synchronized (connection) {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "SELECT COUNT(*) FROM "
                  + CHAIN_TABLE
                  + " WHERE kind = 'EN' AND endorser_pub = ? AND epoch >= ? "
                  + " AND epoch < ?")) {
        ps.setString(1, Base64.getEncoder().withoutPadding().encodeToString(endorserPub));
        ps.setLong(2, epoch);
        ps.setLong(3, epoch + 1);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return rs.getInt(1);
          }
        }
      } catch (SQLException e) {
        LOG.warn("Failed to count endorsements in epoch", e);
      }
    }
    return 0;
  }

  private void updatePeerKarma(byte[] peerPub, int scoreDelta, long timestamp) {
    synchronized (connection) {
      try (PreparedStatement ps =
          connection.prepareStatement(
              "INSERT INTO "
                  + PEER_TABLE
                  + " (peer_pub, total_score, endorsement_count, last_endorsed_at) "
                  + "VALUES (?, ?, 1, ?) "
                  + "ON CONFLICT(peer_pub) DO UPDATE SET "
                  + "total_score = total_score + ?, "
                  + "endorsement_count = endorsement_count + 1, "
                  + "last_endorsed_at = ?")) {
        String b64 = Base64.getEncoder().withoutPadding().encodeToString(peerPub);
        ps.setString(1, b64);
        ps.setLong(2, scoreDelta);
        ps.setLong(3, timestamp);
        ps.setLong(4, scoreDelta);
        ps.setLong(5, timestamp);
        ps.executeUpdate();
      } catch (SQLException e) {
        LOG.warn("Failed to update peer karma", e);
      }
    }
  }

  private void configurePragmas() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.execute("PRAGMA journal_mode = WAL");
      s.execute("PRAGMA synchronous = NORMAL");
      s.execute("PRAGMA foreign_keys = ON");
    }
  }

  private void initializeSchema() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.execute(CREATE_CHAIN_TABLE_SQL);
      s.execute(CREATE_PEER_TABLE_SQL);
      s.execute(CREATE_INDEX_EPOCH_SQL);
      s.execute(CREATE_INDEX_BLOCK_SQL);
      s.execute(CREATE_INDEX_SCORE_SQL);
    }
  }

  private void ensureOpen() {
    if (!open.get()) {
      throw new IllegalStateException("KarmaChainTable is closed");
    }
  }

  /** Immutable record of a peer's aggregate karma score. */
  public static final class PeerKarmaScore {
    private final byte[] peerPub;
    private final long totalScore;
    private final int endorsementCount;
    private final long lastEndorsedAt;

    public PeerKarmaScore(
        byte[] peerPub, long totalScore, int endorsementCount, long lastEndorsedAt) {
      this.peerPub = peerPub == null ? new byte[0] : peerPub.clone();
      this.totalScore = totalScore;
      this.endorsementCount = endorsementCount;
      this.lastEndorsedAt = lastEndorsedAt;
    }

    public byte[] peerPub() {
      return peerPub.clone();
    }

    public long totalScore() {
      return totalScore;
    }

    public int endorsementCount() {
      return endorsementCount;
    }

    public long lastEndorsedAt() {
      return lastEndorsedAt;
    }
  }
}
