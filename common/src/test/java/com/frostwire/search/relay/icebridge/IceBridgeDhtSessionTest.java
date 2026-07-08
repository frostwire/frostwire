/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests that do not require loading native jlibtorrent.
 * Full SessionManager.start is covered by integration / manual EC2 smoke.
 */
class IceBridgeDhtSessionTest {

    @Test
    void bootstrapNodesListIsNonEmptyAndHasKnownRouters() {
        String nodes = IceBridgeDhtSession.DHT_BOOTSTRAP_NODES;
        assertTrue(nodes.contains("router.bittorrent.com"));
        assertTrue(nodes.contains("dht.libtorrent.org"));
        assertTrue(nodes.contains(","));
    }

    @Test
    void cloudDefaultsEnableDht() {
        IceBridgeConfig cloud = IceBridgeConfig.cloudDefaults();
        assertTrue(cloud.dhtEnabled());
        assertTrue(cloud.bootstrap());
    }

    @Test
    void builderDefaultsDisableDhtForInProcessTests() {
        IceBridgeConfig local = IceBridgeConfig.newBuilder()
                .rudpPort(0)
                .controlHttpPort(8797)
                .role(IceBridgeConfig.Role.BOTH)
                .build();
        assertFalse(local.dhtEnabled(),
                "in-process builders must not auto-start DHT (Android/desktop use BTEngine)");
    }
}
