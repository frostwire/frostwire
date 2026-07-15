/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Android embeds IceBridge with relayPort=0 so only one IncomingRelayServer
 * binds TCP identity (full RelayRole). Standalone keeps default 6888.
 */
class IceBridgeRelayPortZeroTest {

    @TempDir
    File tmp;

    @Test
    void builderAcceptsRelayPortZero() {
        IceBridgeConfig cfg = IceBridgeConfig.newBuilder()
                .host("127.0.0.1")
                .rudpPort(0)
                .relayPort(0)
                .controlHttpPort(findFreeTcpPort())
                .role(IceBridgeConfig.Role.BOTH)
                .identityFile(new File(tmp, "id.dat"))
                .maxPeers(10)
                .peerTtlSec(60)
                .maxQpsPerKey(1.0)
                .build();
        assertEquals(0, cfg.relayPort());
    }

    @Test
    void defaultRelayPortIsPositive() {
        IceBridgeConfig cfg = IceBridgeConfig.newBuilder()
                .host("127.0.0.1")
                .rudpPort(0)
                .controlHttpPort(findFreeTcpPort())
                .role(IceBridgeConfig.Role.BOTH)
                .identityFile(new File(tmp, "id.dat"))
                .maxPeers(10)
                .peerTtlSec(60)
                .maxQpsPerKey(1.0)
                .build();
        assertTrue(cfg.relayPort() > 0);
    }

    private static int findFreeTcpPort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
