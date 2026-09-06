/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class KarmaChainWriterTest {

    private static final int LOW_DIFFICULTY = 4;
    private static final byte[] FAKE_PEER_PUB = new byte[]{
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
    };
    private static final byte[] FAKE_INFO_HASH = new byte[20];

    @TempDir
    Path tempDir;

    private File dbFile;
    private KarmaChainTable table;
    private IdentityKeys identity;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tempDir.resolve("karma-test.db").toFile();
        table = KarmaChainTable.open(dbFile);
        identity = IdentityKeys.generate(LOW_DIFFICULTY);
    }

    @AfterEach
    void tearDown() {
        if (table != null) {
            table.close();
        }
    }

    @Test
    void constructorRejectsNulls() {
        FakeBlockSource source = new FakeBlockSource();
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainWriter(null, source, table));
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainWriter(identity, null, table));
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainWriter(identity, source, null));
    }

    @Test
    void firstEndorsementCommitsEpochAndAppendsEndorsement() {
        FakeBlockSource source = new FakeBlockSource()
                .withTip(144L) // epoch 1 boundary
                .withBlock(144L, hashForHeight(144L));
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        KarmaChain chain = writer.chain();
        assertEquals(2, chain.entries().size(), "should have commitment + endorsement");
        assertEquals(KarmaChainEntry.Kind.EPOCH_COMMITMENT, chain.entries().get(0).kind());
        assertEquals(KarmaChainEntry.Kind.ENDORSEMENT, chain.entries().get(1).kind());
        assertEquals(1L, chain.currentEpoch());
        assertEquals(4, chain.availableEnergy(), "started with 5, used 1, decayed carry");
    }

    @Test
    void secondEndorsementInSameEpochDoesNotCommitAgain() {
        FakeBlockSource source = new FakeBlockSource()
                .withTip(200L)
                .withBlock(200L, hashForHeight(200L));
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);
        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        assertEquals(3, writer.chain().entries().size(),
                "1 commitment + 2 endorsements");
    }

    @Test
    void endorsementAtNewEpochBoundaryCommitsNewEpoch() {
        FakeBlockSource source = new FakeBlockSource()
                .withTip(144L) // epoch 1
                .withBlock(144L, hashForHeight(144L));
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);
        assertEquals(1L, writer.chain().currentEpoch());

        source.withTip(288L) // epoch 2
                .withBlock(288L, hashForHeight(288L));
        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        assertEquals(2L, writer.chain().currentEpoch());
        assertEquals(4, writer.chain().entries().size(),
                "2 commitments + 2 endorsements");
    }

    @Test
    void commitEpochIfNeededCommitsAtStartup() {
        FakeBlockSource source = new FakeBlockSource()
                .withTip(144L)
                .withBlock(144L, hashForHeight(144L));
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.commitEpochIfNeeded();

        assertEquals(1, writer.chain().entries().size());
        assertEquals(KarmaChainEntry.Kind.EPOCH_COMMITMENT,
                writer.chain().entries().get(0).kind());
    }

    @Test
    void commitEpochIfNeededIsNoOpWhenUpToDate() {
        FakeBlockSource source = new FakeBlockSource()
                .withTip(144L)
                .withBlock(144L, hashForHeight(144L));
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.commitEpochIfNeeded();
        writer.commitEpochIfNeeded();
        writer.commitEpochIfNeeded();

        assertEquals(1, writer.chain().entries().size());
    }

    @Test
    void noTipFromSourceDoesNotThrowOrAppend() {
        FakeBlockSource source = new FakeBlockSource();
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);
        writer.commitEpochIfNeeded();

        assertEquals(0, writer.chain().entries().size());
    }

    @Test
    void nullArgsAreNoOp() {
        FakeBlockSource source = new FakeBlockSource()
                .withTip(144L)
                .withBlock(144L, hashForHeight(144L));
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.onDownloadCompletedFromPeer(null, FAKE_INFO_HASH);
        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, null);
        assertEquals(0, writer.chain().entries().size());
    }

    @Test
    void persistedEntriesMatchInMemory() {
        FakeBlockSource source = new FakeBlockSource()
                .withTip(144L)
                .withBlock(144L, hashForHeight(144L));
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        // Verify the entries are queryable through the table
        assertEquals(1, table.endorsementCountInEpoch(
                identity.ed25519PubRaw(), 1L));
    }

    @Test
    void peerAggregateScoreIncrementsOnEndorsement() {
        FakeBlockSource source = new FakeBlockSource()
                .withTip(144L)
                .withBlock(144L, hashForHeight(144L));
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);

        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        long score = table.getPeerKarma(FAKE_PEER_PUB);
        assertEquals(1L, score, "endorsement score delta is +1");
    }

    @Test
    void closeDuringBlockReadPreventsCommitmentAndEndorsement() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BlockHeaderSource source = new BlockHeaderSource() {
            @Override
            public long getChainTipHeight() { return 144; }

            @Override
            public BitcoinBlockReference getBlock(long height) {
                entered.countDown();
                awaitBarrier(release);
                return new BitcoinBlockReference(height, hashForHeight(height));
            }
        };
        KarmaChainWriter writer = new KarmaChainWriter(identity, source, table);
        Thread worker = new Thread(() -> writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH));
        try {
            worker.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), writer::close);
            assertFalse(writer.awaitStopped(20, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(writer.awaitStopped(3, TimeUnit.SECONDS));
            writer.commitEpochIfNeeded();
            writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);
            assertTrue(writer.chain().entries().isEmpty());
        } finally {
            release.countDown();
            writer.close();
            worker.join(3000);
            assertFalse(worker.isAlive());
        }
    }

    @Test
    void replacementDuringCommitmentPersistencePreventsLaterEndorsement() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean permitted = new AtomicBoolean(true);
        KarmaChainStore store = new KarmaChainStore() {
            @Override
            public KarmaChain loadChain(byte[] owner) { return table.loadChain(owner); }

            @Override
            public void append(KarmaChainEntry entry) {
                table.append(entry);
                entered.countDown();
                awaitBarrier(release);
            }

            @Override
            public void close() {}
        };
        KarmaChainWriter writer = new KarmaChainWriter(identity,
                new FakeBlockSource().withTip(144).withBlock(144, hashForHeight(144)), store, permitted::get);
        Thread worker = new Thread(() -> writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH));
        try {
            worker.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            permitted.set(false);
            release.countDown();
            assertTrue(writer.awaitStopped(3, TimeUnit.SECONDS));
            permitted.set(true);
            writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);
            assertEquals(1, writer.chain().entries().size());
            assertEquals(0, table.endorsementCountInEpoch(identity.ed25519PubRaw(), 1));
        } finally {
            release.countDown();
            writer.close();
            worker.join(3000);
            assertFalse(worker.isAlive());
        }
    }

    private static void awaitBarrier(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "barrier timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /**
     * Hand-rolled fake of {@link BlockHeaderSource}. Tracks tip and
     * individual block heights; returns deterministic 32-byte hashes.
     */
    private static final class FakeBlockSource implements BlockHeaderSource {
        private final AtomicLong tip = new AtomicLong(-1);
        private final java.util.Map<Long, byte[]> blocks = new java.util.HashMap<>();

        FakeBlockSource withTip(long height) {
            tip.set(height);
            return this;
        }

        FakeBlockSource withBlock(long height, byte[] hash) {
            blocks.put(height, hash);
            return this;
        }

        @Override
        public BitcoinBlockReference getBlock(long height) {
            byte[] hash = blocks.get(height);
            if (hash == null) {
                return null;
            }
            return new BitcoinBlockReference(height, hash);
        }

        @Override
        public long getChainTipHeight() {
            return tip.get();
        }
    }

    private static byte[] hashForHeight(long height) {
        byte[] hash = new byte[32];
        // Encode the height into the first 8 bytes; rest zero. Sufficient
        // for tests; we never verify block hash contents here.
        for (int i = 0; i < 8; i++) {
            hash[i] = (byte) (height >>> (8 * (7 - i)));
        }
        return hash;
    }
}
