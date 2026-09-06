/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Headless two-instance integration test for TORRENT_FETCH (Protocol #3 METADATA): requester A
 * fetches the full .torrent bytes of a desktop-created torrent from holder B over the IceBridge
 * mesh — the NAT-proof metadata path for cellular peers that cannot reach the seeder directly.
 *
 * <p>Verified flow:
 *
 * <ol>
 *   <li>A's {@link MeshTorrentMetadataFetcher} signs and sends a targeted request to B's pub via
 *       rUDP
 *   <li>B's {@link IncomingSearchRequestHandler} demuxes METADATA, verifies the requester
 *       signature, rate-limits, and asks its {@link TorrentMetadataProvider}
 *   <li>B serializes the full torrent in holder-signed chunks sized for the ~1 KB relay frame and
 *       sends them back
 *   <li>A verifies every chunk under B's pub, reassembles, and checks the whole-payload digest
 * </ol>
 */
class TorrentFetchOverMeshTest {

  private final List<AutoCloseable> resources = new ArrayList<>();

  @AfterEach
  void tearDown() {
    java.util.Collections.reverse(resources);
    for (AutoCloseable r : resources) {
      try {
        r.close();
      } catch (Throwable ignored) {
      }
    }
    resources.clear();
  }

  @Test
  void requesterFetchesMultiChunkTorrentFromHolderOverRudp() throws Exception {
    Instance requester = startInstance("requester");
    Instance holder = startInstance("holder");

    crossRegister(requester, holder);

    byte[] infoHash = TestTorrentMetadata.infoHash();
    // Genuine bounded metadata with a comment large enough to exercise streaming.
    byte[] torrentBytes = TestTorrentMetadata.bytes(1000);
    holder.provider.delegate = infoHash1 -> infoHash1 == null ? null : torrentBytes;

    byte[] fetched =
        MeshTorrentMetadataFetcher.fetch(
            requester.transport,
            requester.identity,
            holder.identity.ed25519PubRaw(),
            infoHash,
            10_000);

    assertNotNull(fetched, "metadata should arrive over the mesh");
    assertArrayEquals(torrentBytes, fetched);
  }

  @Test
  void notFoundFallsBackFastWithoutWaitingOutTheTimeout() throws Exception {
    Instance requester = startInstance("requester");
    Instance holder = startInstance("holder");

    crossRegister(requester, holder);

    byte[] infoHash = new byte[20];
    new SecureRandom().nextBytes(infoHash);
    holder.provider.delegate = infoHash1 -> null; // holder does not have it

    long start = System.currentTimeMillis();
    byte[] fetched =
        MeshTorrentMetadataFetcher.fetch(
            requester.transport,
            requester.identity,
            holder.identity.ed25519PubRaw(),
            infoHash,
            10_000);
    long elapsed = System.currentTimeMillis() - start;

    assertNull(fetched, "NOT_FOUND must yield null");
    assertTrue(elapsed < 8_000, "signed NOT_FOUND should fast-fallback, took " + elapsed + "ms");
  }

  @Test
  void cachedMetadataIsWithdrawnWithoutRestartingHolder() throws Exception {
    Instance requester = startInstance("withdraw-requester");
    Instance holder = startInstance("withdraw-holder");
    crossRegister(requester, holder);
    byte[] torrent = TestTorrentMetadata.bytes(500);
    java.util.concurrent.atomic.AtomicInteger reads =
        new java.util.concurrent.atomic.AtomicInteger();
    holder.provider.delegate =
        hash -> {
          reads.incrementAndGet();
          return torrent;
        };
    assertArrayEquals(
        torrent,
        MeshTorrentMetadataFetcher.fetch(
            requester.transport,
            requester.identity,
            holder.identity.ed25519PubRaw(),
            TestTorrentMetadata.infoHash(),
            10_000));
    holder.provider.publiclyShared = false;
    int beforeWithdrawal = reads.get();
    assertNull(
        MeshTorrentMetadataFetcher.fetch(
            requester.transport,
            requester.identity,
            holder.identity.ed25519PubRaw(),
            TestTorrentMetadata.infoHash(),
            10_000));
    org.junit.jupiter.api.Assertions.assertEquals(
        beforeWithdrawal,
        reads.get(),
        "revoked metadata must not access the provider or its positive cache");
    holder.provider.publiclyShared = true;
    holder.provider.handlerPublic = false;
    assertNull(
        MeshTorrentMetadataFetcher.fetch(
            requester.transport,
            requester.identity,
            holder.identity.ed25519PubRaw(),
            TestTorrentMetadata.infoHash(),
            10_000));
    org.junit.jupiter.api.Assertions.assertEquals(
        beforeWithdrawal,
        reads.get(),
        "direct handler policy must deny even when the provider authorizes metadata");
  }

  @Test
  void signedMetadataWithWrongActualHashIsRejectedOverRudp() throws Exception {
    Instance requester = startInstance("wrong-requester");
    Instance holder = startInstance("wrong-holder");
    crossRegister(requester, holder);
    holder.provider.delegate = hash -> TestTorrentMetadata.bytes(500);
    byte[] selected = TestTorrentMetadata.infoHash();
    selected[0] ^= 1;
    assertNull(
        MeshTorrentMetadataFetcher.fetch(
            requester.transport,
            requester.identity,
            holder.identity.ed25519PubRaw(),
            selected,
            10_000));
  }

  @Test
  void fetchTimesOutWhenHolderDoesNotAnswer() throws Exception {
    Instance requester = startInstance("requester");
    // No holder instance at all: send targets an unknown pub.
    // A directory-only registration still lets the transport accept
    // the send; nothing answers, so the fetcher must time out.

    byte[] infoHash = new byte[20];
    new SecureRandom().nextBytes(infoHash);
    byte[] unknownHolder = new byte[32];
    new SecureRandom().nextBytes(unknownHolder);

    long start = System.currentTimeMillis();
    byte[] fetched =
        MeshTorrentMetadataFetcher.fetch(
            requester.transport, requester.identity, unknownHolder, infoHash, 1_500);
    long elapsed = System.currentTimeMillis() - start;

    assertNull(fetched);
    assertTrue(elapsed < 1_400, "unknown target should fail fast, took " + elapsed + "ms");
  }

  @Test
  void wireSmokeChunkDataFitsRelayFrame() {
    byte[] chunk = new byte[TorrentMetadataResponse.CHUNK_DATA_BYTES];
    byte[] chunkJson =
        SearchPayloadCodec.encodeTorrentMetadataResponse(
            TorrentMetadataResponse.builder()
                .nonce(new byte[32])
                .infoHash(new byte[20])
                .payloadDigest(new byte[32])
                .timestamp(1L)
                .chunkIndex(0)
                .finalChunk(true)
                .data(chunk)
                .signature(new byte[64])
                .build());
    int wireLength =
        com.frostwire.search.relay.icebridge.MeshEnvelope.encodeForWire(
                com.frostwire.search.relay.icebridge.MeshProtocolId.METADATA, chunkJson)
            .length;
    assertTrue(
        wireLength <= com.frostwire.search.relay.icebridge.udp.RelayFrame.MAX_APP_PAYLOAD,
        "envelope-wrapped chunk wire ("
            + wireLength
            + "B) must fit the mesh RELAY cap ("
            + com.frostwire.search.relay.icebridge.udp.RelayFrame.MAX_APP_PAYLOAD
            + "B)");
  }

  // --- helpers ---

  private void crossRegister(Instance a, Instance b) throws Exception {
    a.directory.upsertVerified(b.identity.ed25519PubRaw(), "127.0.0.1", b.rudpPort);
    b.directory.upsertVerified(a.identity.ed25519PubRaw(), "127.0.0.1", a.rudpPort);
    assertTrue(
        a.client.route(
            b.identity.ed25519PubRaw(), "127.0.0.1", b.rudpPort, IceBridgeConfig.Role.BOTH));
    assertTrue(
        b.client.route(
            a.identity.ed25519PubRaw(), "127.0.0.1", a.rudpPort, IceBridgeConfig.Role.BOTH));
  }

  private Instance startInstance(String label) throws Exception {
    Path tmpDir = Files.createTempDirectory("frostwire-torrentfetch-" + label + "-");
    Path identityFile = tmpDir.resolve("identity.dat");

    IdentityKeys preGenerated = IdentityKeys.generate(0);
    IdentityKeys.save(preGenerated, identityFile.toFile());

    IceBridgeConfig config =
        IceBridgeConfig.newBuilder()
            .rudpPort(freePort())
            .controlHttpPort(freePort())
            .role(IceBridgeConfig.Role.BOTH)
            .maxPeers(100)
            .peerTtlSec(120)
            .maxQpsPerKey(100.0)
            .identityFile(identityFile.toFile())
            .build();

    IceBridgeServer server = new IceBridgeServer(config);
    server.start();
    resources.add(server);

    IceBridgeClient client = new IceBridgeClient(server.controlPort());
    client.setAuthToken(server.authToken());
    boolean healthy = false;
    for (int i = 0; i < 100; i++) {
      if (client.health()) {
        healthy = true;
        break;
      }
      Thread.sleep(50);
    }
    assertTrue(healthy, "Instance " + label + " did not become healthy");

    IdentityKeys identity = server.identity();
    assertTrue(
        client.register(identity, "127.0.0.1", server.rudpPort(), IceBridgeConfig.Role.BOTH));
    resources.add(client);
    NoOpKarmaCache karma = new NoOpKarmaCache();
    resources.add(karma);
    PeerDirectory directory = new PeerDirectory(karma);

    IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
    transport.start();
    resources.add(transport);

    MutableProvider provider = new MutableProvider();
    RelaySearchService searchService =
        new RelaySearchService(new EmptyLocalIndex(), identity, hash -> provider.handlerPublic);
    IncomingSearchRequestHandler incomingHandler =
        new IncomingSearchRequestHandler(transport, searchService, directory, identity);
    incomingHandler.setTorrentMetadataProvider(provider);
    incomingHandler.start();
    resources.add(() -> incomingHandler.stop());

    return new Instance(identity, directory, client, transport, provider, server.rudpPort());
  }

  private static int freePort() throws java.io.IOException {
    try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  private static final class MutableProvider implements TorrentMetadataProvider {
    volatile TorrentMetadataProvider delegate = infoHashV1 -> null;
    volatile boolean publiclyShared = true;
    volatile boolean handlerPublic = true;

    @Override
    public boolean isPubliclyShared(byte[] infoHashV1) {
      return publiclyShared;
    }

    @Override
    public byte[] torrentBytes(byte[] infoHashV1) {
      return delegate.torrentBytes(infoHashV1);
    }
  }

  private static final class Instance {
    final IdentityKeys identity;
    final PeerDirectory directory;
    final IceBridgeClient client;
    final IceBridgeSearchTransport transport;
    final MutableProvider provider;
    final int rudpPort;

    Instance(
        IdentityKeys identity,
        PeerDirectory directory,
        IceBridgeClient client,
        IceBridgeSearchTransport transport,
        MutableProvider provider,
        int rudpPort) {
      this.identity = identity;
      this.directory = directory;
      this.client = client;
      this.transport = transport;
      this.provider = provider;
      this.rudpPort = rudpPort;
    }
  }

  private static final class NoOpKarmaCache extends PeerKarmaCache {
    NoOpKarmaCache() {
      super(new RemoteKarmaChainFetcher(new NoOpKarmaSource()));
    }

    @Override
    public long getKarma(byte[] peerPub) {
      return 0;
    }
  }

  private static final class NoOpKarmaSource implements KarmaChainSource {
    @Override
    public com.frostwire.jlibtorrent.Entry fetchManifest(byte[] peerPub) {
      return null;
    }
  }
}
