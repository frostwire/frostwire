/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import java.io.File;
import java.net.ServerSocket;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 3-node topology: searcher behind a standalone forwarder (running {@link SearchRelayApp}) reaches
 * an index-holding peer registered on the same forwarder. Proves the forwarder
 * dual-envelope-forwards the signed request and routes the signed response back — the production
 * gap from 2026-07-20 (client knew only the hub, hub swallowed app payloads).
 */
class SearchRelayAppTest {

  @TempDir File tempDir;

  private final List<AutoCloseable> resources = new ArrayList<>();

  @AfterEach
  void tearDown() {
    for (AutoCloseable r : resources) {
      try {
        r.close();
      } catch (Throwable ignored) {
      }
    }
  }

  @Test
  void forwarderAppForwardsSignedRequestAndRoutesSignedResponse() throws Exception {
    // --- 1. Standalone forwarder + search app ---
    IdentityKeys.save(IdentityKeys.generate(0), new File(tempDir, "fwd-identity.dat"));
    IceBridgeServer server = startForwarderWithRetry();
    resources.add(server);
    SearchRelayApp app = SearchRelayApp.start(server);
    resources.add(app);

    // --- 2. "Desktop": index holder registered on the forwarder ---
    IdentityKeys desktopKeys = IdentityKeys.generate(0);
    InMemoryIndex desktopIndex = new InMemoryIndex();
    desktopIndex.torrents.add(torrent("mesh-only-torrent"));
    PeerClient desktop = startPeerClient(desktopKeys, desktopIndex, server);
    // Make the forwarder's app directory aware of the holder NOW (the
    // registry sync import would do the same within one sync tick).
    app.directory()
        .upsertVerified(
            desktopKeys.ed25519PubRaw(),
            "127.0.0.1",
            desktop.rudpPort,
            desktop.rudpPort,
            NodeCapabilities.fromRole("BOTH"),
            "1.1.0");

    // --- 3. "Searcher": knows only the forwarder ---
    IdentityKeys searcherKeys = IdentityKeys.generate(0);
    PeerClient searcher = startPeerClient(searcherKeys, new InMemoryIndex(), server);
    List<byte[]> inbox = new CopyOnWriteArrayList<>();
    searcher.transport.addListener((sourcePub, payload, receivedMs) -> inbox.add(payload));

    // --- 4. Signed request addressed TO THE FORWARDER ---
    RemoteSearchRequest req = signRequest(searcherKeys, "mesh-only", 5, 1);
    boolean sent =
        searcher.transport.send(
            server.identity().ed25519PubRaw(),
            MeshProtocolId.SEARCH,
            SearchPayloadCodec.encodeRequest(req));
    assertTrue(sent, "search request must be accepted by the control plane");

    // --- 5. Signed response from the desktop holder must arrive ---
    RemoteSearchResponse resp = awaitResponse(inbox, 10_000);
    assertNotNull(resp, "signed response must be routed back to the searcher");
    assertEquals(1, resp.rows().size());
    assertEquals("mesh-only-torrent", resp.rows().get(0).name);
    assertTrue(Arrays.equals(req.nonce(), resp.nonce()), "nonce correlation");
    assertTrue(
        verifyResponseSignature(resp, desktopKeys.ed25519PubRaw()),
        "response must verify under the desktop holder's key (dual-envelope preserved)");
  }

  // ---- fixtures ----

