/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.search;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Android implementation of {@link LocalIndex} backed by
 * {@link SQLiteDatabase} with FTS5 full-text search.
 *
 * <p>Mirrors the schema of the desktop {@code LocalIndexTable}:
 * {@code shared_torrents} + {@code shared_torrents_fts} (external-content
 * FTS5 with triggers) + {@code shared_files} + {@code shared_files_fts}
 * (standalone FTS5, manually managed).
 *
 * <p>The database file lives in app-private storage via
 * {@link Context#getDatabasePath(String)}.
 */
public final class AndroidLocalIndex implements LocalIndex, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AndroidLocalIndex.class);

    public static final String DEFAULT_DB_NAME = "frostwire-shared-torrents.db";
    static final int SCHEMA_VERSION = 2;

    static final String TABLE = "shared_torrents";
    static final String FTS = "shared_torrents_fts";
    static final String FILES_TABLE = "shared_files";
    static final String FILES_FTS = "shared_files_fts";

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
                    "last_published_at INTEGER)";

    private static final String CREATE_FTS_SQL =
            "CREATE VIRTUAL TABLE IF NOT EXISTS " + FTS + " USING fts5(" +
                    "name, tags, " +
                    "content='" + TABLE + "', content_rowid='rowid', " +
                    "tokenize='porter unicode61')";

    private static final String CREATE_FILES_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + FILES_TABLE + " (" +
                    "torrent_rowid INTEGER NOT NULL, " +
                    "file_path TEXT NOT NULL, " +
                    "file_size INTEGER NOT NULL DEFAULT 0)";

    private static final String CREATE_INDEX_FILES_TORRENT_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + FILES_TABLE + "_torrent " +
                    "ON " + FILES_TABLE + " (torrent_rowid)";

    private static final String CREATE_FILES_FTS_SQL =
            "CREATE VIRTUAL TABLE IF NOT EXISTS " + FILES_FTS + " USING fts5(" +
                    "file_path, torrent_rowid UNINDEXED, " +
                    "tokenize='porter unicode61')";

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

    private static final String CREATE_META_SQL =
            "CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)";

    private static final String CREATE_INDEX_ADDED_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + TABLE + "_added ON " + TABLE + " (added_at)";

    private static final String CREATE_INDEX_NAME_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + TABLE + "_name ON " + TABLE + " (name)";

    private static final String CREATE_INDEX_LAST_PUBLISHED_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + TABLE + "_last_published_at ON " + TABLE + " (last_published_at)";

    private static final String[] SELECT_COLUMNS = {
            "info_hash", "name", "size_bytes", "file_count", "files_json", "tags",
            "publisher_node_id", "publisher_ed25519_pub", "publisher_utp_port",
            "added_at", "last_seen_at", "last_published_at"
    };

    private final DbHelper helper;
    private final SQLiteDatabase db;
    private volatile boolean open = false;
    private final boolean fts5Available;

    private AndroidLocalIndex(Context context, String dbName) {
        this.helper = new DbHelper(context.getApplicationContext(), dbName);
        this.db = helper.getWritableDatabase();
        this.db.enableWriteAheadLogging();
        this.open = true;
        this.fts5Available = checkFts5Available();
    }

    public static AndroidLocalIndex open(Context context) {
        return new AndroidLocalIndex(context, DEFAULT_DB_NAME);
    }

    public static AndroidLocalIndex open(Context context, String dbName) {
        return new AndroidLocalIndex(context, dbName);
    }

    @Override
    public void upsert(LocalSharedTorrent t) {
        ensureOpen();
        synchronized (db) {
            db.beginTransaction();
            try {
                Long oldRowid = lookupRowid(t.infoHashHex());
                if (oldRowid != null) {
                    db.delete(FILES_TABLE, "torrent_rowid = ?", new String[]{String.valueOf(oldRowid)});
                }
                ContentValues cv = new ContentValues(12);
                cv.put("info_hash", t.infoHashHex());
                cv.put("name", t.name());
                cv.put("size_bytes", t.sizeBytes());
                cv.put("file_count", t.fileCount());
                cv.put("files_json", t.filesJson());
                cv.put("tags", t.tags());
                cv.put("publisher_node_id", Hex.encode(t.publisherNodeId()));
                cv.put("publisher_ed25519_pub", t.publisherEd25519Pub());
                cv.put("publisher_utp_port", t.publisherUtpPort());
                cv.put("added_at", t.addedAt());
                cv.put("last_seen_at", t.lastSeenAt());
                if (t.lastPublishedAt() != null) {
                    cv.put("last_published_at", t.lastPublishedAt());
                } else {
                    cv.putNull("last_published_at");
                }
                db.replace(TABLE, null, cv);
                syncSharedFiles(t.infoHashHex(), t.filesJson());
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    @Override
    public void delete(String infoHashHex) {
        ensureOpen();
        String hex = normalizeHex(infoHashHex);
        synchronized (db) {
            db.beginTransaction();
            try {
                Long rowid = lookupRowid(hex);
                if (rowid != null) {
                    if (fts5Available) {
                        db.delete(FILES_FTS, "torrent_rowid = ?", new String[]{String.valueOf(rowid)});
                    }
                    db.delete(FILES_TABLE, "torrent_rowid = ?", new String[]{String.valueOf(rowid)});
                }
                db.delete(TABLE, "info_hash = ?", new String[]{hex});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    @Override
    public Optional<LocalSharedTorrent> get(String infoHashHex) {
        ensureOpen();
        String hex = normalizeHex(infoHashHex);
        synchronized (db) {
            try (Cursor c = db.query(TABLE, SELECT_COLUMNS, "info_hash = ?",
                    new String[]{hex}, null, null, null, "1")) {
                if (c.moveToFirst()) {
                    return Optional.of(readRow(c));
                }
            } catch (Throwable e) {
                LOG.warn("get failed for " + hex, e);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<LocalSharedTorrent> search(String query, int limit) {
        ensureOpen();
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (query.length() > 256) {
            query = query.substring(0, 256);
        }
        int cap = Math.max(1, limit);
        if (fts5Available) {
            return searchFts5(query, cap);
        }
        return searchLike(query, cap);
    }

    private List<LocalSharedTorrent> searchFts5(String query, int cap) {
        String ftsQuery = sanitizeFtsQuery(query);
        if (ftsQuery.isEmpty()) {
            return new ArrayList<>();
        }
        List<LocalSharedTorrent> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        synchronized (db) {
            String torrentSql =
                    "SELECT s.info_hash, s.name, s.size_bytes, s.file_count, s.files_json, s.tags, " +
                            "s.publisher_node_id, s.publisher_ed25519_pub, s.publisher_utp_port, " +
                            "s.added_at, s.last_seen_at, s.last_published_at " +
                            "FROM " + TABLE + " s " +
                            "JOIN " + FTS + " ON " + FTS + ".rowid = s.rowid " +
                            "WHERE " + FTS + " MATCH ? " +
                            "ORDER BY bm25(" + FTS + ") " +
                            "LIMIT ?";
            try (Cursor c = db.rawQuery(torrentSql, new String[]{ftsQuery, String.valueOf(cap)})) {
                while (c.moveToNext()) {
                    LocalSharedTorrent t = readRow(c);
                    if (seen.add(t.infoHashHex())) {
                        out.add(t);
                    }
                }
            } catch (Throwable e) {
                LOG.warn("search (torrent-name) failed for query: " + ftsQuery, e);
            }
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
                int remaining = (cap - out.size()) * 2;
                try (Cursor c = db.rawQuery(fileSql, new String[]{ftsQuery, String.valueOf(remaining)})) {
                    while (c.moveToNext() && out.size() < cap) {
                        LocalSharedTorrent t = readRow(c);
                        if (seen.add(t.infoHashHex())) {
                            String mf = c.getString(c.getColumnIndexOrThrow("matched_file"));
                            out.add(t.toBuilder().matchedFile(mf).build());
                        }
                    }
                } catch (Throwable e) {
                    LOG.warn("search (file-path) failed for query: " + ftsQuery, e);
                }
            }
        }
        return out;
    }

    private List<LocalSharedTorrent> searchLike(String query, int cap) {
        String sanitized = sanitizeLikeQuery(query);
        if (sanitized.isEmpty()) {
            return new ArrayList<>();
        }
        String pattern = "%" + sanitized + "%";
        List<LocalSharedTorrent> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        synchronized (db) {
            String torrentSql =
                    "SELECT info_hash, name, size_bytes, file_count, files_json, tags, " +
                            "publisher_node_id, publisher_ed25519_pub, publisher_utp_port, " +
                            "added_at, last_seen_at, last_published_at " +
                            "FROM " + TABLE + " WHERE LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(tags) LIKE ? ESCAPE '\\' " +
                            "ORDER BY last_seen_at DESC LIMIT ?";
            try (Cursor c = db.rawQuery(torrentSql, new String[]{pattern, pattern, String.valueOf(cap)})) {
                while (c.moveToNext()) {
                    LocalSharedTorrent t = readRow(c);
                    if (seen.add(t.infoHashHex())) {
                        out.add(t);
                    }
                }
            } catch (Throwable e) {
                LOG.warn("search (LIKE torrent-name) failed for query: " + sanitized, e);
            }
            if (out.size() < cap) {
                String fileSql =
                        "SELECT s.info_hash, s.name, s.size_bytes, s.file_count, s.files_json, s.tags, " +
                                "s.publisher_node_id, s.publisher_ed25519_pub, s.publisher_utp_port, " +
                                "s.added_at, s.last_seen_at, s.last_published_at, " +
                                "sf.file_path AS matched_file " +
                                "FROM " + TABLE + " s " +
                                "JOIN " + FILES_TABLE + " sf ON sf.torrent_rowid = s.rowid " +
                                "WHERE LOWER(sf.file_path) LIKE ? ESCAPE '\\' " +
                                "LIMIT ?";
                int remaining = cap - out.size();
                try (Cursor c = db.rawQuery(fileSql, new String[]{pattern, String.valueOf(remaining)})) {
                    while (c.moveToNext() && out.size() < cap) {
                        LocalSharedTorrent t = readRow(c);
                        if (seen.add(t.infoHashHex())) {
                            String mf = c.getString(c.getColumnIndexOrThrow("matched_file"));
                            out.add(t.toBuilder().matchedFile(mf).build());
                        }
                    }
                } catch (Throwable e) {
                    LOG.warn("search (LIKE file-path) failed for query: " + sanitized, e);
                }
            }
        }
        return out;
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {
        ensureOpen();
        ContentValues cv = new ContentValues(1);
        cv.put("last_published_at", timestamp);
        synchronized (db) {
            db.update(TABLE, cv, "info_hash = ?", new String[]{normalizeHex(infoHashHex)});
        }
    }

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
        ensureOpen();
        long cutoff = nowSec - thresholdSec;
        List<String> out = new ArrayList<>();
        synchronized (db) {
            try (Cursor c = db.query(TABLE, new String[]{"info_hash"},
                    "last_published_at IS NULL OR last_published_at < ?",
                    new String[]{String.valueOf(cutoff)}, null, null, null)) {
                while (c.moveToNext()) {
                    out.add(c.getString(0));
                }
            } catch (Throwable e) {
                LOG.warn("needsRepublish failed", e);
            }
        }
        return out;
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {
        ensureOpen();
        ContentValues cv = new ContentValues(1);
        cv.put("last_seen_at", ts);
        synchronized (db) {
            db.update(TABLE, cv, "info_hash = ?", new String[]{normalizeHex(infoHashHex)});
        }
    }

    @Override
    public int size() {
        ensureOpen();
        synchronized (db) {
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
                if (c.moveToFirst()) {
                    return c.getInt(0);
                }
            }
        }
        return 0;
    }

    @Override
    public List<LocalSharedTorrent> listAll() {
        ensureOpen();
        List<LocalSharedTorrent> out = new ArrayList<>();
        synchronized (db) {
            try (Cursor c = db.query(TABLE, SELECT_COLUMNS, null, null, null, null, "last_seen_at DESC")) {
                while (c.moveToNext()) {
                    out.add(readRow(c));
                }
            } catch (Throwable e) {
                LOG.warn("listAll failed", e);
            }
        }
        return out;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        if (!open) {
            return;
        }
        synchronized (db) {
            if (!open) {
                return;
            }
            open = false;
            try {
                helper.close();
            } catch (Throwable t) {
                LOG.warn("Error closing database", t);
            }
        }
    }

    private boolean checkFts5Available() {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{FTS})) {
            return c.moveToFirst();
        }
    }

    private Long lookupRowid(String infoHashHex) {
        try (Cursor c = db.rawQuery(
                "SELECT rowid FROM " + TABLE + " WHERE info_hash = ?",
                new String[]{normalizeHex(infoHashHex)})) {
            if (c.moveToFirst()) {
                return c.getLong(0);
            }
        }
        return null;
    }

    private void syncSharedFiles(String infoHashHex, String filesJson) {
        Long torrentRowid = lookupRowid(infoHashHex);
        if (torrentRowid == null) {
            return;
        }
        if (fts5Available) {
            db.delete(FILES_FTS, "torrent_rowid = ?", new String[]{String.valueOf(torrentRowid)});
        }
        db.delete(FILES_TABLE, "torrent_rowid = ?", new String[]{String.valueOf(torrentRowid)});
        List<FileEntry> entries = parseFilesJson(filesJson);
        if (entries.isEmpty()) {
            return;
        }
        for (FileEntry e : entries) {
            ContentValues fileCv = new ContentValues(3);
            fileCv.put("torrent_rowid", torrentRowid);
            fileCv.put("file_path", e.path);
            fileCv.put("file_size", e.size);
            db.insert(FILES_TABLE, null, fileCv);

            if (fts5Available) {
                ContentValues ftsCv = new ContentValues(2);
                ftsCv.put("file_path", e.path);
                ftsCv.put("torrent_rowid", torrentRowid);
                db.insert(FILES_FTS, null, ftsCv);
            }
        }
    }

    private LocalSharedTorrent readRow(Cursor c) {
        LocalSharedTorrent.Builder b = new LocalSharedTorrent.Builder();
        b.infoHash(Hex.decode(c.getString(c.getColumnIndexOrThrow("info_hash"))))
                .name(c.getString(c.getColumnIndexOrThrow("name")))
                .sizeBytes(c.getLong(c.getColumnIndexOrThrow("size_bytes")))
                .fileCount(c.getInt(c.getColumnIndexOrThrow("file_count")))
                .filesJson(c.getString(c.getColumnIndexOrThrow("files_json")))
                .tags(c.getString(c.getColumnIndexOrThrow("tags")))
                .publisherNodeId(Hex.decode(c.getString(c.getColumnIndexOrThrow("publisher_node_id"))))
                .publisherEd25519Pub(c.getBlob(c.getColumnIndexOrThrow("publisher_ed25519_pub")))
                .publisherUtpPort(c.getInt(c.getColumnIndexOrThrow("publisher_utp_port")))
                .addedAt(c.getLong(c.getColumnIndexOrThrow("added_at")))
                .lastSeenAt(c.getLong(c.getColumnIndexOrThrow("last_seen_at")));
        int lpIdx = c.getColumnIndex("last_published_at");
        if (lpIdx >= 0 && !c.isNull(lpIdx)) {
            b.lastPublishedAt(c.getLong(lpIdx));
        }
        return b.build();
    }

    private void ensureOpen() {
        if (!open) {
            throw new IllegalStateException("AndroidLocalIndex is closed");
        }
    }

    private static String normalizeHex(String hex) {
        return hex == null ? "" : hex.toLowerCase();
    }

    private static List<FileEntry> parseFilesJson(String json) {
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return Collections.emptyList();
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
            String preview = json.length() > 200 ? json.substring(0, 200) + "..." : json;
            LOG.warn("parseFilesJson failed: " + preview, t);
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

    private static String sanitizeFtsQuery(String raw) {
        if (raw == null) return "";
        String lowered = raw.toLowerCase();
        StringBuilder clean = new StringBuilder(lowered.length());
        for (int i = 0; i < lowered.length(); i++) {
            char ch = lowered.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                clean.append(ch);
            } else {
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

    /**
     * Sanitize a free-form query for use in a LIKE pattern.
     *
     * <p>Escapes LIKE wildcards ({@code %}, {@code _}) and the escape
     * character ({@code \}) so that a remote peer cannot inject
     * {@code %} to match the entire local index in a single query.
     * Also strips control characters and caps the length.
     *
     * <p>The corresponding LIKE clause must use {@code ESCAPE '\\'}.
     */
    private static String sanitizeLikeQuery(String raw) {
        if (raw == null) return "";
        String lowered = raw.toLowerCase();
        if (lowered.length() > 256) {
            lowered = lowered.substring(0, 256);
        }
        StringBuilder out = new StringBuilder(lowered.length());
        for (int i = 0; i < lowered.length(); i++) {
            char ch = lowered.charAt(i);
            if (ch == '%' || ch == '_' || ch == '\\') {
                out.append('\\').append(ch);
            } else if (ch >= ' ') {
                out.append(ch);
            }
        }
        return out.toString().trim();
    }

    private static final class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context context, String name) {
            super(context, name, null, SCHEMA_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_META_SQL);
            db.execSQL(CREATE_TABLE_SQL);
            db.execSQL(CREATE_FILES_TABLE_SQL);
            db.execSQL(CREATE_INDEX_FILES_TORRENT_SQL);
            db.execSQL(CREATE_INDEX_ADDED_SQL);
            db.execSQL(CREATE_INDEX_NAME_SQL);
            db.execSQL(CREATE_INDEX_LAST_PUBLISHED_SQL);
            try {
                db.execSQL(CREATE_FTS_SQL);
                db.execSQL(CREATE_TRIGGERS_SQL);
                db.execSQL(CREATE_FILES_FTS_SQL);
            } catch (Throwable t) {
                LOG.warn("FTS5 not available, falling back to LIKE-based search", t);
            }
            ContentValues cv = new ContentValues(2);
            cv.put("key", "version");
            cv.put("value", String.valueOf(SCHEMA_VERSION));
            db.insert("schema_meta", null, cv);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + FILES_FTS);
            db.execSQL("DROP TABLE IF EXISTS " + FILES_TABLE);
            db.execSQL("DROP TRIGGER IF EXISTS " + TABLE + "_ai");
            db.execSQL("DROP TRIGGER IF EXISTS " + TABLE + "_ad");
            db.execSQL("DROP TRIGGER IF EXISTS " + TABLE + "_au");
            db.execSQL("DROP TABLE IF EXISTS " + FTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            db.execSQL("DROP TABLE IF EXISTS schema_meta");
            onCreate(db);
        }
    }
}
