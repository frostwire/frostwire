/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.TcpEndpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Regression: iterating a live DHT peer list with for-each used to throw
 * ConcurrentModificationException when jlibtorrent alert threads mutated it.
 */
class DhtPeerDiscoverySourceTest {

    @Test
    void collectEndpointsSurvivesConcurrentMutationOfSourceList() throws Exception {
        ArrayList<TcpEndpoint> live = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            live.add(new TcpEndpoint("1.2.3." + (i % 250 + 1), 6888 + (i % 10)));
        }

        AtomicBoolean mutatorFailed = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);
        Thread mutator = new Thread(() -> {
            started.countDown();
            try {
                for (int i = 0; i < 10_000; i++) {
                    live.add(new TcpEndpoint("9.9.9." + (i % 200 + 1), 7000));
                    if (live.size() > 200) {
                        live.remove(0);
                    }
                }
            } catch (Throwable t) {
                mutatorFailed.set(true);
            }
        }, "dht-list-mutator");
        mutator.setDaemon(true);
        mutator.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));

        // Must not throw ConcurrentModificationException (the production bug).
        List<DiscoveredEndpoint> collected = assertDoesNotThrow(
                () -> DhtPeerDiscoverySource.collectEndpointsForTest(live));

        mutator.join(5_000);
        assertFalse(mutatorFailed.get());
        // May be empty or non-empty depending on races; just ensure no crash.
        assertTrue(collected != null);
    }

    @Test
    void collectEndpointsHandlesNullAndEmpty() {
        assertTrue(DhtPeerDiscoverySource.collectEndpointsForTest(null).isEmpty());
        assertTrue(DhtPeerDiscoverySource.collectEndpointsForTest(new ArrayList<>()).isEmpty());
    }
}
