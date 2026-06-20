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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

    /**
     * SQLite + FTS5 backed table holding every torrent this FrostWire node
     * is sharing (or has seen). Powers the "Local" search engine and the
     * {@code IndexAnnouncement} republisher.
     *
     * <p>Schema (per {@code DESIGN_RELAY_REGISTRY.md} §4.2):
     * <pre>
     * CREATE TABLE shared_torrents (
     *     info_hash              TEXT PRIMARY KEY,
     *     name                   TEXT NOT NULL,
     *     size_bytes             INTEGER NOT NULL,
     *     file_count             INTEGER NOT NULL,
     *     files_json             TEXT NOT NULL,
     *     tags                   TEXT,
     *     publisher_node_id      TEXT NOT NULL,
     *     publisher_ed25519_pub  BLOB NOT NULL,
     *     publisher_utp_port     INTEGER,
     *     added_at               INTEGER NOT NULL,
     *     last_seen_at           INTEGER NOT NULL,
     *     last_published_at      INTEGER
     * );
     * CREATE INDEX idx_shared_torrents_added          ON shared_torrents(added_at);
     * CREATE INDEX idx_shared_torrents_name           ON shared_torrents(name);
     * CREATE INDEX idx_shared_torrents_last_published ON shared_torrents(last_published_at);
     * </pre>
     * plus a contentless FTS5 mirror ({@code shared_torrents_fts}) kept in
     * sync by triggers on insert/update/delete.
     *
     * <p>Note on {@code publisher_utp_port}: libtorrent never publishes a
     * NULL port — 0 means "not currently listening" — so the column is
     * always written with a value and reads return 0 for "no port".
     */
