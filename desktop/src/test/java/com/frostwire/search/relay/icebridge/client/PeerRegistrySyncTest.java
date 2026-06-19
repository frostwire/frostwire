/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

class PeerRegistrySyncTest {

    private IdentityKeys identity;
    private PeerDirectory directory;
    private PeerRegistry registry;
    private ControlServer server;
    private IceBridgeClient client;
    private PeerRegistrySync sync;

    @BeforeEach
    void setUp() throws Exception {
        identity = IdentityKeys.generate(0);
        IceBridgeConfig config = IceBridgeConfig.newBuilder()
                .controlHttpPort(freePort())
                .rudpPort(0)
                .role(IceBridgeConfig.Role.BOTH)
                .maxPeers(100)
                .peerTtlSec(120)
                .maxQpsPerKey(100.0)
                .build();
        registry = new PeerRegistry(config);
        IceBridgeMetrics metrics = new IceBridgeMetrics();
        InboundMessageQueue queue = new InboundMessageQueue();
        RudpSessionManager rudp = new RudpSessionManager(identity, registry, metrics, queue);
        server = new ControlServer(registry, metrics, config, rudp, queue, null);
        server.start();
        client = new IceBridgeClient(server.port());
        directory = new PeerDirectory(new PeerKarmaCache(
                new RemoteKarmaChainFetcher(pub -> null)));
    }

    @AfterEach
    void tearDown() {
        if (sync != null) {
            sync.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void syncRoutesVerifiedPeers() {
        directory.upsertVerified(identity.ed25519PubRaw(), "10.0.0.5", 6888);
        sync = new PeerRegistrySync(client, directory, "127.0.0.1");
        sync.sync();
        assertEquals(1, registry.size(), "verified peer should be routed to IceBridge");
    }

    @Test
    void syncSkipsUnverifiedPeers() {
        directory.upsert(identity.ed25519PubRaw(), "10.0.0.5", 6888);
        sync = new PeerRegistrySync(client, directory, "127.0.0.1");
        sync.sync();
        assertEquals(0, registry.size(), "unverified peers should not be synced");
    }

    @Test
    void syncHandlesEmptyDirectory() {
        sync = new PeerRegistrySync(client, directory, "127.0.0.1");
        sync.sync();
        assertEquals(0, registry.size());
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
