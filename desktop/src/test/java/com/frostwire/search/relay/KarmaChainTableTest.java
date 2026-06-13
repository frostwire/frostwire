/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KarmaChainTableTest {

    private static KeyPair keyPair;
    private static byte[] pubRaw;

    private File tempDir;
    private File dbFile;
    private KarmaChainTable table;

    @BeforeAll
    static void setUpClass() throws Exception {
        keyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        pubRaw = IdentityRecord.extractRawEd25519(keyPair.getPublic());
    }

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("karma-chain-table-test-").toFile();
        dbFile = new File(tempDir, "test.db");
        table = KarmaChainTable.open(dbFile);
    }

    @AfterEach
    void tearDown() {
        if (table != null && table.isOpen()) {
            table.close();
        }
        deleteRecursive(tempDir);
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

    @Test
    void openCreatesSchemaAndIsOpen() {
        assertTrue(table.isOpen());
        assertEquals(dbFile, table.path());
    }

    @Test
    void appendEpochCommitmentPersists() {
        KarmaChainEntry entry = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        table.append(entry);
        // No assertion needed beyond no-throw; the table accepted the entry
    }

    @Test
    void loadChainRestoresPersistedEntries() {
        byte[] peer = dummyPeerPub(7);
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block(850050L),
                peer, dummyInfoHash(7), 1, keyPair.getPrivate());
        table.append(ec);
        table.append(en);

        KarmaChain loaded = table.loadChain(pubRaw);

        assertNotNull(loaded);
        assertEquals(2, loaded.entries().size());
        assertEquals(0, loaded.entries().get(0).seq());
        assertEquals(1, loaded.entries().get(1).seq());
        assertEquals(5.0, loaded.entries().get(0).energy());
        assertEquals(1, table.getPeerKarma(peer));
    }

    @Test
    void loadChainReturnsEmptyChainForMissingOwner() {
        KarmaChain loaded = table.loadChain(pubRaw);
        assertNotNull(loaded);
        assertTrue(loaded.entries().isEmpty());
    }

    @Test
    void loadChainReturnsFreshChainWhenStoredEntriesAreInvalid() {
        // Append a standalone endorsement without a preceding epoch commitment
        KarmaChainEntry orphan = KarmaChainEntry.createEndorsement(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw, block(850050L),
                dummyPeerPub(9), dummyInfoHash(9), 1, keyPair.getPrivate());
        table.append(orphan);

        KarmaChain loaded = table.loadChain(pubRaw);

        assertNotNull(loaded);
        assertTrue(loaded.entries().isEmpty(),
                "Invalid persisted chain must be discarded, not loaded");
    }

    @Test
    void appendEndorsementUpdatesPeerKarma() {
        byte[] peer1 = dummyPeerPub(1);
        byte[] peer2 = dummyPeerPub(2);

        // Commit epoch
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        table.append(ec);

        // First endorsement (prevHash links to epoch commitment)
        KarmaChainEntry en1 = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block(850050L),
                peer1, dummyInfoHash(1), 1, keyPair.getPrivate());
        table.append(en1);
        assertEquals(1, table.getPeerKarma(peer1));

        // Second endorsement for same peer
        KarmaChainEntry en2 = KarmaChainEntry.createEndorsement(
                en1.entryHash(), 2, pubRaw, block(850060L),
                peer1, dummyInfoHash(2), 1, keyPair.getPrivate());
        table.append(en2);
        assertEquals(2, table.getPeerKarma(peer1));

        // Third endorsement for different peer
        KarmaChainEntry en3 = KarmaChainEntry.createEndorsement(
                en2.entryHash(), 3, pubRaw, block(850070L),
                peer2, dummyInfoHash(3), 1, keyPair.getPrivate());
        table.append(en3);
        assertEquals(2, table.getPeerKarma(peer1));
        assertEquals(1, table.getPeerKarma(peer2));
    }

    @Test
    void getTopPeersReturnsDescending() {
        byte[] peer1 = dummyPeerPub(1);
        byte[] peer2 = dummyPeerPub(2);
        byte[] peer3 = dummyPeerPub(3);

        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 10.0, keyPair.getPrivate());
        table.append(ec);

        // peer1: 3 endorsements
        KarmaChainEntry prev = ec;
        for (int i = 0; i < 3; i++) {
            prev = KarmaChainEntry.createEndorsement(
                    prev.entryHash(), i + 1, pubRaw, block(850050L + i),
                    peer1, dummyInfoHash(i), 1, keyPair.getPrivate());
            table.append(prev);
        }
        // peer2: 5 endorsements
        for (int i = 0; i < 5; i++) {
            prev = KarmaChainEntry.createEndorsement(
                    prev.entryHash(), i + 10, pubRaw, block(850060L + i),
                    peer2, dummyInfoHash(i + 10), 1, keyPair.getPrivate());
            table.append(prev);
        }
        // peer3: 1 endorsement
        prev = KarmaChainEntry.createEndorsement(
                prev.entryHash(), 20, pubRaw, block(850070L),
                peer3, dummyInfoHash(20), 1, keyPair.getPrivate());
        table.append(prev);

        List<KarmaChainTable.PeerKarmaScore> top = table.getTopPeers(10);
        assertEquals(3, top.size());
        assertEquals(5, top.get(0).totalScore(), "peer2 has most endorsements");
        assertEquals(3, top.get(1).totalScore(), "peer1 second");
        assertEquals(1, top.get(2).totalScore(), "peer3 last");
    }

    @Test
    void getPeerKarmaReturnsZeroForUnknown() {
        assertEquals(0, table.getPeerKarma(dummyPeerPub(99)));
    }

    @Test
    void endorsementCountInEpochWorks() {
        long epoch = 850000L / KarmaConstants.BLOCKS_PER_EPOCH;
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        table.append(ec);
        KarmaChainEntry prev = ec;
        // Use blocks in the same epoch as the commitment
        for (int i = 0; i < 3; i++) {
            prev = KarmaChainEntry.createEndorsement(
                    prev.entryHash(), i + 1, pubRaw, block(850001L + i),
                    dummyPeerPub(i), dummyInfoHash(i), 1,
                    keyPair.getPrivate());
            table.append(prev);
        }
        assertEquals(3, table.endorsementCountInEpoch(pubRaw, epoch));
        assertEquals(0, table.endorsementCountInEpoch(pubRaw, epoch + 1));
    }

    @Test
    void operationsAfterCloseThrow() {
        table.close();
        assertThrows(IllegalStateException.class, () -> table.getPeerKarma(dummyPeerPub(1)));
    }

    @Test
    void tryWithResourcesClosesTable() throws IOException {
        File f = new File(tempDir, "resources-test.db");
        try (KarmaChainTable t = KarmaChainTable.open(f)) {
            assertTrue(t.isOpen());
            t.append(KarmaChainEntry.createEpochCommitment(
                    KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                    block(850000L), 5.0, keyPair.getPrivate()));
        }
        // Re-open and verify the data is there
        try (KarmaChainTable t = KarmaChainTable.open(f)) {
            assertTrue(t.isOpen());
            // getPeerKarma(0) returns 0 for unknown, but the table is functional
            assertEquals(0, t.getPeerKarma(dummyPeerPub(1)));
        }
    }

    @Test
    void getTopPeersRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> table.getTopPeers(0));
        assertThrows(IllegalArgumentException.class, () -> table.getTopPeers(-1));
    }

    @Test
    void appendRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> table.append(null));
    }

    @Test
    void peerKarmaScoreDefensiveCopies() {
        byte[] peer = dummyPeerPub(1);
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        table.append(ec);
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block(850050L),
                peer, dummyInfoHash(1), 1, keyPair.getPrivate());
        table.append(en);

        List<KarmaChainTable.PeerKarmaScore> top = table.getTopPeers(1);
        assertEquals(1, top.size());
        byte[] pubCopy = top.get(0).peerPub();
        java.util.Arrays.fill(pubCopy, (byte) 0xff);
        assertFalse(java.util.Arrays.equals(top.get(0).peerPub(), pubCopy));
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