public final class LocalIndexTable implements LocalIndex, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(LocalIndexTable.class);

    public static final String DEFAULT_DB_NAME = "frostwire-shared-torrents.db";
    public static final int SCHEMA_VERSION = 2;

    static final String TABLE = "shared_torrents";
    static final String FTS = "shared_torrents_fts";
    static final String FILES_TABLE = "shared_files";
    static final String FILES_FTS = "shared_files_fts";

    /**
     * Maximum number of file paths indexed per torrent in
     * {@code shared_files}. Prevents unbounded row growth for torrents
     * with tens of thousands of files (e.g. Linux distro piece
     * archives). Excess files are silently truncated.
     */
    static final int MAX_FILES_PER_TORRENT = 10_000;

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                    "info_hash TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "size_bytes INTEGER NOT NULL, " +
                    "file_count INTEGER NOT NULL, " +
                    "files_json TEXT NOT NULL, " +
                    "tags TEXT, " +
                    "publisher_node_id TEXT NOT NULL, " +
                    "publisher_ed25519_pub BLOB NOT NULL, " +
                    "publisher_utp_port INTEGER, " +
                    "added_at INTEGER NOT NULL, " +
                    "last_seen_at INTEGER NOT NULL, " +
                    "last_published_at INTEGER" +
                    ")";

    private static final String CREATE_FTS_SQL =
            "CREATE VIRTUAL TABLE IF NOT EXISTS " + FTS + " USING fts5(" +
                    "name, tags, " +
                    "content='" + TABLE + "', content_rowid='rowid', " +
                    "tokenize='porter unicode61'" +
                    ")";

    /**
     * Separate table for individual file paths within torrents.
     * Each row is one file, linked to its parent torrent by
     * {@code torrent_rowid} (which corresponds to the torrent's
     * {@code rowid} in {@code shared_torrents}).
     *
     * <p>No FK constraint because SQLite does not allow FK references
     * to the implicit {@code rowid} column. Cascade delete is handled
     * manually in {@link #delete(String)}.
     */
    private static final String CREATE_FILES_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + FILES_TABLE + " (" +
                    "torrent_rowid INTEGER NOT NULL, " +
                    "file_path TEXT NOT NULL, " +
                    "file_size INTEGER NOT NULL DEFAULT 0" +
                    ")";

    private static final String CREATE_INDEX_FILES_TORRENT_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + FILES_TABLE + "_torrent " +
                    "ON " + FILES_TABLE + " (torrent_rowid)";

    /**
     * FTS5 virtual table indexing individual file paths. Uses a standalone
     * FTS5 table (not external content) to avoid trigger and rowid-reuse
     * issues. Stores {@code torrent_rowid} so search results can join
     * back to {@code shared_torrents} without going through
     * {@code shared_files}.
     */
    private static final String CREATE_FILES_FTS_SQL =
            "CREATE VIRTUAL TABLE IF NOT EXISTS " + FILES_FTS + " USING fts5(" +
                    "file_path, torrent_rowid UNINDEXED, " +
                    "tokenize='porter unicode61'" +
                    ")";

    /**
     * Triggers to keep shared_torrents_fts in sync with shared_torrents
     * (external-content FTS5 pattern).
     */
    private static final String CREATE_TRIGGERS_SQL =
            "CREATE TRIGGER IF NOT EXISTS " + TABLE + "_ai AFTER INSERT ON " + TABLE + " BEGIN " +
                    "INSERT INTO " + FTS + "(rowid, name, tags) VALUES (new.rowid, new.name, new.tags); " +
                    "END;" +
                    "CREATE TRIGGER IF NOT EXISTS " + TABLE + "_ad AFTER DELETE ON " + TABLE + " BEGIN " +
                    "INSERT INTO " + FTS + "(" + FTS + ", rowid, name, tags) VALUES('delete', old.rowid, old.name, old.tags); " +
                    "END;" +
                    "CREATE TRIGGER IF NOT EXISTS " + TABLE + "_au AFTER UPDATE ON " + TABLE + " BEGIN " +
                    "INSERT INTO " + FTS + "(" + FTS + ", rowid, name, tags) VALUES('delete', old.rowid, old.name, old.tags); " +
                    "INSERT INTO " + FTS + "(rowid, name, tags) VALUES (new.rowid, new.name, new.tags); " +
                    "END;";

    /**
     * No triggers for shared_files_fts — the FTS5 index is managed
     * manually in {@link #syncSharedFiles} and {@link #delete}. This
     * avoids issues with FTS5 "delete" trigger commands when rowids
     * are reused after INSERT OR REPLACE on shared_torrents.
     */

    private static final String CREATE_META_SQL =
            "CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)";

    private static final String CREATE_INDEX_ADDED_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + TABLE + "_added ON " + TABLE + " (added_at)";

    private static final String CREATE_INDEX_NAME_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + TABLE + "_name ON " + TABLE + " (name)";

    private static final String CREATE_INDEX_LAST_PUBLISHED_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + TABLE + "_last_published_at ON " + TABLE + " (last_published_at)";

    private final Connection connection;
    private final File path;
    private final AtomicBoolean open = new AtomicBoolean(false);

    private LocalIndexTable(File dbFile) {
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
            throw new IllegalStateException("Failed to open LocalIndexTable at " + dbFile, e);
        }
    }

    public static LocalIndexTable open(File dbFile) {
        return new LocalIndexTable(dbFile);
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

    public void upsert(LocalSharedTorrent t) {
        ensureOpen();
        String upsertSql = "INSERT OR REPLACE INTO " + TABLE +
                " (info_hash, name, size_bytes, file_count, files_json, tags, " +
                " publisher_node_id, publisher_ed25519_pub, publisher_utp_port, " +
                " added_at, last_seen_at, last_published_at) " +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        synchronized (connection) {
            try {
                // Look up and delete old shared_files rows BEFORE the
                // INSERT OR REPLACE, because REPLACE assigns a new rowid —
                // old file rows would be orphaned (no FK on implicit rowid
                // to cascade).
                try (PreparedStatement lookup = connection.prepareStatement(
                        "SELECT rowid FROM " + TABLE + " WHERE info_hash = ?")) {
                    lookup.setString(1, t.infoHashHex());
                    try (ResultSet rs = lookup.executeQuery()) {
                        if (rs.next()) {
                            long oldRowid = rs.getLong(1);
                            try (PreparedStatement del = connection.prepareStatement(
                                    "DELETE FROM " + FILES_TABLE + " WHERE torrent_rowid = ?")) {
                                del.setLong(1, oldRowid);
                                del.executeUpdate();
                            }
                        }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(upsertSql)) {
                    ps.setString(1, t.infoHashHex());
                    ps.setString(2, t.name());
                    ps.setLong(3, t.sizeBytes());
                    ps.setInt(4, t.fileCount());
                    ps.setString(5, t.filesJson());
                    ps.setString(6, t.tags());
                    ps.setString(7, Hex.encode(t.publisherNodeId()));
                    ps.setBytes(8, t.publisherEd25519Pub());
                    ps.setInt(9, t.publisherUtpPort());
                    ps.setLong(10, t.addedAt());
                    ps.setLong(11, t.lastSeenAt());
                    if (t.lastPublishedAt() != null) {
                        ps.setLong(12, t.lastPublishedAt());
                    } else {
                        ps.setNull(12, Types.INTEGER);
                    }
                    ps.executeUpdate();
                }
                syncSharedFiles(t.infoHashHex(), t.filesJson());
            } catch (SQLException e) {
                throw new IllegalStateException("upsert failed for " + t.infoHashHex(), e);
            }
        }
    }

    /**
     * Replace all rows in {@code shared_files} and {@code shared_files_fts}
     * for the torrent that was just inserted/updated.
     *
     * <p>FTS index is managed manually (standalone FTS5 table, no triggers)
     * to avoid issues with external content table "delete" commands when
     * rowids are reused.
     */
    private void syncSharedFiles(String infoHashHex, String filesJson) throws SQLException {
        Long torrentRowid = lookupTorrentRowid(infoHashHex);
        if (torrentRowid == null) {
            return;
        }
        // Delete old shared_files rows and FTS entries for this torrent.
        try (PreparedStatement delFts = connection.prepareStatement(
                "DELETE FROM " + FILES_FTS + " WHERE torrent_rowid = ?")) {
            delFts.setLong(1, torrentRowid);
            delFts.executeUpdate();
        }
        try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM " + FILES_TABLE + " WHERE torrent_rowid = ?")) {
            del.setLong(1, torrentRowid);
            del.executeUpdate();
        }
        // Insert new shared_files rows and FTS entries.
        List<FileEntry> entries = parseFilesJson(filesJson);
        if (entries.isEmpty()) {
            return;
        }
        try (PreparedStatement insFile = connection.prepareStatement(
                "INSERT INTO " + FILES_TABLE + " (torrent_rowid, file_path, file_size) VALUES (?,?,?)")) {
            try (PreparedStatement insFts = connection.prepareStatement(
                    "INSERT INTO " + FILES_FTS + " (file_path, torrent_rowid) VALUES (?,?)")) {
                for (FileEntry e : entries) {
                    insFile.setLong(1, torrentRowid);
                    insFile.setString(2, e.path);
                    insFile.setLong(3, e.size);
                    insFile.addBatch();

                    insFts.setString(1, e.path);
                    insFts.setLong(2, torrentRowid);
                    insFts.addBatch();
                }
                insFile.executeBatch();
                insFts.executeBatch();
            }
        }
    }

    /**
     * Resolve the {@code rowid} of the torrent with the given info_hash.
     * Called after {@code INSERT OR REPLACE} so the row is guaranteed to
     * exist. Uses an explicit lookup instead of {@code last_insert_rowid()}
     * because FTS5 trigger INSERTs can interfere with the latter.
     */
    private Long lookupTorrentRowid(String infoHashHex) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT rowid FROM " + TABLE + " WHERE info_hash = ?")) {
            ps.setString(1, infoHashHex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    private static List<FileEntry> parseFilesJson(String json) {
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return java.util.Collections.emptyList();
        }
        List<FileEntry> out = new ArrayList<>();
        try {
            var arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            for (var el : arr) {
                if (out.size() >= MAX_FILES_PER_TORRENT) {
                    break;
                }
                var obj = el.getAsJsonObject();
                var pathEl = obj.get("path");
                if (pathEl == null || pathEl.isJsonNull()) {
                    continue;
                }
                String path = pathEl.getAsString();
                if (path == null || path.isEmpty()) {
                    continue;
                }
                long size = 0;
                var sizeEl = obj.get("size");
                if (sizeEl != null && !sizeEl.isJsonNull()) {
                    size = sizeEl.getAsLong();
                }
                out.add(new FileEntry(path, size));
            }
        } catch (Throwable t) {
            LOG.warn("parseFilesJson failed: " + json, t);
        }
        return out;
    }

    private static final class FileEntry {
        final String path;
        final long size;
        FileEntry(String path, long size) {
            this.path = path;
            this.size = size;
        }
    }

    public void delete(String infoHashHex) {
        ensureOpen();
        synchronized (connection) {
            try {
                // Look up rowid before deleting the torrent so we can
                // manually cascade to shared_files (no FK on rowid).
                long torrentRowid = -1;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT rowid FROM " + TABLE + " WHERE info_hash = ?")) {
                    ps.setString(1, normalizeHex(infoHashHex));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            torrentRowid = rs.getLong(1);
                        }
                    }
                }
                if (torrentRowid != -1) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM " + FILES_FTS + " WHERE torrent_rowid = ?")) {
                        ps.setLong(1, torrentRowid);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM " + FILES_TABLE + " WHERE torrent_rowid = ?")) {
                        ps.setLong(1, torrentRowid);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM " + TABLE + " WHERE info_hash = ?")) {
                    ps.setString(1, normalizeHex(infoHashHex));
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("delete failed for " + infoHashHex, e);
            }
        }
    }

    public Optional<LocalSharedTorrent> get(String infoHashHex) {
        ensureOpen();
        String sql = "SELECT info_hash, name, size_bytes, file_count, files_json, tags, " +
                "publisher_node_id, publisher_ed25519_pub, publisher_utp_port, " +
                "added_at, last_seen_at, last_published_at " +
                "FROM " + TABLE + " WHERE info_hash = ?";
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, normalizeHex(infoHashHex));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(readRow(rs));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("get failed for " + infoHashHex, e);
            }
        }
        return Optional.empty();
    }

    public List<LocalSharedTorrent> search(String query, int limit) {
        ensureOpen();
        String ftsQuery = sanitizeFtsQuery(query);
        if (ftsQuery.isEmpty()) {
            return new ArrayList<>();
        }
        int cap = Math.max(1, limit);
        List<LocalSharedTorrent> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        synchronized (connection) {
            // Phase 1: torrent-name FTS matches (matchedFile = null).
            String torrentSql =
                    "SELECT s.info_hash, s.name, s.size_bytes, s.file_count, s.files_json, s.tags, " +
                            "s.publisher_node_id, s.publisher_ed25519_pub, s.publisher_utp_port, " +
                            "s.added_at, s.last_seen_at, s.last_published_at " +
                            "FROM " + TABLE + " s " +
                            "JOIN " + FTS + " ON " + FTS + ".rowid = s.rowid " +
                            "WHERE " + FTS + " MATCH ? " +
                            "ORDER BY bm25(" + FTS + ") " +
                            "LIMIT ?";
            try (PreparedStatement ps = connection.prepareStatement(torrentSql)) {
                ps.setString(1, ftsQuery);
                ps.setInt(2, cap);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalSharedTorrent t = readRow(rs);
                        if (seen.add(t.infoHashHex())) {
                            out.add(t);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("search (torrent-name) failed for query: " + ftsQuery, e);
            }
            // Phase 2: file-path FTS matches (matchedFile = matched file path).
            if (out.size() < cap) {
                String fileSql =
                        "SELECT s.info_hash, s.name, s.size_bytes, s.file_count, s.files_json, s.tags, " +
                                "s.publisher_node_id, s.publisher_ed25519_pub, s.publisher_utp_port, " +
                                "s.added_at, s.last_seen_at, s.last_published_at, " +
                                "ffts.file_path AS matched_file " +
                                "FROM " + TABLE + " s " +
                                "JOIN " + FILES_FTS + " ffts ON ffts.torrent_rowid = s.rowid " +
                                "WHERE " + FILES_FTS + " MATCH ? " +
                                "ORDER BY bm25(" + FILES_FTS + ") " +
                                "LIMIT ?";
                int remaining = cap - out.size();
                try (PreparedStatement ps = connection.prepareStatement(fileSql)) {
                    ps.setString(1, ftsQuery);
                    ps.setInt(2, remaining * 2);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next() && out.size() < cap) {
                            LocalSharedTorrent t = readRow(rs);
                            if (seen.add(t.infoHashHex())) {
                                String mf = rs.getString("matched_file");
                                out.add(t.toBuilder().matchedFile(mf).build());
                            }
                        }
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("search (file-path) failed for query: " + ftsQuery, e);
                }
            }
        }
        return out;
    }

    public void markPublished(String infoHashHex, long timestamp) {
        ensureOpen();
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + TABLE + " SET last_published_at = ? WHERE info_hash = ?")) {
                ps.setLong(1, timestamp);
                ps.setString(2, normalizeHex(infoHashHex));
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.warn("markPublished failed for " + infoHashHex, e);
            }
        }
    }

    public List<String> needsRepublish(long nowSec, long thresholdSec) {
        ensureOpen();
        String sql = "SELECT info_hash FROM " + TABLE +
                " WHERE last_published_at IS NULL OR last_published_at < ?";
        long cutoff = nowSec - thresholdSec;
        List<String> out = new ArrayList<>();
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, cutoff);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            } catch (SQLException e) {
                LOG.warn("needsRepublish failed", e);
            }
        }
        return out;
    }

    public void updateLastSeen(String infoHashHex, long ts) {
        ensureOpen();
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + TABLE + " SET last_seen_at = ? WHERE info_hash = ?")) {
                ps.setLong(1, ts);
                ps.setString(2, normalizeHex(infoHashHex));
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.warn("updateLastSeen failed for " + infoHashHex, e);
            }
        }
    }

    public int size() {
        ensureOpen();
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM " + TABLE);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                LOG.warn("size() failed", e);
            }
        }
        return 0;
    }

    @Override
    public List<LocalSharedTorrent> listAll() {
        ensureOpen();
        String sql = "SELECT info_hash, name, size_bytes, file_count, files_json, tags, " +
                "publisher_node_id, publisher_ed25519_pub, publisher_utp_port, " +
                "added_at, last_seen_at, last_published_at " +
                "FROM " + TABLE + " ORDER BY last_seen_at DESC";
        List<LocalSharedTorrent> out = new ArrayList<>();
        synchronized (connection) {
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readRow(rs));
                }
            } catch (SQLException e) {
                LOG.warn("listAll failed", e);
            }
        }
        return out;
    }

    public long sizeInBytes() {
        ensureOpen();
        long total = 0;
        if (path.exists()) total += path.length();
        File wal = new File(path.getAbsolutePath() + "-wal");
        if (wal.exists()) total += wal.length();
        File shm = new File(path.getAbsolutePath() + "-shm");
        if (shm.exists()) total += shm.length();
        return total;
    }

    private LocalSharedTorrent readRow(ResultSet rs) throws SQLException {
        LocalSharedTorrent.Builder b = new LocalSharedTorrent.Builder();
        b.infoHash(Hex.decode(rs.getString("info_hash")))
                .name(rs.getString("name"))
                .sizeBytes(rs.getLong("size_bytes"))
                .fileCount(rs.getInt("file_count"))
                .filesJson(rs.getString("files_json"))
                .tags(rs.getString("tags"))
                .publisherNodeId(Hex.decode(rs.getString("publisher_node_id")))
                .publisherEd25519Pub(rs.getBytes("publisher_ed25519_pub"))
                .publisherUtpPort(rs.getInt("publisher_utp_port"))
                .addedAt(rs.getLong("added_at"))
                .lastSeenAt(rs.getLong("last_seen_at"));
        long lp = rs.getLong("last_published_at");
        if (!rs.wasNull()) {
            b.lastPublishedAt(lp);
        }
        return b.build();
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
            s.execute(CREATE_META_SQL);
            s.execute(CREATE_TABLE_SQL);
            s.execute(CREATE_FTS_SQL);
            s.execute(CREATE_TRIGGERS_SQL);
            s.execute(CREATE_FILES_TABLE_SQL);
            s.execute(CREATE_FILES_FTS_SQL);
            s.execute(CREATE_INDEX_FILES_TORRENT_SQL);
            s.execute(CREATE_INDEX_ADDED_SQL);
            s.execute(CREATE_INDEX_NAME_SQL);
            s.execute(CREATE_INDEX_LAST_PUBLISHED_SQL);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO schema_meta(key, value) VALUES('version', ?)")) {
                ps.setString(1, String.valueOf(SCHEMA_VERSION));
                ps.executeUpdate();
            }
        }
    }

    private void ensureOpen() {
        if (!open.get()) {
            throw new IllegalStateException("LocalIndexTable is closed");
        }
    }

    private static String normalizeHex(String hex) {
        return hex == null ? "" : hex.toLowerCase();
    }

    /**
     * Sanitize a free-form user query into a safe FTS5 MATCH expression.
     *
     * <p>Rules:
     * <ul>
     *   <li>Lowercase.</li>
     *   <li>Keep only [a-z0-9]; replace everything else (including
     *       underscore, dot, dash, slash) with a space so token
     *       boundaries match the FTS5 unicode61 tokenizer.</li>
     *   <li>Split on whitespace, discard empty tokens.</li>
     *   <li>Wrap each remaining token in double quotes (phrase token) so
     *       FTS5 reserved words like {@code OR}, {@code AND}, {@code NOT},
     *       {@code NEAR} become literal text instead of operators.</li>
     *   <li>Join with single space — FTS5 implicit AND.</li>
     *   <li>Return empty string if no usable tokens remain (caller
     *       treats as "no results", not as an error).</li>
     * </ul>
     *
     * <p><b>Safety note:</b> Wrapping tokens in double quotes is safe
     * because the preceding sanitization step strips all non-[a-z0-9]
     * characters — there is no way for a {@code "} or {@code *} to
     * survive into the quoted token. If the sanitization logic changes,
     * this invariant must be preserved.
     */
    private static String sanitizeFtsQuery(String raw) {
        if (raw == null) return "";
        String lowered = raw.toLowerCase();
        StringBuilder clean = new StringBuilder(lowered.length());
        for (int i = 0; i < lowered.length(); i++) {
            char ch = lowered.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                clean.append(ch);
            } else {
                // Replace any non-alphanumeric (including underscore, dot,
                // dash, slash) with a space so it acts as a token boundary
                // — matching how FTS5's unicode61 tokenizer splits on the
                // same characters.
                clean.append(' ');
            }
        }
        String[] tokens = clean.toString().split("\\s+");
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            if (!first) out.append(' ');
            out.append('"').append(tok).append('"');
            first = false;
        }
        return out.toString();
    }
}
