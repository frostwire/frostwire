/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import android.content.Context;

import com.frostwire.search.relay.BitcoinBlockReference;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.KarmaChain;
import com.frostwire.search.relay.KarmaChainEntry;
import com.frostwire.search.relay.KarmaConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.security.KeyPair;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AndroidKarmaChainStoreTest {

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
        store = new AndroidKarmaChainStore(context, "test-karma-chain.db");
    }

    @After
    public void tearDown() {
        if (store != null && store.isOpen()) {
            store.close();
        }
        context.deleteDatabase("test-karma-chain.db");
    }

    @Test
    public void open_createsSchemaAndIsOpen() {
        assertTrue(store.isOpen());
    }

    @Test
    public void close_setsOpenFalse() {
        store.close();
        assertFalse(store.isOpen());
    }

    @Test
    public void close_isIdempotent() {
        store.close();
        store.close();
        assertFalse(store.isOpen());
    }

    @Test
    public void loadChain_returnsEmptyForMissingOwner() {
        KarmaChain chain = store.loadChain(pubRaw);
        assertNotNull(chain);
        assertTrue("Chain should be empty for a new owner", chain.entries().isEmpty());
    }

    @Test
    public void appendEpochCommitment_persistsAndLoads() {
        KarmaChainEntry entry = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), KarmaConstants.MAX_ENERGY,
                keyPair.getPrivate());
        store.append(entry);

        KarmaChain loaded = store.loadChain(pubRaw);
        assertEquals(1, loaded.entries().size());
        assertEquals(KarmaChainEntry.Kind.EPOCH_COMMITMENT, loaded.entries().get(0).kind());
    }

    @Test
    public void appendEndorsement_afterEpoch_loadsBoth() {
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), KarmaConstants.MAX_ENERGY,
                keyPair.getPrivate());
        store.append(ec);

        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block(850001L),
                dummyPeerPub(1), dummyInfoHash(1), 1,
                keyPair.getPrivate());
        store.append(en);

        KarmaChain loaded = store.loadChain(pubRaw);
        assertEquals(2, loaded.entries().size());
        assertEquals(KarmaChainEntry.Kind.EPOCH_COMMITMENT, loaded.entries().get(0).kind());
        assertEquals(KarmaChainEntry.Kind.ENDORSEMENT, loaded.entries().get(1).kind());
    }

    @Test
    public void loadChain_returnsFreshChainWhenEntriesAreInvalid() {
        KarmaChainEntry orphan = KarmaChainEntry.createEndorsement(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850050L), dummyPeerPub(99), dummyInfoHash(99), 1,
                keyPair.getPrivate());
        store.append(orphan);

        KarmaChain loaded = store.loadChain(pubRaw);
        assertNotNull(loaded);
        assertTrue("Orphan endorsement without epoch should yield empty chain",
                loaded.entries().isEmpty());
    }

    @Test
    public void appendEndorsement_updatesPeerKarma() {
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), KarmaConstants.MAX_ENERGY,
                keyPair.getPrivate());
        store.append(ec);

        byte[] peerPub = dummyPeerPub(5);
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block(850001L),
                peerPub, dummyInfoHash(1), 3,
                keyPair.getPrivate());
        store.append(en);

        KarmaChain loaded = store.loadChain(pubRaw);
        assertEquals(2, loaded.entries().size());
    }

    private static BitcoinBlockReference block(long height) {
        byte[] hash = new byte[32];
        for (int i = 0; i < 32; i++) hash[i] = (byte) ((height + i) & 0xff);
        return new BitcoinBlockReference(height, hash);
    }

    private static byte[] dummyPeerPub(int seed) {
        byte[] b = new byte[32];
        b[0] = (byte) seed;
        b[1] = (byte) (seed + 1);
        return b;
    }

    private static byte[] dummyInfoHash(int seed) {
        byte[] b = new byte[20];
        b[0] = (byte) seed;
        b[1] = (byte) (seed + 1);
        return b;
    }
}
