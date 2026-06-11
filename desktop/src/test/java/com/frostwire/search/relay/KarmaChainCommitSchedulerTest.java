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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class KarmaChainCommitSchedulerTest {

    @TempDir
    Path tempDir;

    private File dbFile;
    private KarmaChainTable table;
    private IdentityKeys identity;
    private FakeBlockSource blockSource;
    private KarmaChainWriter writer;
    private KarmaChainPublisher publisher;
    private KarmaChainCommitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tempDir.resolve("karma-scheduler-test.db").toFile();
        table = KarmaChainTable.open(dbFile);
        identity = IdentityKeys.generate(4);
        blockSource = new FakeBlockSource();
        writer = new KarmaChainWriter(identity, blockSource, table);
        publisher = new KarmaChainPublisher(writer, identity);
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.stop();
        }
        if (table != null) {
            table.close();
        }
    }

    @Test
    void constructorRejectsNullsAndBadInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainCommitScheduler(null, publisher, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainCommitScheduler(writer, null, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainCommitScheduler(writer, publisher, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainCommitScheduler(writer, publisher, -1));
    }

    @Test
    void isRunningFalseBeforeStart() {
        scheduler = new KarmaChainCommitScheduler(writer, publisher, 60);
        assertFalse(scheduler.isRunning());
    }

    @Test
    void startAndStopTransitionIsRunning() {
        scheduler = new KarmaChainCommitScheduler(writer, publisher, 60);
        scheduler.start();
        assertTrue(scheduler.isRunning());
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    void doubleStartIsNoOp() {
        scheduler = new KarmaChainCommitScheduler(writer, publisher, 60);
        scheduler.start();
        scheduler.start();
        assertTrue(scheduler.isRunning());
    }

    @Test
    void doubleStopIsNoOp() {
        scheduler = new KarmaChainCommitScheduler(writer, publisher, 60);
        scheduler.start();
        scheduler.stop();
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    void stopWithoutStartIsNoOp() {
        scheduler = new KarmaChainCommitScheduler(writer, publisher, 60);
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    void tickCommitsEpochWhenNewBitcoinEpochAvailable() throws Exception {
        blockSource.withTip(144L).withBlock(144L, hashForHeight(144L));
        scheduler = new KarmaChainCommitScheduler(writer, publisher, 1);
        scheduler.start();

        // Wait up to 4 seconds for the scheduled tick to fire
        long deadline = System.currentTimeMillis() + 4_000;
        while (System.currentTimeMillis() < deadline
                && writer.chain().currentEpoch() < 0) {
            Thread.sleep(50);
        }

        assertEquals(1L, writer.chain().currentEpoch(),
                "scheduler should have committed epoch 1 within 4s");
    }

    // --- helpers ---

    private static byte[] hashForHeight(long height) {
        byte[] hash = new byte[32];
        for (int i = 0; i < 8; i++) {
            hash[i] = (byte) (height >>> (8 * (7 - i)));
        }
        return hash;
    }

    private static final class FakeBlockSource implements BlockHeaderSource {
        private final AtomicLong tip = new AtomicLong(-1);
        private final Map<Long, byte[]> blocks = new HashMap<>();

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
}
