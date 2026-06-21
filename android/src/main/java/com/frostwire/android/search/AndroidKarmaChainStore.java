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

import com.frostwire.search.relay.KarmaChain;
import com.frostwire.search.relay.KarmaChainEntry;
import com.frostwire.search.relay.KarmaChainStore;
import com.frostwire.search.relay.KarmaConstants;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Android SQLite implementation of {@link KarmaChainStore}.
 *
 * <p>Stores the local node's karma chain entries in a {@code karma_chain}
 * table. Uses the same schema as the desktop {@code KarmaChainTable} so
 * databases are compatible if copied between platforms.
 *
 * <p>The database file is shared with {@link AndroidLocalIndex} (WAL mode
 * allows multiple connections from the same process). This class opens
 * the existing database directly via {@link SQLiteDatabase#openDatabase}
 * instead of using a {@code SQLiteOpenHelper} — the helper in
 * {@link AndroidLocalIndex} owns schema versioning, and a second helper
 * with a different version number would trigger an unwanted
 * {@code onDowngrade} crash.
 */
public final class AndroidKarmaChainStore implements KarmaChainStore {

    private static final Logger LOG = Logger.getLogger(AndroidKarmaChainStore.class);

    static final String CHAIN_TABLE = "karma_chain";
    static final String PEER_TABLE = "peer_karma";

    private static final String CREATE_CHAIN_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + CHAIN_TABLE + " (" +
                    "seq INTEGER PRIMARY KEY, " +
                    "kind TEXT NOT NULL, " +
                    "prev_hash TEXT NOT NULL, " +
                    "endorser_pub TEXT NOT NULL, " +
                    "timestamp INTEGER NOT NULL, " +
                    "block_height INTEGER NOT NULL, " +
                    "block_hash TEXT NOT NULL, " +
                    "epoch INTEGER, " +
                    "energy REAL, " +
                    "peer_pub TEXT, " +
                    "info_hash TEXT, " +
                    "score_delta INTEGER, " +
                    "signature TEXT NOT NULL, " +
                    "canonical_bytes BLOB NOT NULL)";

    private static final String CREATE_PEER_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + PEER_TABLE + " (" +
                    "peer_pub TEXT PRIMARY KEY, " +
                    "total_score INTEGER NOT NULL DEFAULT 0, " +
                    "endorsement_count INTEGER NOT NULL DEFAULT 0, " +
                    "last_endorsed_at INTEGER NOT NULL DEFAULT 0)";

    private static final String CREATE_INDEX_EPOCH_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + CHAIN_TABLE + "_epoch ON " +
                    CHAIN_TABLE + " (epoch)";

    private static final String CREATE_INDEX_BLOCK_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + CHAIN_TABLE + "_block ON " +
                    CHAIN_TABLE + " (block_height)";

    private static final String CREATE_INDEX_SCORE_SQL =
            "CREATE INDEX IF NOT EXISTS idx_" + PEER_TABLE + "_score ON " +
                    PEER_TABLE + " (total_score)";

    private final SQLiteDatabase db;
    private volatile boolean open = true;

    /**
     * Open a karma chain store sharing the same database file as
     * {@link AndroidLocalIndex}.
     *
     * @param context Android context
     * @param dbName  the database file name (same as AndroidLocalIndex)
     */
    public AndroidKarmaChainStore(Context context, String dbName) {
        File dbFile = context.getDatabasePath(dbName);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        this.db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY);
        initializeSchema();
    }

    private void initializeSchema() {
        db.execSQL(CREATE_CHAIN_TABLE_SQL);
        db.execSQL(CREATE_PEER_TABLE_SQL);
        db.execSQL(CREATE_INDEX_EPOCH_SQL);
        db.execSQL(CREATE_INDEX_BLOCK_SQL);
        db.execSQL(CREATE_INDEX_SCORE_SQL);
    }

    @Override
    public void append(KarmaChainEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry is null");
        }
        ensureOpen();
        long epoch = entry.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT
                ? entry.epoch()
                : KarmaConstants.epochForHeight(entry.blockHeight());
        synchronized (db) {
            db.beginTransaction();
            try {
                ContentValues cv = new ContentValues(14);
                cv.put("seq", entry.seq());
                cv.put("kind", entry.kind().code());
                cv.put("prev_hash", Hex.encode(entry.prevHash()));
                cv.put("endorser_pub", Base64.getEncoder().withoutPadding()
                        .encodeToString(entry.endorserPub()));
                cv.put("timestamp", entry.timestamp());
                cv.put("block_height", entry.blockHeight());
                cv.put("block_hash", Hex.encode(entry.blockHash()));
                cv.put("epoch", epoch);
                if (entry.energy() != null) {
                    cv.put("energy", entry.energy());
                } else {
                    cv.putNull("energy");
                }
                if (entry.peerPub() != null) {
                    cv.put("peer_pub", Base64.getEncoder().withoutPadding()
                            .encodeToString(entry.peerPub()));
                } else {
                    cv.putNull("peer_pub");
                }
                if (entry.infoHash() != null) {
                    cv.put("info_hash", Hex.encode(entry.infoHash()));
                } else {
                    cv.putNull("info_hash");
                }
                if (entry.scoreDelta() != null) {
                    cv.put("score_delta", entry.scoreDelta());
                } else {
                    cv.putNull("score_delta");
                }
                cv.put("signature", Base64.getEncoder().withoutPadding()
                        .encodeToString(entry.signature()));
                cv.put("canonical_bytes", entry.canonicalBytes());
                db.replace(CHAIN_TABLE, null, cv);

                if (entry.kind() == KarmaChainEntry.Kind.ENDORSEMENT
                        && entry.peerPub() != null
                        && entry.scoreDelta() != null) {
                    updatePeerKarma(entry.peerPub(), entry.scoreDelta(), entry.timestamp());
                }
                db.setTransactionSuccessful();
            } catch (Throwable t) {
                LOG.warn("Failed to append karma entry seq=" + entry.seq(), t);
            } finally {
                db.endTransaction();
            }
        }
    }

    @Override
    public KarmaChain loadChain(byte[] ownerPub) {
        if (ownerPub == null || ownerPub.length != 32) {
            throw new IllegalArgumentException("ownerPub must be 32 bytes");
        }
        ensureOpen();
        List<KarmaChainEntry> loaded = new ArrayList<>();
        synchronized (db) {
            try (Cursor c = db.rawQuery(
                    "SELECT kind, prev_hash, endorser_pub, timestamp, " +
                            "block_height, block_hash, epoch, energy, " +
                            "peer_pub, info_hash, score_delta, signature " +
                            "FROM " + CHAIN_TABLE + " ORDER BY seq ASC", null)) {
                while (c.moveToNext()) {
                    try {
                        int idx = 0;
                        KarmaChainEntry.Kind kind = "EC".equals(c.getString(idx++))
                                ? KarmaChainEntry.Kind.EPOCH_COMMITMENT
                                : KarmaChainEntry.Kind.ENDORSEMENT;
                        byte[] prevHash = Hex.decode(c.getString(idx++));
                        byte[] endorserPub = Base64.getDecoder().decode(c.getString(idx++));
                        long timestamp = c.getLong(idx++);
                        long blockHeight = c.getLong(idx++);
                        byte[] blockHash = Hex.decode(c.getString(idx++));
                        long epoch = c.getLong(idx++);
                        boolean epochWasNull = c.isNull(idx - 1);
                        double energy = c.getDouble(idx++);
                        boolean energyWasNull = c.isNull(idx - 1);
                        String peerPubStr = c.getString(idx++);
                        byte[] peerPub = peerPubStr != null
                                ? Base64.getDecoder().decode(peerPubStr) : null;
                        String infoHashStr = c.getString(idx++);
                        byte[] infoHash = infoHashStr != null
                                ? Hex.decode(infoHashStr) : null;
                        int scoreDelta = c.getInt(idx++);
                        boolean scoreDeltaWasNull = c.isNull(idx - 1);
                        byte[] signature = Base64.getDecoder().decode(c.getString(idx++));

                        KarmaChainEntry entry = KarmaChainEntry.fromStoredFields(
                                kind, prevHash, loaded.size(), endorserPub, timestamp,
                                blockHeight, blockHash,
                                epochWasNull ? null : epoch,
                                energyWasNull ? null : energy,
                                peerPub, infoHash,
                                scoreDeltaWasNull ? null : scoreDelta,
                                signature);
                        loaded.add(entry);
                    } catch (Throwable t) {
                        LOG.warn("Skipping corrupt karma chain entry during load", t);
                    }
                }
            } catch (Throwable e) {
                LOG.warn("loadChain failed", e);
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
                db.close();
            } catch (Throwable t) {
                LOG.warn("Error closing karma chain database", t);
            }
        }
    }

    private void updatePeerKarma(byte[] peerPub, int scoreDelta, long timestamp) {
        String peerPubB64 = Base64.getEncoder().withoutPadding().encodeToString(peerPub);
        try (Cursor c = db.query(PEER_TABLE, new String[]{"total_score", "endorsement_count", "last_endorsed_at"},
                "peer_pub = ?", new String[]{peerPubB64}, null, null, null, "1")) {
            if (c.moveToFirst()) {
                long totalScore = c.getLong(0) + scoreDelta;
                int count = c.getInt(1) + 1;
                long lastAt = Math.max(c.getLong(2), timestamp);
                ContentValues cv = new ContentValues(3);
                cv.put("total_score", totalScore);
                cv.put("endorsement_count", count);
                cv.put("last_endorsed_at", lastAt);
                db.update(PEER_TABLE, cv, "peer_pub = ?", new String[]{peerPubB64});
            } else {
                ContentValues cv = new ContentValues(4);
                cv.put("peer_pub", peerPubB64);
                cv.put("total_score", scoreDelta);
                cv.put("endorsement_count", 1);
                cv.put("last_endorsed_at", timestamp);
                db.insert(PEER_TABLE, null, cv);
            }
        }
    }

    private void ensureOpen() {
        if (!open) {
            throw new IllegalStateException("AndroidKarmaChainStore is closed");
        }
    }
}
