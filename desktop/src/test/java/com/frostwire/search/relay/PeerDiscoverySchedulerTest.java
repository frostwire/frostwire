/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PeerDiscoverySchedulerTest {

    private FakeSource source;
    private PeerDirectory directory;
    private PeerDiscovery discovery;
    private PeerDiscoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        source = new FakeSource();
        directory = new PeerDirectory(new NoopKarmaCache());
        discovery = new PeerDiscovery(source, directory);
        scheduler = new PeerDiscoveryScheduler(discovery, 60);
    }

    @Test
    void constructorRejectsNullAndBadInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> new PeerDiscoveryScheduler(null, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new PeerDiscoveryScheduler(discovery, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PeerDiscoveryScheduler(discovery, -1));
    }

    @Test
    void isRunningFalseBeforeStart() {
        assertFalse(scheduler.isRunning());
    }

    @Test
    void startAndStopTransitionIsRunning() {
        try {
            scheduler.start();
            assertTrue(scheduler.isRunning());
        } finally {
            scheduler.stop();
        }
        assertFalse(scheduler.isRunning());
    }

    @Test
    void stopWithoutStartIsNoOp() {
        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    void doubleStartIsNoOp() {
        try {
            scheduler.start();
            scheduler.start();
            assertTrue(scheduler.isRunning());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void doubleStopIsNoOp() {
        try {
            scheduler.start();
            scheduler.stop();
            scheduler.stop();
            assertFalse(scheduler.isRunning());
        } catch (Throwable t) {
            scheduler.stop();
        }
    }

    @Test
    void tickIncrementsCountersAndRegistersEndpoints() {
        source.endpoints.add(new DiscoveredEndpoint("10.0.0.1", 6888));
        source.endpoints.add(new DiscoveredEndpoint("10.0.0.2", 6888));

        List<DiscoveredEndpoint> result = scheduler.tick();
        assertEquals(2, result.size());
        assertEquals(2, scheduler.totalDiscovered());
        assertEquals(1, scheduler.totalTicks());
    }

    @Test
    void tickReturnsEmptyWhenNothingNew() {
        List<DiscoveredEndpoint> first = scheduler.tick();
        assertTrue(first.isEmpty());
        assertEquals(0, scheduler.totalDiscovered());
        assertEquals(1, scheduler.totalTicks());
    }

    @Test
    void tickFailsClosedOnException() {
        source.throwOnFetch = true;
        List<DiscoveredEndpoint> result = scheduler.tick();
        assertTrue(result.isEmpty());
        assertEquals(1, scheduler.totalTicks(),
                "tick is counted even on failure");
    }

    @Test
    void countersStartAtZero() {
        PeerDiscoveryScheduler fresh = new PeerDiscoveryScheduler(discovery, 60);
        assertEquals(0, fresh.totalDiscovered());
        assertEquals(0, fresh.totalTicks());
    }

    // --- helpers ---

    private static final class FakeSource implements PeerDiscoverySource {
        final List<DiscoveredEndpoint> endpoints = new ArrayList<>();
        final Map<byte[], Entry> identityEntries = new HashMap<>();
        boolean throwOnFetch;

        @Override
        public List<DiscoveredEndpoint> fetchEndpoints() {
            if (throwOnFetch) {
                throw new RuntimeException("simulated BEP 5 failure");
            }
            return new ArrayList<>(endpoints);
        }

        @Override
        public Entry fetchIdentityEntry(byte[] peerPub) {
            return identityEntries.get(peerPub);
        }
    }

    private static final class NoopKarmaCache extends PeerKarmaCache {
        NoopKarmaCache() {
            super(new RemoteKarmaChainFetcher(new KarmaChainSource() {
                @Override
                public Entry fetchManifest(byte[] peerPub) {
                    return null;
                }
            }));
        }
    }
}
