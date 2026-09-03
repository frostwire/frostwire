/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.peer;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    @Test
    void capacityRejectsNewPeersButAllowsRefresh() {
        PeerRegistry registry = new RegistryBuilder().maxPeers(2).build();

        byte[] pubA = new byte[32];
        pubA[0] = 1;
        byte[] pubB = new byte[32];
        pubB[0] = 2;
        byte[] pubC = new byte[32];
        pubC[0] = 3;

        assertTrue(registry.register(new PeerRecord(pubA, "1.0.0.1", 6888,
                IceBridgeConfig.Role.FORWARDER, System.currentTimeMillis())));
        assertTrue(registry.register(new PeerRecord(pubB, "1.0.0.2", 6888,
                IceBridgeConfig.Role.FORWARDER, System.currentTimeMillis())));

        // At capacity — new peer rejected.
        assertFalse(registry.register(new PeerRecord(pubC, "1.0.0.3", 6888,
                IceBridgeConfig.Role.FORWARDER, System.currentTimeMillis())));
        assertEquals(2, registry.size());

        // Existing peer refresh is allowed even at capacity.
        assertTrue(registry.register(new PeerRecord(pubA, "1.0.0.10", 6889,
                IceBridgeConfig.Role.FORWARDER, System.currentTimeMillis())));
        assertEquals(2, registry.size());
        assertEquals("1.0.0.10", registry.lookup(pubA).host());
    }

    @Test
    void lookupForwardersIsSeedDeterministic() {
        PeerRegistry registry = registryWithForwarders("10.0.0", 1, 5);
        List<String> first = hexes(registry.lookupForwarders(3, new Random(7)));
        List<String> second = hexes(registry.lookupForwarders(3, new Random(7)));
        assertEquals(3, first.size());
        assertEquals(first, second);
    }

    @Test
    void lookupForwardersSpreadsAcrossRegistry() {
        PeerRegistry registry = registryWithForwarders("10.1", 0, 10);
        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 100; seed++) {
            seen.addAll(hexes(registry.lookupForwarders(4, new Random(seed))));
        }
        assertEquals(10, seen.size(), "every forwarder should be picked over 100 draws");
    }

    @Test
    void lookupForwardersCapsPerSubnetWithoutShrinkingCoverage() {
        PeerRegistry registry = new RegistryBuilder().maxPeers(100).build();
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 6; i++) {
            registry.register(forwarder(i, "10.0.0." + i, now));
        }
        for (int i = 1; i <= 4; i++) {
            registry.register(forwarder(100 + i, "11.0.0." + i, now));
        }
        for (int i = 1; i <= 4; i++) {
            registry.register(forwarder(200 + i, "12.0.0." + i, now));
        }
        List<PeerRecord> result = registry.lookupForwarders(6, new Random(3));
        assertEquals(6, result.size());
        long sameSubnet = result.stream().filter(r -> r.host().startsWith("10.0.0.")).count();
        assertTrue(sameSubnet <= 2, "at most 2 per /24, was " + sameSubnet);
    }

    @Test
    void lookupForwardersRejectsNullRandom() {
        PeerRegistry registry = new RegistryBuilder().build();
        assertThrows(IllegalArgumentException.class, () -> registry.lookupForwarders(1, null));
        assertTrue(registry.lookupForwarders(0, new Random(1)).isEmpty());
    }

    private static PeerRegistry registryWithForwarders(String prefix, int from, int count) {
        PeerRegistry registry = new RegistryBuilder().maxPeers(100).build();
        long now = System.currentTimeMillis();
        for (int i = from; i < from + count; i++) {
            byte[] pub = new byte[32];
            pub[0] = (byte) (i + 1);
            pub[1] = (byte) prefix.hashCode();
            // Distinct /24 per peer so the subnet cap never interferes here.
            registry.register(new PeerRecord(pub, (10 + i) + ".0.0.1", 6888,
                    IceBridgeConfig.Role.FORWARDER, now));
        }
        return registry;
    }

    private static PeerRecord forwarder(int id, String host, long now) {
        byte[] pub = new byte[32];
        pub[0] = (byte) id;
        pub[1] = (byte) (id >> 8);
        return new PeerRecord(pub, host, 6888, IceBridgeConfig.Role.FORWARDER, now);
    }

    private static List<String> hexes(List<PeerRecord> records) {
        List<String> out = new ArrayList<>();
        for (PeerRecord r : records) {
            out.add(com.frostwire.util.Hex.encode(r.ed25519Pub()));
        }
        return out;
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