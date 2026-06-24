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

import java.util.ArrayList;
import java.util.List;

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
    void stopWithoutStartIsNoOp() {
        advertiser.stop();
        assertFalse(advertiser.isRunning());
    }

    // --- helpers ---

    private static final class RecordingSession extends SessionManager {
        final List<Object[]> putItemCalls = new ArrayList<>();

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
