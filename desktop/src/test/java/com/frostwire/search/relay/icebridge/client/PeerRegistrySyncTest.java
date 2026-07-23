/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PeerRegistrySyncTest {

  private IdentityKeys identity;
  private PeerDirectory directory;
  private PeerRegistry registry;
  private ControlServer server;
  private IceBridgeClient client;
  private PeerRegistrySync sync;
  private String authToken;
  private IceBridgeMetrics metrics;

  @BeforeEach
  void setUp() throws Exception {
    identity = IdentityKeys.generate(0);
    IceBridgeConfig config =
        IceBridgeConfig.newBuilder()
            .controlHttpPort(freePort())
            .rudpPort(0)
            .role(IceBridgeConfig.Role.BOTH)
            .maxPeers(100)
            .peerTtlSec(120)
            .maxQpsPerKey(100.0)
            .build();
    registry = new PeerRegistry(config);
    metrics = new IceBridgeMetrics();
    InboundMessageQueue queue = new InboundMessageQueue();
    RudpSessionManager rudp = new RudpSessionManager(identity, registry, metrics, queue);
    byte[] tokenBytes = new byte[32];
    new java.security.SecureRandom().nextBytes(tokenBytes);
    authToken = com.frostwire.util.Hex.encode(tokenBytes);
    java.io.File tmpTokens = java.io.File.createTempFile("peer-registry-sync-tokens-", ".txt");
    tmpTokens.deleteOnExit();
    IceBridgeTokens tokens = new IceBridgeTokens(tmpTokens);
    tokens.addRuntimeToken(authToken);
    server = new ControlServer(registry, metrics, config, rudp, queue, tokens);
    server.start();
    client = new IceBridgeClient(server.port());
    client.setAuthToken(authToken);
    directory = new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null)));
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
  void syncRoutesVerifiedPeers() throws Exception {
    IdentityKeys other = IdentityKeys.generate(0);
    directory.upsertVerified(other.ed25519PubRaw(), "10.0.0.5", 6888, 6889);
    sync = new PeerRegistrySync(client, directory, "127.0.0.1");
    sync.sync();
    assertTrue(registry.size() >= 1, "verified peer should be routed to IceBridge");
  }

  @Test
  void syncSkipsUnverifiedPeers() throws Exception {
    IdentityKeys other = IdentityKeys.generate(0);
    directory.upsert(other.ed25519PubRaw(), "10.0.0.5", 6888);
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

  @Test
  void syncRegistersSelfAndPullsMeshPeers() throws Exception {
    IdentityKeys peer = IdentityKeys.generate(0);
    // Pre-seed registry as if peer registered on the forwarder.
    registry.register(
        new com.frostwire.search.relay.icebridge.peer.PeerRecord(
            peer.ed25519PubRaw(),
            "10.0.0.9",
            6889,
            IceBridgeConfig.Role.BOTH,
            System.currentTimeMillis()));
    IdentityKeys self = IdentityKeys.generate(0);
    directory = new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null)));
    sync =
        new PeerRegistrySync(client, directory, "127.0.0.1", 6889, self, IceBridgeConfig.Role.BOTH);
    sync.sync();
    assertTrue(
        directory.get(peer.ed25519PubRaw()).isPresent(),
        "mesh lookup peer must be imported as verified");
    assertTrue(directory.topByTrustVerified(10).size() >= 1);
  }

  @Test
  void syncWarmsRoutedPeersWithTelemetryPing() throws Exception {
    IdentityKeys other = IdentityKeys.generate(0);
    directory.upsertVerified(other.ed25519PubRaw(), "10.0.0.5", 6888, 6889);
    sync = new PeerRegistrySync(client, directory, "127.0.0.1");
    sync.sync();
    // push: /route + warm /send; pull: /lookup; import of that same peer: /route + warm /send.
    assertEquals(
        5,
        metrics.controlRequests(),
        "routing a verified peer must warm its rUDP session with a TELEMETRY ping");
  }

  private static int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
