/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KarmaChainTest {

    private static KeyPair keyPair;
    private static byte[] pubRaw;

    @BeforeAll
    static void setUp() throws Exception {
        keyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        pubRaw = IdentityRecord.extractRawEd25519(keyPair.getPublic());
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
    void emptyChainHasNoHeadAndZeroNextSeq() {
        KarmaChain chain = new KarmaChain(pubRaw);
        assertNull(chain.head());
        assertEquals(0, chain.nextSeq());
        assertTrue(chain.entries().isEmpty());
        assertEquals(0, chain.availableEnergy());
        assertEquals(-1, chain.currentEpoch());
    }

    @Test
    void constructorRejectsNullOrWrongSizeOwnerPub() {
        assertThrows(IllegalArgumentException.class, () -> new KarmaChain(null));
        assertThrows(IllegalArgumentException.class, () -> new KarmaChain(new byte[31]));
    }

    @Test
    void commitEpochAppendsEpochCommitment() {
        KarmaChain chain = new KarmaChain(pubRaw);
        KarmaChainEntry ec = chain.commitEpoch(block(850000L), keyPair.getPrivate());
        assertNotNull(ec);
        assertEquals(KarmaChainEntry.Kind.EPOCH_COMMITMENT, ec.kind());
        assertEquals(0, ec.seq());
        assertEquals(850000L / KarmaConstants.BLOCKS_PER_EPOCH, chain.currentEpoch());
        assertEquals(5, chain.availableEnergy(), "Fresh epoch grants 5 energy");
    }

    @Test
    void endorseAppendsEndorsement() {
        KarmaChain chain = new KarmaChain(pubRaw);
        chain.commitEpoch(block(850000L), keyPair.getPrivate());
        int before = chain.availableEnergy();
        KarmaChainEntry en = chain.endorse(dummyPeerPub(1), dummyInfoHash(1),
                block(850100L), keyPair.getPrivate());
        assertNotNull(en);
        assertEquals(KarmaChainEntry.Kind.ENDORSEMENT, en.kind());
        assertEquals(1, en.seq());
        assertEquals(before - 1, chain.availableEnergy(), "Endorsement costs 1 energy");
    }

    @Test
    void endorseThrowsIfNoEpochCommitted() {
        KarmaChain chain = new KarmaChain(pubRaw);
        assertThrows(IllegalStateException.class, () ->
                chain.endorse(dummyPeerPub(1), dummyInfoHash(1), block(850000L),
                        keyPair.getPrivate()));
    }

    @Test
    void endorseThrowsIfEnergyExhausted() {
        KarmaChain chain = new KarmaChain(pubRaw);
        chain.commitEpoch(block(850000L), keyPair.getPrivate());
        // Budget is 5 endorsements
        for (int i = 0; i < 5; i++) {
            chain.endorse(dummyPeerPub(i), dummyInfoHash(i), block(850100L),
                    keyPair.getPrivate());
        }
        assertEquals(0, chain.availableEnergy());
        assertThrows(IllegalStateException.class, () ->
                chain.endorse(dummyPeerPub(99), dummyInfoHash(99), block(850100L),
                        keyPair.getPrivate()));
    }

    @Test
    void commitEpochRejectsSameOrPastEpoch() {
        KarmaChain chain = new KarmaChain(pubRaw);
        chain.commitEpoch(block(850000L), keyPair.getPrivate());
        // 850000 is in epoch 5902, so a block in the same epoch must be rejected
        // 850001 is also in epoch 5902 (850001/144 = 5902)
        assertThrows(IllegalStateException.class, () ->
                chain.commitEpoch(block(850001L), keyPair.getPrivate()));
        // Past epoch
        assertThrows(IllegalStateException.class, () ->
                chain.commitEpoch(block(849999L), keyPair.getPrivate()));
    }

    @Test
    void energyDecaysAcrossEpochs() {
        KarmaChain chain = new KarmaChain(pubRaw);
        // Epoch 0: commit, use 3 of 5
        chain.commitEpoch(block(850000L), keyPair.getPrivate());
        for (int i = 0; i < 3; i++) {
            chain.endorse(dummyPeerPub(i), dummyInfoHash(i), block(850050L),
                    keyPair.getPrivate());
        }
        assertEquals(2, chain.availableEnergy());

        // Epoch 1: energy = 5 + 2 * 0.5 = 6.0
        chain.commitEpoch(block(850144L), keyPair.getPrivate());
        assertEquals(6, chain.availableEnergy());
    }

    @Test
    void verifyAcceptsValidChain() {
        KarmaChain chain = new KarmaChain(pubRaw);
        chain.commitEpoch(block(850000L), keyPair.getPrivate());
        for (int i = 0; i < 3; i++) {
            chain.endorse(dummyPeerPub(i), dummyInfoHash(i), block(850050L),
                    keyPair.getPrivate());
        }
        assertTrue(KarmaChain.verify(chain.entries()));
    }

    @Test
    void verifyRejectsEmptyChain() {
        assertFalse(KarmaChain.verify(null));
        assertFalse(KarmaChain.verify(new ArrayList<>()));
    }

    @Test
    void verifyRejectsNonGenesisFirstEntry() {
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                new byte[32], 0, pubRaw, block(850050L),
                dummyPeerPub(1), dummyInfoHash(1), 1, keyPair.getPrivate());
        List<KarmaChainEntry> chain = List.of(en);
        assertFalse(KarmaChain.verify(chain));
    }

    @Test
    void verifyRejectsBrokenHashLink() {
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        byte[] brokenPrev = new byte[32];
        brokenPrev[0] = 0x42;
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                brokenPrev, 1, pubRaw, block(850050L),
                dummyPeerPub(1), dummyInfoHash(1), 1, keyPair.getPrivate());
        assertFalse(KarmaChain.verify(List.of(ec, en)));
    }

    @Test
    void verifyRejectsWrongSequenceNumber() {
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 5, pubRaw, block(850050L),
                dummyPeerPub(1), dummyInfoHash(1), 1, keyPair.getPrivate());
        assertFalse(KarmaChain.verify(List.of(ec, en)));
    }

    @Test
    void verifyRejectsOutOfOrderBlockHeights() {
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block(849999L),
                dummyPeerPub(1), dummyInfoHash(1), 1, keyPair.getPrivate());
        assertFalse(KarmaChain.verify(List.of(ec, en)));
    }

    @Test
    void verifyRejectsOverBudgetEndorsements() {
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 2.0, keyPair.getPrivate());
        KarmaChainEntry en1 = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block(850050L),
                dummyPeerPub(1), dummyInfoHash(1), 1, keyPair.getPrivate());
        KarmaChainEntry en2 = KarmaChainEntry.createEndorsement(
                en1.entryHash(), 2, pubRaw, block(850050L),
                dummyPeerPub(2), dummyInfoHash(2), 1, keyPair.getPrivate());
        KarmaChainEntry en3 = KarmaChainEntry.createEndorsement(
                en2.entryHash(), 3, pubRaw, block(850050L),
                dummyPeerPub(3), dummyInfoHash(3), 1, keyPair.getPrivate());
        assertFalse(KarmaChain.verify(List.of(ec, en1, en2, en3)));
    }

    @Test
    void verifyRejectsEndorsementBeforeEpochCommitment() {
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw, block(850050L),
                dummyPeerPub(1), dummyInfoHash(1), 1, keyPair.getPrivate());
        assertFalse(KarmaChain.verify(List.of(en)));
    }

    @Test
    void verifyRejectsOutOfEpochEndorsementBlock() {
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        // Block 849000 is in an earlier epoch than the committed epoch
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block(849000L),
                dummyPeerPub(1), dummyInfoHash(1), 1, keyPair.getPrivate());
        assertFalse(KarmaChain.verify(List.of(ec, en)));
    }

    @Test
    void verifyRejectsMixedEndorserPubs() throws Exception {
        KeyPair otherKey = java.security.KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair();
        byte[] otherPub = IdentityRecord.extractRawEd25519(otherKey.getPublic());
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, otherPub, block(850050L),
                dummyPeerPub(1), dummyInfoHash(1), 1, otherKey.getPrivate());
        assertFalse(KarmaChain.verify(List.of(ec, en)));
    }

    @Test
    void verifyRejectsBadSignature() {
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw,
                block(850000L), 5.0, keyPair.getPrivate());
        byte[] badSig = ec.signature().clone();
        badSig[0] ^= 1;
        KarmaChainEntry tampered = KarmaChainEntry.fromStoredFields(
                KarmaChainEntry.Kind.EPOCH_COMMITMENT,
                ec.prevHash(), ec.seq(), ec.endorserPub(), ec.timestamp(),
                ec.blockHeight(), ec.blockHash(), ec.epoch(), ec.energy(),
                null, null, null, badSig);
        assertFalse(KarmaChain.verify(List.of(tampered)));
    }

    @Test
    void loadReconstructsStateFromPersistedEntries() {
        KarmaChain chain = new KarmaChain(pubRaw);
        chain.commitEpoch(block(850000L), keyPair.getPrivate());
        chain.endorse(dummyPeerPub(1), dummyInfoHash(1), block(850050L),
                keyPair.getPrivate());
        chain.endorse(dummyPeerPub(2), dummyInfoHash(2), block(850050L),
                keyPair.getPrivate());

        KarmaChain loaded = KarmaChain.load(pubRaw, chain.entries());

        assertNotNull(loaded);
        assertEquals(3, loaded.availableEnergy());
        assertEquals(3, loaded.entries().size());
    }
}
