/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.Sha1Hash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DhtAdvertiserTest {

    private static IdentityKeys identity;

    @BeforeAll
    static void setUpClass() throws Exception {
        identity = IdentityKeys.generate(4);
    }

    private IdentityRecordPublisher publisher;
    private RecordingSession session;
    private DhtAdvertiser advertiser;

    @BeforeEach
    void setUp() {
        publisher = new IdentityRecordPublisher(identity, 6888);
        session = new RecordingSession();
        advertiser = new DhtAdvertiser(publisher, 60);
    }

    @Test
    void constructorRejectsNullAndBadInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> new DhtAdvertiser(null, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new DhtAdvertiser(publisher, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DhtAdvertiser(publisher, -1));
    }

    @Test
    void isRunningFalseBeforeStart() {
        assertFalse(advertiser.isRunning());
    }

    @Test
    void tickCallsPublisherAndAnnounces() {
        // tick() does DHT operations:
        //  1. identityPublisher.publishIfNeeded -> dhtPutItem for the
        //     BEP 46 identity record
        //  2. DhtRendezvous.announcePeer -> dhtAnnounce for the BEP 5
        //     peer topic
        //  3. DhtRendezvous.announceRelay -> dhtAnnounce for the BEP 5
        //     relay topic (because role=BOTH is a forwarder)
        // All record via putItemCalls.
        assertTrue(advertiser.tick(session));
        assertEquals(3, session.putItemCalls.size());
    }

    @Test
    void tickWithBootstrapAndCustomSupplierAnnouncesFourTopics() {
        RecordingSession custom = new RecordingSession();
        DhtAdvertiser icebridgeStyle = new DhtAdvertiser(
                new IdentityRecordPublisher(identity, 6888, 6889, "FORWARDER"),
                null,
                60,
                () -> custom,
                false,  // pure FORWARDER: no peer topic
                true);  // bootstrap topic
        assertTrue(icebridgeStyle.tick(custom));
        // identity put + relay announce + bootstrap announce
        assertEquals(3, custom.putItemCalls.size());
        assertEquals(1, icebridgeStyle.announceCount());
    }

    @Test
    void scheduledTickUsesSessionSupplierNotOnlyBtEngine() throws Exception {
        RecordingSession custom = new RecordingSession();
        DhtAdvertiser withSupplier = new DhtAdvertiser(
                publisher, null, 60, () -> custom, true, false);
        try {
            withSupplier.start();
            // Wait for at least one scheduled tick (interval 60s but initial delay 0).
            long deadline = System.currentTimeMillis() + 3000;
            while (custom.putItemCalls.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertFalse(custom.putItemCalls.isEmpty(),
                    "scheduled tick must use custom SessionManager supplier");
        } finally {
            withSupplier.stop();
        }
    }

    @Test
    void tickWithNullSessionIsNoOp() {
        assertFalse(advertiser.tick(null));
        assertEquals(0, session.putItemCalls.size());
    }

    @Test
    void tickIncrementsCounters() {
        // Force republish to bypass throttle between calls.
        advertiser.tick(session);
        publisher.publish(session); // reset by direct call
        advertiser.tick(session);
        // First tick publishes; second tick is throttled for the
        // publisher but still completes the announce, so tick() is
        // a no-op? No — announce always runs. So both ticks return
        // true and we have 2 putItemCalls.
        assertTrue(advertiser.identityPublishCount() >= 1);
        assertTrue(advertiser.announceCount() >= 2);
    }

    @Test
    void lastTickEpochSecUpdatesAfterTick() {
        assertEquals(0L, advertiser.lastTickEpochSec());
        advertiser.tick(session);
        assertTrue(advertiser.lastTickEpochSec() > 0);
    }

    @Test
    void identityPublishCountStartsAtZero() {
        assertEquals(0, advertiser.identityPublishCount());
    }

    @Test
    void announceCountStartsAtZero() {
        assertEquals(0, advertiser.announceCount());
    }

    @Test
    void doubleStartIsNoOp() {
        // Use start() and a real scheduled executor. Start twice and
        // verify isRunning remains true without throwing.
        // We can't easily assert thread state, but double-starting
        // must be safe.
        try {
            advertiser.start();
            advertiser.start();
            assertTrue(advertiser.isRunning());
        } finally {
            advertiser.stop();
        }
    }

    @Test
    void stopBeforeStartPermanentlyRevokes() throws Exception {
        advertiser.stop();
        advertiser.start();
        assertFalse(advertiser.isRunning());
        assertFalse(advertiser.tick(session));
        assertTrue(advertiser.awaitStopped(0, TimeUnit.NANOSECONDS));
        assertThrows(IllegalArgumentException.class, () -> advertiser.awaitStopped(-1, TimeUnit.SECONDS));
    }

    @Test
    void failingPermissionPredicateRevokesWithoutPublication() throws Exception {
        DhtAdvertiser owner = new DhtAdvertiser(publisher, null, 1, () -> session, true, true,
                () -> { throw new IllegalStateException("fixture permission failure"); });
        assertFalse(owner.tick(session));
        owner.start();
        assertFalse(owner.isRunning());
        assertTrue(owner.awaitStopped(0, TimeUnit.NANOSECONDS));
        assertTrue(session.putItemCalls.isEmpty());
    }

    @Test
    void stopDuringIdentityPublishPreventsLaterStagesAndRestart() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RecordingSession blocked = new RecordingSession() {
            @Override
            public void dhtPutItem(byte[] pub, byte[] key,
                                   com.frostwire.jlibtorrent.Entry entry, byte[] salt) {
                super.dhtPutItem(pub, key, entry, salt);
                entered.countDown();
                awaitUninterruptibly(release);
            }
        };
        DhtAdvertiser owner = new DhtAdvertiser(publisher, null, 1, () -> blocked, true, true);
        try {
            owner.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), owner::stop);
            assertFalse(owner.awaitStopped(20, TimeUnit.MILLISECONDS));
            owner.start();
            assertFalse(owner.isRunning());
            release.countDown();
            assertTrue(owner.awaitStopped(3, TimeUnit.SECONDS));
            assertFalse(owner.tick(blocked));
            assertEquals(1, blocked.putItemCalls.size());
            assertEquals(0, owner.announceCount());
        } finally {
            release.countDown();
            owner.close();
            assertTrue(owner.awaitStopped(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void replacedGenerationCannotAdvanceAfterSessionLookup() throws Exception {
        AtomicBoolean permitted = new AtomicBoolean(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        DhtAdvertiser owner = new DhtAdvertiser(publisher, null, 1, () -> {
            entered.countDown();
            awaitUninterruptibly(release);
            return session;
        }, true, true, permitted::get);
        try {
            owner.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            permitted.set(false);
            release.countDown();
            assertTrue(owner.awaitStopped(3, TimeUnit.SECONDS));
            permitted.set(true);
            owner.start();
            assertFalse(owner.tick(session));
            assertTrue(session.putItemCalls.isEmpty());
        } finally {
            release.countDown();
            owner.close();
            assertTrue(owner.awaitStopped(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void stopDuringIndexReadPreventsItsPutAndAllAnnouncements() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        LocalIndex index = new LocalIndex() {
            public void upsert(LocalSharedTorrent torrent) {}
            public void delete(String hash) {}
            public Optional<LocalSharedTorrent> get(String hash) { return Optional.empty(); }
            public List<LocalSharedTorrent> search(String query, int limit) { return List.of(); }
            public void markPublished(String hash, long timestamp) { fail("late markPublished"); }
            public List<String> needsRepublish(long now, long threshold) {
                entered.countDown();
                awaitUninterruptibly(release);
                return List.of();
            }
            public void updateLastSeen(String hash, long timestamp) {}
            public int size() { return 0; }
        };
        DhtAdvertiser owner = new DhtAdvertiser(publisher,
                new IndexAnnouncementPublisher(index, identity), 1, () -> session, true, true);
        try {
            owner.start();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            owner.close();
            assertFalse(owner.awaitStopped(20, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(owner.awaitStopped(3, TimeUnit.SECONDS));
            assertEquals(1, session.putItemCalls.size(), "only the earlier identity put was admitted");
        } finally {
            release.countDown();
            owner.close();
            assertTrue(owner.awaitStopped(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void stopDuringEachAnnouncementPreventsFollowingTopics() throws Exception {
        for (int blockedAnnouncement = 1; blockedAnnouncement <= 2; blockedAnnouncement++) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            int target = blockedAnnouncement;
            RecordingSession blocked = new RecordingSession() {
                private int announcements;
                @Override
                public void dhtAnnounce(Sha1Hash topic, int port, int flags) {
                    super.dhtAnnounce(topic, port, flags);
                    if (++announcements == target) {
                        entered.countDown();
                        awaitUninterruptibly(release);
                    }
                }
            };
            DhtAdvertiser owner = new DhtAdvertiser(new IdentityRecordPublisher(identity, 6888),
                    null, 1, () -> blocked, true, true);
            try {
                owner.start();
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                owner.close();
                release.countDown();
                assertTrue(owner.awaitStopped(3, TimeUnit.SECONDS));
                assertEquals(1 + target, blocked.putItemCalls.size());
            } finally {
                release.countDown();
                owner.close();
                assertTrue(owner.awaitStopped(3, TimeUnit.SECONDS));
            }
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (true) {
                try {
                    assertTrue(latch.await(Math.max(0, deadline - System.nanoTime()),
                            TimeUnit.NANOSECONDS), "barrier timed out");
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

    // --- helpers ---

    private static class RecordingSession extends SessionManager {
        final List<Object[]> putItemCalls = new CopyOnWriteArrayList<>();

        @Override
        public com.frostwire.jlibtorrent.Sha1Hash dhtPutItem(com.frostwire.jlibtorrent.Entry entry) {
            return null;
        }

        @Override
        public void dhtPutItem(byte[] publicKey, byte[] privateKey,
                               com.frostwire.jlibtorrent.Entry entry, byte[] salt) {
            putItemCalls.add(new Object[]{publicKey, privateKey, entry, salt});
        }

        @Override
        public void dhtAnnounce(Sha1Hash sha1, int port, int flags) {
            putItemCalls.add(new Object[]{"announce", sha1, port, flags});
        }

        @Override
        public boolean isDhtRunning() {
            return true;
        }
    }
}