  private PeerClient startPeerClient(IdentityKeys keys, InMemoryIndex index, IceBridgeServer server)
      throws Exception {
    IceBridgeClient client = new IceBridgeClient(server.controlPort());
    client.setAuthToken(server.authToken());
    // USE_REMOTE-style registration: no rUDP listener, so we register the
    // forwarder's own rUDP port — isLocalRudpEndpoint then delivers to
    // our /poll demux queue instead of black-holing over UDP.
    int rudpPort = server.rudpPort();
    assertTrue(
        client.register(keys, "127.0.0.1", rudpPort, IceBridgeConfig.Role.BOTH),
        "peer client must register on the forwarder");
    IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
    transport.start();
    resources.add(transport);
    PeerDirectory directory =
        new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(peerPub -> null)));
    RelaySearchService service = new RelaySearchService(index, keys);
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, keys, index);
    handler.start();
    resources.add(handler::stop);
    return new PeerClient(keys, client, transport, rudpPort);
  }

  /**
   * The forwarder answers immediately from its empty index (0 rows) AND forwards; the holder's
   * signed answer (1 row) arrives later. Wait for the first response with actual rows.
   */
  private static RemoteSearchResponse awaitResponse(List<byte[]> inbox, long timeoutMs)
      throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      for (byte[] payload : inbox) {
        RemoteSearchResponse resp = SearchPayloadCodec.decodeResponse(payload);
        if (resp != null && !resp.rows().isEmpty()) {
          return resp;
        }
      }
      Thread.sleep(100);
    }
    return null;
  }

  private static RemoteSearchRequest signRequest(
      IdentityKeys keys, String keywords, int limit, int ttl) throws Exception {
    byte[] nonce = new byte[32];
    for (int i = 0; i < 32; i++) {
      nonce[i] = (byte) i;
    }
    long ts = System.currentTimeMillis() / 1000L;
    RemoteSearchRequest unsigned =
        RemoteSearchRequest.builder()
            .keywords(keywords)
            .limit(limit)
            .nonce(nonce)
            .ttl(ttl)
            .requesterPub(keys.ed25519PubRaw())
            .path(new byte[0][])
            .timestamp(ts)
            .signature(new byte[64])
            .build();
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    signer.initSign(keys.ed25519().getPrivate());
    signer.update(unsigned.canonicalBytes());
    return RemoteSearchRequest.builder()
        .keywords(keywords)
        .limit(limit)
        .nonce(nonce)
        .ttl(ttl)
        .requesterPub(keys.ed25519PubRaw())
        .path(new byte[0][])
        .timestamp(ts)
        .signature(signer.sign())
        .build();
  }

  private static boolean verifyResponseSignature(
      RemoteSearchResponse response, byte[] expectedPubRaw) {
    try {
      byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
      byte[] encoded = new byte[prefix.length + expectedPubRaw.length];
      System.arraycopy(prefix, 0, encoded, 0, prefix.length);
      System.arraycopy(expectedPubRaw, 0, encoded, prefix.length, expectedPubRaw.length);
      PublicKey pub =
          IdentityKeys.softwareKeyFactory("Ed25519")
              .generatePublic(new X509EncodedKeySpec(encoded));
      Signature verifier = IdentityKeys.softwareSignature("Ed25519");
      verifier.initVerify(pub);
      verifier.update(response.canonicalBytes());
      return verifier.verify(response.signature());
    } catch (Exception e) {
      return false;
    }
  }

  private static LocalSharedTorrent torrent(String name) {
    long now = System.currentTimeMillis() / 1000L;
    return new LocalSharedTorrent.Builder()
        .infoHash(new byte[20])
        .name(name)
        .sizeBytes(1000)
        .fileCount(1)
        .filesJson("[]")
        .publisherNodeId(new byte[20])
        .publisherEd25519Pub(new byte[32])
        .publisherUtpPort(0)
        .addedAt(now)
        .lastSeenAt(now)
        .build();
  }

  /**
   * freePort() close-then-bind is racy under a busy suite JVM fork; retry with fresh ports on
   * BindException.
   */
  private IceBridgeServer startForwarderWithRetry() throws Exception {
    Throwable last = null;
    for (int attempt = 0; attempt < 5; attempt++) {
      IceBridgeServer server = null;
      try {
        IceBridgeConfig config =
            IceBridgeConfig.newBuilder()
                .host("127.0.0.1")
                .rudpPort(freePort())
                .relayPort(freePort())
                .controlHttpPort(freePort())
                .role(IceBridgeConfig.Role.FORWARDER)
                .maxPeers(100)
                .peerTtlSec(60)
                .maxQpsPerKey(1000.0)
                .identityFile(new File(tempDir, "fwd-identity.dat"))
                .dhtEnabled(false)
                .build();
        server = new IceBridgeServer(config);
        server.start();
        return server;
      } catch (Throwable t) {
        last = t;
        if (server != null) {
          try {
            server.close();
          } catch (Throwable ignored) {
          }
        }
      }
    }
    throw new IllegalStateException("forwarder could not bind after retries", last);
  }

  private static int freePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static final class PeerClient {
    final IdentityKeys keys;
    final IceBridgeClient client;
    final IceBridgeSearchTransport transport;
    final int rudpPort;

    PeerClient(
        IdentityKeys keys,
        IceBridgeClient client,
        IceBridgeSearchTransport transport,
        int rudpPort) {
      this.keys = keys;
      this.client = client;
      this.transport = transport;
      this.rudpPort = rudpPort;
    }
  }

  private static final class InMemoryIndex implements LocalIndex {
    final List<LocalSharedTorrent> torrents = new ArrayList<>();

    @Override
    public void upsert(LocalSharedTorrent torrent) {}

    @Override
    public void delete(String infoHashHex) {}

    @Override
    public Optional<LocalSharedTorrent> get(String infoHashHex) {
      return Optional.empty();
    }

    @Override
    public List<LocalSharedTorrent> search(String query, int limit) {
      List<LocalSharedTorrent> out = new ArrayList<>();
      for (LocalSharedTorrent t : torrents) {
        if (t.name().toLowerCase().contains(query.toLowerCase())) {
          out.add(t);
        }
      }
      return out;
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {}

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
      return new ArrayList<>();
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {}

    @Override
    public int size() {
      return torrents.size();
    }
  }
}
