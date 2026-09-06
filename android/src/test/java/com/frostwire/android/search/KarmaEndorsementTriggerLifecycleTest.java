/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import android.app.Application;

import com.frostwire.search.relay.BitcoinBlockReference;
import com.frostwire.search.relay.BlockHeaderSource;
import com.frostwire.search.relay.KarmaChain;
import com.frostwire.search.relay.KarmaChainEntry;
import com.frostwire.search.relay.KarmaChainStore;
import com.frostwire.search.relay.KarmaChainWriter;
import com.frostwire.search.relay.KarmaEndorsementTrigger;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Writer/store lifecycle tests that need no native handles.
 *
 * <p>downloadUpdate() paths that require BTDownload instances are covered by
 * desktop/common tests where the jlibtorrent native library loads; the Android
 * unit JVM cannot initialize BTDownload's static AlertType table.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class KarmaEndorsementTriggerLifecycleTest {
    private static com.frostwire.search.relay.IdentityKeys testIdentity() throws Exception {
        java.security.KeyPair kp =
                java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] enc = kp.getPrivate().getEncoded();
        byte[] rawSeed = java.util.Arrays.copyOfRange(enc, enc.length - 32, enc.length);
        byte[] pubEnc = kp.getPublic().getEncoded();
        byte[] rawPub =
                java.util.Arrays.copyOfRange(pubEnc, pubEnc.length - 32, pubEnc.length);
        return TestIdentityKeys.fromSeedWithoutNative(rawSeed, rawPub);
    }

    private static final String HASH = "0100000000000000000000000000000000000000";

    @Test
    public void closeDuringBlockedCommitPreventsAppendAndCannotRestart() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger appends = new AtomicInteger();
        KarmaChainStore store = new KarmaChainStore() {
            @Override
            public KarmaChain loadChain(byte[] owner) {
                return new KarmaChain(owner);
            }

            @Override
            public void append(KarmaChainEntry entry) {
                appends.incrementAndGet();
            }

            @Override
            public void close() {}
        };
        BlockHeaderSource source = new BlockHeaderSource() {
            @Override
            public long getChainTipHeight() {
                entered.countDown();
                awaitUninterruptibly(release);
                return 144;
            }

            @Override
            public BitcoinBlockReference getBlock(long height) {
                return new BitcoinBlockReference(height, new byte[32]);
            }
        };
        KarmaChainWriter writer =
                new KarmaChainWriter(testIdentity(), source, store);
        byte[] peer = new byte[32];
        peer[0] = 7;
        byte[] hash = new byte[20];
        Thread endorsement = new Thread(
                () -> writer.onDownloadCompletedFromPeer(peer, hash));
        try {
            endorsement.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            writer.close();
            assertFalse(writer.awaitStopped(20, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(writer.awaitStopped(3, TimeUnit.SECONDS));
            assertEquals(0, appends.get());
            endorsement.join(3000);
            assertFalse(endorsement.isAlive());
        } finally {
            release.countDown();
            writer.close();
            assertTrue(writer.awaitStopped(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void replacementGenerationDoesNotReviveQueuedWork() throws Exception {
        AtomicBoolean permitted = new AtomicBoolean(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger appends = new AtomicInteger();
        KarmaChainStore store = new KarmaChainStore() {
            @Override
            public KarmaChain loadChain(byte[] owner) {
                return new KarmaChain(owner);
            }

            @Override
            public void append(KarmaChainEntry entry) {
                appends.incrementAndGet();
            }

            @Override
            public void close() {}
        };
        BlockHeaderSource source = new BlockHeaderSource() {
            @Override
            public long getChainTipHeight() {
                entered.countDown();
                awaitUninterruptibly(release);
                return 144;
            }

            @Override
            public BitcoinBlockReference getBlock(long height) {
                return new BitcoinBlockReference(height, new byte[32]);
            }
        };
        KarmaChainWriter writer =
                new KarmaChainWriter(testIdentity(), source, store, permitted::get);
        byte[] peer = new byte[32];
        peer[0] = 7;
        byte[] hash = new byte[20];
        Thread endorsement = new Thread(
                () -> writer.onDownloadCompletedFromPeer(peer, hash));
        try {
            endorsement.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            permitted.set(false);
            release.countDown();
            assertTrue(writer.awaitStopped(3, TimeUnit.SECONDS));
            endorsement.join(3000);
            assertFalse(endorsement.isAlive());
            assertEquals(0, appends.get());
            // A new generation starts clean: no queued endorsement revives.
            permitted.set(true);
            assertEquals(0, appends.get());
        } finally {
            release.countDown();
            writer.close();
            assertTrue(writer.awaitStopped(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void triggerCloseIsTerminalWithoutDownloads() {
        LocalIndex index = mock(LocalIndex.class);
        KarmaEndorsementTrigger trigger =
                new KarmaEndorsementTrigger(index, new byte[32], (peer, hash) -> {});
        trigger.close();
        // Null updates are safe no-ops after close; the trigger cannot restart.
        trigger.downloadUpdate(null, null);
        trigger.started(null);
        trigger.downloadUpdate(null, null);
    }

    @Test
    public void triggerRejectsNullConstruction() {
        LocalIndex index = mock(LocalIndex.class);
        try {
            new KarmaEndorsementTrigger(null, new byte[32], (peer, hash) -> {});
            assertTrue("null index must be rejected", false);
        } catch (IllegalArgumentException expected) {
        }
        try {
            new KarmaEndorsementTrigger(index, null, (peer, hash) -> {});
            assertTrue("null pub must be rejected", false);
        } catch (IllegalArgumentException expected) {
        }
        try {
            new KarmaEndorsementTrigger(index, new byte[32], null);
            assertTrue("null sink must be rejected", false);
        } catch (IllegalArgumentException expected) {
        }
    }

    private static LocalSharedTorrent peerTorrent() {
        byte[] peer = new byte[32];
        peer[0] = 1;
        return new LocalSharedTorrent.Builder()
                .infoHash(new byte[20])
                .name("Fixture")
                .fileCount(1)
                .publisherNodeId(new byte[20])
                .publisherEd25519Pub(peer)
                .addedAt(1)
                .lastSeenAt(1)
                .build();
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (true) {
                try {
                    assertTrue(
                            "barrier timed out",
                            latch.await(
                                    Math.max(0, deadline - System.nanoTime()),
                                    TimeUnit.NANOSECONDS));
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
