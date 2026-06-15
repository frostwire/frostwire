/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.peer;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PeerRegistryTest {

    @Test
    void registerRefreshesExistingPeer() {
        PeerRegistry registry = new RegistryBuilder().build();
        byte[] pub = new byte[32];
        pub[0] = 1;

        assertTrue(registry.register(new PeerRecord(pub, "1.2.3.4", 6888, IceBridgeConfig.Role.FORWARDER, 1000)));
        assertTrue(registry.register(new PeerRecord(pub, "5.6.7.8", 6889, IceBridgeConfig.Role.FORWARDER, 2000)));

        assertEquals(1, registry.size());
        PeerRecord current = registry.lookup(pub);
        assertNotNull(current);
        assertEquals("5.6.7.8", current.host());
        assertEquals(6889, current.rudpPort());
    }

    @Test
    void lookupForwardersOnlyReturnsForwarders() {
        PeerRegistry registry = new RegistryBuilder().build();
        byte[] forwarder = new byte[32];
        forwarder[0] = 1;
        byte[] client = new byte[32];
        client[0] = 2;

        registry.register(new PeerRecord(forwarder, "1.2.3.4", 6888, IceBridgeConfig.Role.FORWARDER, 1000));
        registry.register(new PeerRecord(client, "1.2.3.5", 6888, IceBridgeConfig.Role.CLIENT, 1000));

        List<PeerRecord> result = registry.lookupForwarders(10);
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).ed25519Pub()[0]);
    }

    @Test
    void evictStaleRemovesOldPeers() {
        PeerRegistry registry = new RegistryBuilder().peerTtlSec(1).build();
        byte[] pub = new byte[32];
        pub[0] = 1;

        registry.register(new PeerRecord(pub, "1.2.3.4", 6888, IceBridgeConfig.Role.FORWARDER,
                System.currentTimeMillis() - 2000));

        assertEquals(1, registry.evictStale(1000));
        assertEquals(0, registry.size());
    }

    @Test
    void rateLimitRejectsSpam() {
        IceBridgeConfig config = IceBridgeConfig.newBuilder()
                .maxPeers(10)
                .peerTtlSec(60)
                .maxQpsPerKey(1.0)
                .controlHttpPort(8800)
                .build();
        PeerRegistry registry = new PeerRegistry(config);
        byte[] pub = new byte[32];
        pub[0] = 1;
        PeerRecord record = new PeerRecord(pub, "1.2.3.4", 6888, IceBridgeConfig.Role.FORWARDER,
                System.currentTimeMillis());

        assertTrue(registry.register(record));
        assertFalse(registry.register(record));
    }

    private static final class RegistryBuilder {
        private int maxPeers = 100;
        private long peerTtlSec = 120;
        private double maxQpsPerKey = 10.0;

        RegistryBuilder maxPeers(int maxPeers) {
            this.maxPeers = maxPeers;
            return this;
        }

        RegistryBuilder peerTtlSec(long peerTtlSec) {
            this.peerTtlSec = peerTtlSec;
            return this;
        }

        PeerRegistry build() {
            IceBridgeConfig config = IceBridgeConfig.newBuilder()
                    .maxPeers(maxPeers)
                    .peerTtlSec(peerTtlSec)
                    .maxQpsPerKey(maxQpsPerKey)
                    .controlHttpPort(8800)
                    .build();
            return new PeerRegistry(config);
        }
    }
}