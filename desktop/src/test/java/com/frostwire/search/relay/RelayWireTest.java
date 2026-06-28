/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.Entry;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelayWireTest {

  private static KeyPair requesterKey;
  private static byte[] requesterPub;
  private static IdentityKeys responderIdentity;

  @BeforeAll
  static void setUpClass() throws Exception {
    requesterKey = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    requesterPub = IdentityRecord.extractRawEd25519(requesterKey.getPublic());
    responderIdentity = IdentityKeys.generate(4);
  }

  private NoopLocalIndex index;
  private PeerDirectory directory;
  private RelaySearchService service;
  private RelayRole role;
  private IncomingRelayServer server;

  @BeforeEach
  void setUp() throws Exception {
    index = new NoopLocalIndex();
    directory = new PeerDirectory(new NoopKarmaCache());
    service = new RelaySearchService(index, responderIdentity);
    role = new RelayRole(service, directory);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void encodeAndDecodeRequestRoundTrip() throws Exception {
    RemoteSearchRequest req = signRequest("ubuntu", 5);
    byte[] bytes = RelayWireCodec.encodeRequest(req);
    RemoteSearchRequest decoded = RelayWireCodec.decodeRequest(bytes);
    assertNotNull(decoded);
    assertEquals(req.keywords(), decoded.keywords());
    assertEquals(req.limit(), decoded.limit());
    assertArrayEquals(req.nonce(), decoded.nonce());
    assertArrayEquals(req.requesterPub(), decoded.requesterPub());
    assertArrayEquals(req.signature(), decoded.signature());
  }

  @Test
  void encodeAndDecodeResponseRoundTrip() {
    byte[] nonce = new byte[32];
    byte[] infoHash = new byte[20];
    byte[] pub = new byte[32];
    byte[] sig = new byte[64];
    for (int i = 0; i < sig.length; i++) sig[i] = (byte) i;
    RemoteSearchResponse resp =
        RemoteSearchResponse.builder()
            .nonce(nonce)
            .timestamp(1700000000L)
            .addRow(infoHash, "ubuntu-22.04", 1000, 1, pub)
            .addRow(infoHash.clone(), "debian-12", 2000, 2, pub, null)
            .signature(sig)
            .build();
    byte[] bytes = RelayWireCodec.encodeResponse(resp);
    RemoteSearchResponse decoded = RelayWireCodec.decodeResponse(bytes);
    assertNotNull(decoded);
    assertEquals(2, decoded.rows().size());
    assertArrayEquals(nonce, decoded.nonce());
    assertArrayEquals(sig, decoded.signature());
    assertEquals("ubuntu-22.04", decoded.rows().get(0).name);
    assertEquals("debian-12", decoded.rows().get(1).name);
  }

  @Test
  void codecRejectsNullInputs() {
    assertThrows(IllegalArgumentException.class, () -> RelayWireCodec.encodeRequest(null));
    assertThrows(IllegalArgumentException.class, () -> RelayWireCodec.encodeResponse(null));
    assertThrows(
        IllegalArgumentException.class, () -> RelayWireCodec.writeFrame(null, new byte[10]));
    assertThrows(
        IllegalArgumentException.class,
        () -> RelayWireCodec.writeFrame(new java.io.ByteArrayOutputStream(), null));
    assertThrows(IllegalArgumentException.class, () -> RelayWireCodec.readFrame(null));
  }

  @Test
  void decodeRequestRejectsInvalidPayload() {
    assertNull(RelayWireCodec.decodeRequest(null));
    assertNull(RelayWireCodec.decodeRequest(new byte[] {1, 2, 3}));
  }

  @Test
  void decodeResponseRejectsInvalidPayload() {
    assertNull(RelayWireCodec.decodeResponse(null));
    assertNull(RelayWireCodec.decodeResponse(new byte[] {1, 2, 3}));
  }

  @Test
  void writeAndReadFrameRoundTrip() throws Exception {
    byte[] payload = new byte[] {1, 2, 3, 4, 5};
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    RelayWireCodec.writeFrame(out, payload);
    byte[] framed = out.toByteArray();
    assertEquals(RelayWireCodec.LENGTH_PREFIX_BYTES + payload.length, framed.length);
    java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(framed);
    byte[] read = RelayWireCodec.readFrame(in);
    assertArrayEquals(payload, read);
  }

  @Test
  void readFrameReturnsNullOnCleanEof() throws Exception {
    java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(new byte[0]);
    assertNull(RelayWireCodec.readFrame(in));
  }

  @Test
  void readFrameRejectsOversizeFrame() {
    // Frame length prefix claims 2 MB which exceeds MAX_FRAME_BYTES
    byte[] fake =
        new byte[] {
          (byte) 0x00, (byte) 0x10, (byte) 0x00, (byte) 0x00 // 1,048,576 = exactly 1 MB
        };
    java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(fake);
    // 1 MB is exactly the cap, so this should still be read. Let's
    // try 1 MB + 1 instead.
    byte[] tooBig =
        new byte[] {
          (byte) 0x00, (byte) 0x10, (byte) 0x00, (byte) 0x01 // 1 MB + 1
        };
    java.io.ByteArrayInputStream in2 = new java.io.ByteArrayInputStream(tooBig);
    assertThrows(java.io.IOException.class, () -> RelayWireCodec.readFrame(in2));
  }

  @Test
  void inProcessServerHandlesValidRequest() throws Exception {
    index.torrents.add(torrent("ubuntu", 1000, 1));
    directory.upsert(requesterPub, "test", 1);
    server = new IncomingRelayServer(role, 0);
    server.start();
    int port = server.port();

    OutgoingRelayClient client = new OutgoingRelayClient();
    RemoteSearchRequest req = signRequest("ubuntu", 5);
    Optional<RemoteSearchResponse> resp = client.send("127.0.0.1", port, req);

    assertTrue(resp.isPresent());
    assertEquals(1, resp.get().rows().size());
    assertEquals("ubuntu", resp.get().rows().get(0).name);
  }

  @Test
  void verifiedSendAcceptsValidResponse() throws Exception {
    index.torrents.add(torrent("ubuntu", 1000, 1));
    directory.upsert(requesterPub, "test", 1);
    server = new IncomingRelayServer(role, 0);
    server.start();
    int port = server.port();

    OutgoingRelayClient client = new OutgoingRelayClient();
    RemoteSearchRequest req = signRequest("ubuntu", 5);
    Optional<RemoteSearchResponse> resp =
        client.send("127.0.0.1", port, req, responderIdentity.ed25519PubRaw());

    assertTrue(resp.isPresent());
    assertEquals(1, resp.get().rows().size());
  }

  @Test
  void verifiedSendRejectsWrongResponderPub() throws Exception {
    index.torrents.add(torrent("ubuntu", 1000, 1));
    directory.upsert(requesterPub, "test", 1);
    server = new IncomingRelayServer(role, 0);
    server.start();
    int port = server.port();

    OutgoingRelayClient client = new OutgoingRelayClient();
    RemoteSearchRequest req = signRequest("ubuntu", 5);
    byte[] wrongPub = new byte[32];
    wrongPub[31] = 0x42;
    Optional<RemoteSearchResponse> resp = client.send("127.0.0.1", port, req, wrongPub);

    assertTrue(resp.isEmpty(), "Response signed by a different peer must be rejected");
  }

  @Test
  void verifiedSendRejectsInvalidResponderPubInput() throws Exception {
    OutgoingRelayClient client = new OutgoingRelayClient();
    RemoteSearchRequest req = signRequest("x", 1);
    assertTrue(client.send("127.0.0.1", 1234, req, null).isEmpty());
    assertTrue(client.send("127.0.0.1", 1234, req, new byte[31]).isEmpty());
  }

  @Test
  void identityHandshakeReturnsConfiguredRecord() throws Exception {
    IdentityRecord identity = sampleIdentityRecord();
    server = new IncomingRelayServer(role, identity, 0);
    server.start();
    int port = server.port();

    OutgoingRelayClient client = new OutgoingRelayClient();
    Optional<IdentityRecord> fetched = client.fetchIdentity("127.0.0.1", port);

    assertTrue(fetched.isPresent());
    assertArrayEquals(identity.ed25519Pub(), fetched.get().ed25519Pub());
    assertTrue(fetched.get().verifySignature());
  }

  @Test
  void identityHandshakeReturnsEmptyWhenServerHasNoIdentity() throws Exception {
    server = new IncomingRelayServer(role, 0);
    server.start();
    int port = server.port();

    OutgoingRelayClient client = new OutgoingRelayClient();
    Optional<IdentityRecord> fetched = client.fetchIdentity("127.0.0.1", port);

    assertTrue(fetched.isEmpty());
  }

  @Test
  void directTcpAuthenticatorAcceptsValidServerIdentity() throws Exception {
    IdentityRecord identity = sampleIdentityRecord();
    server = new IncomingRelayServer(role, identity, 0);
    server.start();
    int port = server.port();

    DirectTcpPeerAuthenticator auth = new DirectTcpPeerAuthenticator();
    Optional<IdentityRecord> fetched = auth.authenticate("127.0.0.1", port);

    assertTrue(fetched.isPresent());
    assertArrayEquals(identity.ed25519Pub(), fetched.get().ed25519Pub());
  }

  @Test
  void directTcpAuthenticatorRejectsMissingServerIdentity() throws Exception {
    server = new IncomingRelayServer(role, 0);
    server.start();
    int port = server.port();

    DirectTcpPeerAuthenticator auth = new DirectTcpPeerAuthenticator();
    Optional<IdentityRecord> fetched = auth.authenticate("127.0.0.1", port);

    assertTrue(fetched.isEmpty(), "Peer without a published identity record must not authenticate");
  }

  @Test
  void verifyResponseAcceptsValidResponse() throws Exception {
    RemoteSearchRequest req = signRequest("ubuntu", 5);
    RemoteSearchResponse resp = signResponse(req.nonce(), System.currentTimeMillis() / 1000L);
    assertTrue(SearchResponseVerifier.verify(resp, req, responderIdentity.ed25519PubRaw()));
  }

  @Test
  void verifyResponseRejectsWrongNonce() throws Exception {
    RemoteSearchRequest req = signRequest("ubuntu", 5);
    byte[] wrongNonce = req.nonce().clone();
    wrongNonce[0] ^= 1;
    RemoteSearchResponse resp = signResponse(wrongNonce, System.currentTimeMillis() / 1000L);
    assertFalse(SearchResponseVerifier.verify(resp, req, responderIdentity.ed25519PubRaw()));
  }

  @Test
  void verifyResponseRejectsStaleTimestamp() throws Exception {
    RemoteSearchRequest req = signRequest("ubuntu", 5);
    long stale =
        System.currentTimeMillis() / 1000L - RemoteSearchRequest.MAX_TIMESTAMP_SKEW_SEC - 60;
    RemoteSearchResponse resp = signResponse(req.nonce(), stale);
    assertFalse(SearchResponseVerifier.verify(resp, req, responderIdentity.ed25519PubRaw()));
  }

  @Test
  void verifyResponseRejectsBadSignature() throws Exception {
    RemoteSearchRequest req = signRequest("ubuntu", 5);
    RemoteSearchResponse resp = signResponse(req.nonce(), System.currentTimeMillis() / 1000L);
    byte[] tamperedSig = resp.signature().clone();
    tamperedSig[0] ^= 1;
    RemoteSearchResponse.Builder b =
        RemoteSearchResponse.builder()
            .nonce(resp.nonce())
            .timestamp(resp.timestamp())
            .signature(tamperedSig);
    for (RemoteSearchResponse.Row r : resp.rows()) {
      b.addRow(
          r.infoHash, r.name, r.sizeBytes, r.fileCount, r.publisherEd25519Pub, r.publisherNodeId);
    }
    RemoteSearchResponse tampered = b.build();
    assertFalse(SearchResponseVerifier.verify(tampered, req, responderIdentity.ed25519PubRaw()));
  }

  @Test
  void inProcessServerRejectsInvalidSignature() throws Exception {
    index.torrents.add(torrent("ubuntu", 1000, 1));
    directory.upsert(requesterPub, "test", 1);
    server = new IncomingRelayServer(role, 0);
    server.start();
    int port = server.port();

    // Send an invalid request (zero-length signature)
    RemoteSearchRequest bad =
        RemoteSearchRequest.builder()
            .nonce(new byte[32])
            .requesterPub(requesterPub)
            .keywords("ubuntu")
            .limit(5)
            .timestamp(System.currentTimeMillis() / 1000L)
            .signature(new byte[64])
            .build();
    OutgoingRelayClient client = new OutgoingRelayClient();
    assertTrue(client.send("127.0.0.1", port, bad).isEmpty());
  }

  @Test
  void inProcessServerRejectsConnectionToClosedPort() throws Exception {
    server = new IncomingRelayServer(role, 0);
    server.start();
    int port = server.port();
    server.stop();
    OutgoingRelayClient client = new OutgoingRelayClient(1000, 1000);
    Optional<RemoteSearchResponse> resp = client.send("127.0.0.1", port, signRequest("x", 1));
    assertTrue(resp.isEmpty());
  }

  @Test
  void clientRejectsBadHostInputs() throws Exception {
    OutgoingRelayClient client = new OutgoingRelayClient();
    assertTrue(client.send(null, 1234, signRequest("x", 1)).isEmpty());
    assertTrue(client.send("", 1234, signRequest("x", 1)).isEmpty());
    assertTrue(client.send("127.0.0.1", 0, signRequest("x", 1)).isEmpty());
    assertTrue(client.send("127.0.0.1", -1, signRequest("x", 1)).isEmpty());
    assertTrue(client.send("127.0.0.1", 99999, signRequest("x", 1)).isEmpty());
    assertTrue(client.send("127.0.0.1", 1234, null).isEmpty());
  }

  @Test
  void inProcessServerTracksConnectionCount() throws Exception {
    server = new IncomingRelayServer(role, 0);
    server.start();
    int port = server.port();
    OutgoingRelayClient client = new OutgoingRelayClient();
    for (int i = 0; i < 3; i++) {
      client.send("127.0.0.1", port, signRequest("u" + i, 1));
    }
    // Give the server a moment to count
    Thread.sleep(100);
    assertTrue(server.connectionCount() >= 3);
  }

  @Test
  void serverIdempotentStart() throws Exception {
    server = new IncomingRelayServer(role, 0);
    server.start();
    server.start(); // no-op
    assertTrue(server.isRunning());
  }

  @Test
  void serverStopWithoutStartIsNoOp() {
    server = new IncomingRelayServer(role, 0);
    server.stop();
    assertFalse(server.isRunning());
  }

  @Test
  void serverConstructorRejectsBadInputs() {
    assertThrows(
        IllegalArgumentException.class, () -> new IncomingRelayServer((RelayRole) null, 0));
    assertThrows(IllegalArgumentException.class, () -> new IncomingRelayServer(role, -1));
    assertThrows(IllegalArgumentException.class, () -> new IncomingRelayServer(role, 99999));
    assertThrows(IllegalArgumentException.class, () -> new IncomingRelayServer(role, 0, 0, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new IncomingRelayServer(role, 0, 1, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> new IncomingRelayServer(role, 0, 1, 1, -1));
  }

  @Test
  void clientConstructorRejectsBadInputs() {
    assertThrows(IllegalArgumentException.class, () -> new OutgoingRelayClient(0, 1));
    assertThrows(IllegalArgumentException.class, () -> new OutgoingRelayClient(1, 0));
    assertThrows(IllegalArgumentException.class, () -> new OutgoingRelayClient(-1, 1));
  }

  // --- helpers ---

  private static RemoteSearchRequest signRequest(String keywords, int limit) throws Exception {
    long ts = System.currentTimeMillis() / 1000L;
    byte[] nonce = new byte[32];
    for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
    RemoteSearchRequest unsigned =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(requesterPub)
            .keywords(keywords)
            .limit(limit)
            .timestamp(ts)
            .path(new byte[0][])
            .signature(new byte[64])
            .build();
    java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
    signer.initSign(requesterKey.getPrivate());
    signer.update(unsigned.canonicalBytes());
    return RemoteSearchRequest.builder()
        .nonce(nonce)
        .requesterPub(requesterPub)
        .keywords(keywords)
        .limit(limit)
        .timestamp(ts)
        .path(new byte[0][])
        .signature(signer.sign())
        .build();
  }

  private static RemoteSearchResponse signResponse(byte[] nonce, long timestamp) throws Exception {
    byte[] infoHash = new byte[20];
    byte[] pub = new byte[32];
    RemoteSearchResponse unsigned =
        RemoteSearchResponse.builder()
            .nonce(nonce)
            .timestamp(timestamp)
            .addRow(infoHash, "ubuntu-22.04", 1000, 1, pub)
            .signature(new byte[64])
            .build();
    java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
    signer.initSign(responderIdentity.ed25519().getPrivate());
    signer.update(unsigned.canonicalBytes());
    return RemoteSearchResponse.builder()
        .nonce(nonce)
        .timestamp(timestamp)
        .addRow(infoHash, "ubuntu-22.04", 1000, 1, pub)
        .signature(signer.sign())
        .build();
  }

  private static IdentityRecord sampleIdentityRecord() throws Exception {
    java.security.KeyPair kp =
        java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    byte[] nodeId = new byte[20];
    byte[] x25519 = new byte[32];
    return IdentityRecord.createSigned(nodeId, kp, x25519, 6881);
  }

  private static LocalSharedTorrent torrent(String name, long size, int fileCount) {
    byte[] hash = new byte[20];
    for (int i = 0; i < 20; i++) hash[i] = (byte) i;
    byte[] pub = new byte[32];
    byte[] nodeId = new byte[20];
    long now = System.currentTimeMillis() / 1000L;
    return new LocalSharedTorrent.Builder()
        .infoHash(hash)
        .name(name)
        .sizeBytes(size)
        .fileCount(fileCount)
        .filesJson("[]")
        .publisherNodeId(nodeId)
        .publisherEd25519Pub(pub)
        .publisherUtpPort(0)
        .addedAt(now)
        .lastSeenAt(now)
        .build();
  }

  private static final class NoopKarmaCache extends PeerKarmaCache {
    NoopKarmaCache() {
      super(
          new RemoteKarmaChainFetcher(
              new KarmaChainSource() {
                @Override
                public Entry fetchManifest(byte[] peerPub) {
                  return null;
                }
              }));
    }
  }

  private static final class NoopLocalIndex implements LocalIndex {
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
      if (query == null || query.isEmpty()) {
        return new ArrayList<>();
      }
      String q = query.toLowerCase();
      List<LocalSharedTorrent> out = new ArrayList<>();
      for (LocalSharedTorrent t : torrents) {
        if (t.name().toLowerCase().contains(q)) {
          out.add(t);
          if (out.size() >= limit) break;
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
