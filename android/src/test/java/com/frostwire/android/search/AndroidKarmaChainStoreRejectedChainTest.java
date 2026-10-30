/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.frostwire.search.relay.BitcoinBlockReference;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.KarmaChain;
import com.frostwire.search.relay.KarmaChainEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A persisted karma chain that fails verification starts a fresh chain, and the fresh chain's
 * entries reuse its sequence numbers. They must not overwrite the old rows: the old chain is kept
 * aside, so a verifier bug or a new rule never destroys a user's karma for good.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AndroidKarmaChainStoreRejectedChainTest {

    private static final String DB_NAME = "test-karma-chain-rejected.db";

    private static KeyPair keyPair;
    private static byte[] pubRaw;

    private Context context;
    private AndroidKarmaChainStore store;

    @Before
    public void setUp() throws Exception {
        if (keyPair == null) {
            keyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            pubRaw = IdentityRecord.extractRawEd25519(keyPair.getPublic());
        }
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        store = new AndroidKarmaChainStore(context, DB_NAME);
    }

    @After
    public void tearDown() {
        if (store != null && store.isOpen()) {
            store.close();
        }
        context.deleteDatabase(DB_NAME);
    }

    @Test
    public void aChainThatFailsVerificationIsKeptAside() {
        store.append(orphanEndorsement());

        assertTrue(store.loadChain(pubRaw).entries().isEmpty());

        List<String> rejected = rejectedTables();
        assertEquals("the rejected chain is kept in its own table", 1, rejected.size());
        assertEquals(1, count(rejected.get(0)));
        assertEquals(0, count(AndroidKarmaChainStore.CHAIN_TABLE));
    }

    @Test
    public void theFreshChainDoesNotOverwriteTheRejectedOne() {
        store.append(orphanEndorsement());
        KarmaChain fresh = store.loadChain(pubRaw);

        store.append(fresh.commitEpoch(block(850000L), keyPair.getPrivate()));

        assertEquals(1, count(rejectedTables().get(0)));
        assertEquals(1, count(AndroidKarmaChainStore.CHAIN_TABLE));
        assertEquals(1, store.loadChain(pubRaw).entries().size());
    }

    @Test
    public void aValidChainIsLeftInPlace() {
        KarmaChain chain = new KarmaChain(pubRaw);
        store.append(chain.commitEpoch(block(850000L), keyPair.getPrivate()));

        assertEquals(1, store.loadChain(pubRaw).entries().size());

        assertTrue(rejectedTables().isEmpty());
    }

    private static KarmaChainEntry orphanEndorsement() {
        // An endorsement with no epoch commitment before it fails verification.
        byte[] peer = new byte[32];
        peer[0] = 9;
        byte[] infoHash = new byte[20];
        infoHash[0] = 9;
        return KarmaChainEntry.createEndorsement(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw, block(850050L),
                peer, infoHash, 1, keyPair.getPrivate());
    }

    private List<String> rejectedTables() {
        List<String> names = new ArrayList<>();
        try (SQLiteDatabase db = openRaw();
             Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'"
                     + " AND name LIKE 'karma_chain_rejected_%'", null)) {
            while (c.moveToNext()) {
                names.add(c.getString(0));
            }
        }
        return names;
    }

    private long count(String table) {
        try (SQLiteDatabase db = openRaw();
             Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    private SQLiteDatabase openRaw() {
        return SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).getAbsolutePath(),
                null, SQLiteDatabase.OPEN_READONLY);
    }

    private static BitcoinBlockReference block(long height) {
        byte[] hash = new byte[32];
        for (int i = 0; i < 32; i++) hash[i] = (byte) ((height + i) & 0xff);
        return new BitcoinBlockReference(height, hash);
    }
}
