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
import com.frostwire.search.relay.DhtAdvertiser;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecordPublisher;
import com.frostwire.search.relay.KarmaChain;
import com.frostwire.search.relay.KarmaChainCommitScheduler;
import com.frostwire.search.relay.KarmaChainEntry;
import com.frostwire.search.relay.KarmaChainPublisher;
import com.frostwire.search.relay.KarmaChainStore;
import com.frostwire.search.relay.KarmaChainWriter;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidRelayStackLifecycleTest {

    private static IdentityKeys testIdentity() throws Exception {
        java.security.KeyPair kp =
                java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] enc = kp.getPrivate().getEncoded();
        byte[] rawSeed = java.util.Arrays.copyOfRange(enc, enc.length - 32, enc.length);
        byte[] pubEnc = kp.getPublic().getEncoded();
        byte[] rawPub =
                java.util.Arrays.copyOfRange(pubEnc, pubEnc.length - 32, pubEnc.length);
        return TestIdentityKeys.fromSeedWithoutNative(rawSeed, rawPub);
    }

    @Test
    public void blockedEndorsementDefersStoreCloseAndBoundsReplacementGenerations() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger appends = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AtomicBoolean inStore = new AtomicBoolean();
        AtomicBoolean permitted = new AtomicBoolean(true);
        KarmaChainStore store = new KarmaChainStore() {
            @Override public KarmaChain loadChain(byte[] owner) { return new KarmaChain(owner); }
            @Override public void append(KarmaChainEntry entry) {
                inStore.set(true);
                appends.incrementAndGet();
                inStore.set(false);
            }
            @Override public void close() {
                assertFalse("Borrowed store closed during append", inStore.get());
                closes.incrementAndGet();
                closed.countDown();
            }
        };
        BlockHeaderSource blocks = mock(BlockHeaderSource.class);
        when(blocks.getChainTipHeight()).thenAnswer(call -> {
            entered.countDown();
            awaitUninterruptibly(release);
            return 144L;
        });
        when(blocks.getBlock(144L)).thenReturn(new BitcoinBlockReference(144, new byte[32]));
        KarmaChainWriter writer = new KarmaChainWriter(testIdentity(), blocks, store, permitted::get);
        AndroidRelayStack.PublicationOwners owners = new AndroidRelayStack.PublicationOwners(null, null, null, writer, store);
        byte[] peer = new byte[32];
        peer[0] = 1;
        byte[] infoHash = new byte[20];
        Method retire = AndroidRelayStack.class.getDeclaredMethod("retirePublications", AndroidRelayStack.PublicationOwners.class);
        retire.setAccessible(true);
        Thread endorsement = new Thread(() -> writer.onDownloadCompletedFromPeer(peer, infoHash));
        try {
            endorsement.start();
            assertTrue("writer did not reach blocked append", entered.await(3, TimeUnit.SECONDS));
            owners.stop();
            assertFalse(owners.closeWhenDrained(20, TimeUnit.MILLISECONDS));
            assertEquals(0, closes.get());
            retire.invoke(null, owners);
            Field cleanup = AndroidRelayStack.class.getDeclaredField("CLEANUP");
            cleanup.setAccessible(true);
            ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) cleanup.get(null);
            for (int i = 0; i < 50; i++) {
                assertNull(AndroidRelayStack.start(null, null, null, () -> true));
                retire.invoke(null, owners);
            }
            assertTrue(executor.getPoolSize() <= 1);
            assertTrue(executor.getQueue().size() <= 1);
            assertEquals(0, closes.get());
            release.countDown();
            assertTrue(closed.await(3, TimeUnit.SECONDS));
            assertTrue(writer.awaitStopped(3, TimeUnit.SECONDS));
            assertTrue(owners.closeWhenDrained(0, TimeUnit.NANOSECONDS));
            assertEquals(1, closes.get());
            permitted.set(false);
            assertEquals("Revoked endorsement fails closed without appending", 0, appends.get());
            endorsement.join(3000);
            assertFalse(endorsement.isAlive());
        } finally {
            release.countDown();
            owners.stop();
            assertTrue(writer.awaitStopped(3, TimeUnit.SECONDS));
            assertTrue(owners.closeWhenDrained(3, TimeUnit.SECONDS));
            Method retry = AndroidRelayStack.class.getDeclaredMethod("retryRetirement");
            retry.setAccessible(true);
            retry.invoke(null);
        }
    }

    @Test
    public void advertiserDrainIsRequiredEvenWhenOtherOwnersAreAbsent() throws Exception {
        // No SessionManager instance is created: the session supplier blocks, then
        // resolves null, exercising supplier-drain without native handles.
        AtomicBoolean permitted = new AtomicBoolean(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IdentityRecordPublisher publisher = mock(IdentityRecordPublisher.class);
        DhtAdvertiser advertiser = new DhtAdvertiser(publisher, null, 1, () -> {
            entered.countDown();
            awaitUninterruptibly(release);
            return null;
        }, true, true, permitted::get);
        KarmaChainStore store = mock(KarmaChainStore.class);
        AndroidRelayStack.PublicationOwners owners = new AndroidRelayStack.PublicationOwners(advertiser, null, null, null, store);
        try {
            advertiser.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            permitted.set(false);
            owners.stop();
            assertFalse(owners.closeWhenDrained(20, TimeUnit.MILLISECONDS));
            verifyNoInteractions(store);
            verifyNoInteractions(publisher);
            release.countDown();
            assertTrue(owners.closeWhenDrained(3, TimeUnit.SECONDS));
            verify(store).close();
        } finally {
            release.countDown();
            owners.stop();
            assertTrue(owners.closeWhenDrained(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void schedulerResolvingSessionMustDrainBeforeStoreDisposal() throws Exception {
        AtomicBoolean permitted = new AtomicBoolean(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        KarmaChainStore store = mock(KarmaChainStore.class);
        when(store.loadChain(any())).thenAnswer(call -> new KarmaChain(call.getArgument(0)));
        BlockHeaderSource blocks = mock(BlockHeaderSource.class);
        when(blocks.getChainTipHeight()).thenReturn(-1L);
        KarmaChainWriter writer = new KarmaChainWriter(testIdentity(), blocks, store, permitted::get);
        KarmaChainPublisher publisher = mock(KarmaChainPublisher.class);
        KarmaChainCommitScheduler scheduler = new KarmaChainCommitScheduler(writer, publisher, 60, () -> {
            entered.countDown();
            awaitUninterruptibly(release);
            return null;
        }, permitted::get);
        AndroidRelayStack.PublicationOwners owners = new AndroidRelayStack.PublicationOwners(null, scheduler, null, writer, store);
        try {
            scheduler.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            permitted.set(false);
            owners.stop();
            assertFalse(owners.closeWhenDrained(20, TimeUnit.MILLISECONDS));
            verify(store, never()).close();
            release.countDown();
            assertTrue(owners.closeWhenDrained(3, TimeUnit.SECONDS));
            verify(store).close();
            verifyNoInteractions(publisher);
        } finally {
            release.countDown();
            owners.stop();
            assertTrue(owners.closeWhenDrained(3, TimeUnit.SECONDS));
        }
    }

    private static void awaitUninterruptibly(CountDownLatch release) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean interrupted = false;
        try {
            for (;;) {
                try {
                    assertTrue("Barrier timed out", release.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
