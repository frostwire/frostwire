/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IncomingSearchRequestHandlerTest {

  private static final AtomicInteger HASH_COUNTER = new AtomicInteger();

  @Test
  void multiHopForwardsPreservingRequesterSignature() throws Exception {
    assertTrue(
        IncomingSearchRequestHandler.MULTI_HOP_FORWARDING_ENABLED,
        "dual-envelope multi-hop must be enabled");

    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);

    IdentityKeys handlerIdentity = IdentityKeys.generate();
    InMemoryLocalIndex index = new InMemoryLocalIndex();
    index.torrents.add(torrent("ubuntu server", 500L, 1));
    RelaySearchService service =
        new RelaySearchService(index, handlerIdentity, ShareVisibilityPolicy.INCLUDE_ALL);

    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
    byte[] peerA = rawPub(generateEd25519KeyPair());
    byte[] peerB = rawPub(generateEd25519KeyPair());
    directory.upsertVerified(peerA, "host-a", 6881);
    directory.upsertVerified(peerB, "host-b", 6882);

    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
    handler.start();

    byte[][] path = {requesterPub};
    RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 2, path);
    transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

    // 1 local response + up to 2 forwards
    assertTrue(
        transport.sent.size() >= 2,
        "local response plus at least one dual-envelope forward; got " + transport.sent.size());
    List<RemoteSearchRequest> forwarded = extractForwardedRequests(transport);
    assertFalse(forwarded.isEmpty(), "expected dual-envelope forwards to directory peers");
    for (RemoteSearchRequest hop : forwarded) {
      assertArrayEquals(
          request.signature(), hop.signature(), "forward must preserve requester signature");
      assertEquals(1, hop.ttl(), "ttl should be decremented by one useful hop");
      assertTrue(hop.pathLength() >= 2, "path should include forwarder pub");
    }
    RemoteSearchResponse response =
        SearchPayloadCodec.decodeResponse(transport.sent.get(0).payload);
    assertNotNull(response);
    assertEquals(1, response.rows().size());
    assertArrayEquals(request.nonce(), response.nonce());
  }

  @Test
  void forwardCapLimitsDualEnvelopeForwards() throws Exception {
    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);

    IdentityKeys handlerIdentity = IdentityKeys.generate();
    InMemoryLocalIndex index = new InMemoryLocalIndex();
    index.torrents.add(torrent("ubuntu server", 500L, 1));
    RelaySearchService service =
        new RelaySearchService(index, handlerIdentity, ShareVisibilityPolicy.INCLUDE_ALL);

    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
    byte[] peerA = rawPub(generateEd25519KeyPair());
    byte[] peerB = rawPub(generateEd25519KeyPair());
    directory.upsertVerified(peerA, "host-a", 6881);
    directory.upsertVerified(peerB, "host-b", 6882);

    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
    handler.setMaxForwardTargets(1);
    handler.start();

    byte[][] path = {requesterPub};
    RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 2, path);
    transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

    assertEquals(2, transport.sent.size(), "local response plus exactly one capped forward");
  }

  @Test
  void dropsRequestWhenTtlZero() throws Exception {
    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);

    IdentityKeys handlerIdentity = IdentityKeys.generate();
    InMemoryLocalIndex index = new InMemoryLocalIndex();
    index.torrents.add(torrent("ubuntu server", 500L, 1));
    RelaySearchService service =
        new RelaySearchService(index, handlerIdentity, ShareVisibilityPolicy.INCLUDE_ALL);

    KeyPair peerKey = generateEd25519KeyPair();
    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
    directory.upsertVerified(rawPub(peerKey), "host", 6881);

    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
    handler.start();

    byte[][] path = {requesterPub};
    RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 0, path);
    transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

    assertEquals(0, transport.sent.size(), "ttl=0 requests are dropped, not answered");
  }

  @Test
  void forwardingDisabledStillAnswersButDoesNotForward() throws Exception {
    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);

    IdentityKeys handlerIdentity = IdentityKeys.generate();
    InMemoryLocalIndex index = new InMemoryLocalIndex();
    index.torrents.add(torrent("ubuntu server", 500L, 1));
    RelaySearchService service =
        new RelaySearchService(index, handlerIdentity, ShareVisibilityPolicy.INCLUDE_ALL);

    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
    byte[] peerA = rawPub(generateEd25519KeyPair());
    byte[] peerB = rawPub(generateEd25519KeyPair());
    directory.upsertVerified(peerA, "host-a", 6881);
    directory.upsertVerified(peerB, "host-b", 6882);

    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
    // CLIENT leaf: answer locally, never forward.
    handler.setForwardingEnabled(false);
    handler.start();

    byte[][] path = {requesterPub};
    RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 1, path);
    transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

    assertEquals(1, transport.sent.size(), "local response only, no dual-envelope forwards");
    RemoteSearchResponse response =
        SearchPayloadCodec.decodeResponse(transport.sent.get(0).payload);
    assertNotNull(response, "the single sent payload must be the response");
    assertArrayEquals(request.nonce(), response.nonce(), "response nonce must match request nonce");
  }

  @Test
  void dualEnvelopeHopPreservesRequesterVerify() throws Exception {
    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);
    IdentityKeys forwarder = IdentityKeys.generate();
    IdentityKeys targetIdentity = IdentityKeys.generate();
    InMemoryLocalIndex index = new InMemoryLocalIndex();
    index.torrents.add(torrent("ubuntu server", 500L, 1));
    RelaySearchService targetService =
        new RelaySearchService(index, targetIdentity, ShareVisibilityPolicy.INCLUDE_ALL);

    byte[][] path = {requesterPub};
    RemoteSearchRequest original = signedRequest(requesterKey, "ubuntu", 25, 2, path);
    RemoteSearchRequest nextHop = original.withNextHop(forwarder.ed25519PubRaw(), 1);

    assertArrayEquals(
        original.signature(), nextHop.signature(), "withNextHop must preserve requester signature");
    assertArrayEquals(
        original.queryCanonicalBytes(),
        nextHop.queryCanonicalBytes(),
        "query envelope unchanged across hop");
    assertTrue(
        verifySignature(nextHop, requesterPub), "hopped request still verifies under requesterPub");
    assertTrue(
        targetService.handle(nextHop).isPresent(),
        "RelaySearchService accepts dual-envelope hop with preserved requester sig");
    assertTrue(
        targetService.handle(original).isEmpty(),
        "second graph arrival is rejected even with a different outer path");
  }

  @Test
  void localResponseStillGoesThroughWhenNoPeersToForward() throws Exception {
    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);

    IdentityKeys handlerIdentity = IdentityKeys.generate();
    InMemoryLocalIndex index = new InMemoryLocalIndex();
    index.torrents.add(torrent("ubuntu server", 500L, 1));
    RelaySearchService service =
        new RelaySearchService(index, handlerIdentity, ShareVisibilityPolicy.INCLUDE_ALL);

    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());

    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
    handler.start();

    byte[][] path = {requesterPub};
    RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 1, path);
    transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

    assertEquals(1, transport.sent.size(), "local response must be sent even with no peers");
    RemoteSearchResponse response =
        SearchPayloadCodec.decodeResponse(transport.sent.get(0).payload);
    assertNotNull(response, "the single sent payload must be the response");
    assertArrayEquals(request.nonce(), response.nonce(), "response nonce must match request nonce");
  }

  @Test
  void torrentPayloadUpTo256KBIsServedInChunks() throws Exception {
    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);

    IdentityKeys holderIdentity = IdentityKeys.generate();
    RelaySearchService service =
        new RelaySearchService(
            new InMemoryLocalIndex(), holderIdentity, ShareVisibilityPolicy.INCLUDE_ALL);
    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());

    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, holderIdentity);

    byte[] infoHash = TestTorrentMetadata.infoHash();
    // 200KB: over the old 64KB cap, under the 256KB torrent cap.
    byte[] torrentBytes = TestTorrentMetadata.bytes(200 * 1024);
    handler.setTorrentMetadataProvider(publicProvider(ih -> torrentBytes));
    handler.start();

    TorrentMetadataRequest request = signedMetadataRequest(requesterKey, infoHash, randomBytes(32));
    handler.onPayload(
        requesterPub,
        SearchPayloadCodec.encodeTorrentMetadataRequest(request),
        System.currentTimeMillis(),
        MeshProtocolId.METADATA);

    int expectedChunks =
        (torrentBytes.length + TorrentMetadataResponse.CHUNK_DATA_BYTES - 1)
            / TorrentMetadataResponse.CHUNK_DATA_BYTES;
    assertEquals(
        expectedChunks,
        transport.sent.size(),
        "200KB torrent must be served as chunks, not TOO_LARGE");
    List<TorrentMetadataResponse> chunks = new ArrayList<>();
    for (CapturingTransport.SentPayload sp : transport.sent) {
      TorrentMetadataResponse chunk = SearchPayloadCodec.decodeTorrentMetadataResponse(sp.payload);
      assertNotNull(chunk);
      assertFalse(chunk.isError(), "no chunk may carry an error");
      assertTrue(
          chunk.verifySignature(holderIdentity.ed25519PubRaw()),
          "chunk must verify under holder pub");
      assertArrayEquals(request.nonce(), chunk.nonce(), "chunk nonce must match request nonce");
      chunks.add(chunk);
    }
    chunks.sort(java.util.Comparator.comparingInt(TorrentMetadataResponse::chunkIndex));
    assertArrayEquals(torrentBytes, TorrentMetadataResponse.assemble(chunks));
    assertEquals(1, handler.torrentCacheSize(), "served payload must be cached");
  }

  @Test
  void torrentOver256KBIsRejectedTooLarge() throws Exception {
    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);

    IdentityKeys holderIdentity = IdentityKeys.generate();
    RelaySearchService service =
        new RelaySearchService(
            new InMemoryLocalIndex(), holderIdentity, ShareVisibilityPolicy.INCLUDE_ALL);
    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());

    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, holderIdentity);
    handler.setTorrentMetadataProvider(publicProvider(ih -> randomBytes(300 * 1024)));
    handler.start();

    TorrentMetadataRequest request =
        signedMetadataRequest(requesterKey, randomBytes(20), randomBytes(32));
    handler.onPayload(
        requesterPub,
        SearchPayloadCodec.encodeTorrentMetadataRequest(request),
        System.currentTimeMillis(),
        MeshProtocolId.METADATA);

    assertEquals(1, transport.sent.size(), "over-cap torrent must yield exactly one error frame");
    TorrentMetadataResponse err =
        SearchPayloadCodec.decodeTorrentMetadataResponse(transport.sent.get(0).payload);
    assertNotNull(err);
    assertTrue(err.isError());
    assertEquals(TorrentMetadataResponse.ERR_TOO_LARGE, err.error());
    assertTrue(err.verifySignature(holderIdentity.ed25519PubRaw()));
  }

  @Test
  void repeatTorrentFetchHitsCacheWithoutProviderRecall() throws Exception {
    KeyPair requesterKey = generateEd25519KeyPair();
    byte[] requesterPub = rawPub(requesterKey);

    IdentityKeys holderIdentity = IdentityKeys.generate();
    RelaySearchService service =
        new RelaySearchService(
            new InMemoryLocalIndex(), holderIdentity, ShareVisibilityPolicy.INCLUDE_ALL);
    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());

    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, holderIdentity);
    byte[] torrentBytes = TestTorrentMetadata.bytes(1024);
    AtomicInteger providerCalls = new AtomicInteger();
    handler.setTorrentMetadataProvider(
        publicProvider(
            ih -> {
              providerCalls.incrementAndGet();
              return torrentBytes;
            }));
    handler.start();

    byte[] infoHash = TestTorrentMetadata.infoHash();
    // Two distinct nonces: signatures are restamped templates, so chunk
    // signatures must be byte-identical across rounds (zero re-signing) while
    // each frame still carries its own request nonce.
    List<byte[]> firstSigs = null;
    for (int round = 0; round < 2; round++) {
      TorrentMetadataRequest request =
          signedMetadataRequest(requesterKey, infoHash, randomBytes(32));
      handler.onPayload(
          requesterPub,
          SearchPayloadCodec.encodeTorrentMetadataRequest(request),
          System.currentTimeMillis(),
          MeshProtocolId.METADATA);
      List<TorrentMetadataResponse> chunks = new ArrayList<>();
      List<byte[]> sigs = new ArrayList<>();
      for (CapturingTransport.SentPayload sp : transport.sent) {
        TorrentMetadataResponse chunk =
            SearchPayloadCodec.decodeTorrentMetadataResponse(sp.payload);
        assertNotNull(chunk);
        assertFalse(chunk.isError());
        assertArrayEquals(request.nonce(), chunk.nonce());
        assertTrue(chunk.verifySignature(holderIdentity.ed25519PubRaw()));
        chunks.add(chunk);
        sigs.add(chunk.signature());
      }
      chunks.sort(java.util.Comparator.comparingInt(TorrentMetadataResponse::chunkIndex));
      assertArrayEquals(torrentBytes, TorrentMetadataResponse.assemble(chunks));
      if (firstSigs == null) {
        firstSigs = sigs;
      } else {
        assertEquals(firstSigs.size(), sigs.size());
        for (int i = 0; i < sigs.size(); i++) {
          assertArrayEquals(firstSigs.get(i), sigs.get(i), "hit must not re-sign chunk " + i);
        }
      }
      transport.sent.clear();
    }
    assertEquals(1, providerCalls.get(), "second fetch must hit the payload cache");
    assertEquals(1, handler.torrentCacheSize());
  }

  @Test
  void rejectedAndRepeatedSearchesNeverForward() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    KeyPair requester = generateEd25519KeyPair();
    PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
    directory.upsertVerified(rawPub(generateEd25519KeyPair()), "peer", 6881);
    RelaySearchService service = new RelaySearchService(new InMemoryLocalIndex(), holder);
    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, directory, holder);
    RemoteSearchRequest valid = signedRequest(requester, "ubuntu", 25, 2, new byte[0][]);
    java.util.Map<String, Object> map = valid.toBencodeableMap();
    map.put("sig", java.util.Base64.getEncoder().encodeToString(new byte[64]));
    RemoteSearchRequest bad = RemoteSearchRequest.fromBencodeableMap(map);
    handler.onPayload(rawPub(requester), SearchPayloadCodec.encodeRequest(bad), 0);
    assertTrue(transport.sent.isEmpty(), "failed verification cannot send or forward");
    assertEquals(0, service.rateLimiter().bucketCount());
    handler.onPayload(rawPub(requester), SearchPayloadCodec.encodeRequest(valid), 0);
    assertEquals(2, transport.sent.size());
    transport.sent.clear();
    handler.onPayload(rawPub(requester), SearchPayloadCodec.encodeRequest(valid), 0);
    assertTrue(transport.sent.isEmpty(), "duplicate graph arrival cannot repeat work");
    RemoteSearchRequest terminal =
        signedRequest(requester, "ubuntu", 25, 1, new byte[0][], randomBytes(32));
    handler.onPayload(rawPub(requester), SearchPayloadCodec.encodeRequest(terminal), 0);
    assertEquals(1, transport.sent.size(), "ttl1 only answers locally");
    assertTrue(extractForwardedRequests(transport).isEmpty());
  }

  @Test
  void defaultDeniedMetadataNeverReadsProviderAndWithdrawalInvalidatesCache() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    KeyPair requester = generateEd25519KeyPair();
    byte[] hash = randomBytes(20);
    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(
            transport,
            new RelaySearchService(
                new InMemoryLocalIndex(), holder, ShareVisibilityPolicy.INCLUDE_ALL),
            null,
            holder);
    AtomicInteger reads = new AtomicInteger();
    handler.setTorrentMetadataProvider(
        ih -> {
          reads.incrementAndGet();
          return new byte[900];
        });
    TorrentMetadataRequest first = signedMetadataRequest(requester, hash, randomBytes(32));
    handler.onPayload(
        rawPub(requester),
        SearchPayloadCodec.encodeTorrentMetadataRequest(first),
        0,
        MeshProtocolId.METADATA);
    assertEquals(0, reads.get());
    assertEquals(
        TorrentMetadataResponse.ERR_NOT_FOUND,
        SearchPayloadCodec.decodeTorrentMetadataResponse(transport.sent.get(0).payload).error());
    java.util.concurrent.atomic.AtomicBoolean visible =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    handler.setTorrentMetadataProvider(
        new TorrentMetadataProvider() {
          @Override
          public boolean isPubliclyShared(byte[] ih) {
            return visible.get();
          }

          @Override
          public byte[] torrentBytes(byte[] ih) {
            reads.incrementAndGet();
            return new byte[900];
          }
        });
    transport.sent.clear();
    TorrentMetadataRequest publicRequest = signedMetadataRequest(requester, hash, randomBytes(32));
    handler.onPayload(
        rawPub(requester),
        SearchPayloadCodec.encodeTorrentMetadataRequest(publicRequest),
        0,
        MeshProtocolId.METADATA);
    assertEquals(1, reads.get());
    assertEquals(1, handler.torrentCacheSize());
    visible.set(
        false); // Covers policy withdrawal for removed/private/unshared/metadata-only states.
    transport.sent.clear();
    TorrentMetadataRequest withdrawn = signedMetadataRequest(requester, hash, randomBytes(32));
    handler.onPayload(
        rawPub(requester),
        SearchPayloadCodec.encodeTorrentMetadataRequest(withdrawn),
        0,
        MeshProtocolId.METADATA);
    assertEquals(1, reads.get(), "denial precedes both provider and cached bytes");
    assertEquals(0, handler.torrentCacheSize());
    assertEquals(1, transport.sent.size());
    assertEquals(
        TorrentMetadataResponse.ERR_NOT_FOUND,
        SearchPayloadCodec.decodeTorrentMetadataResponse(transport.sent.get(0).payload).error());
    transport.sent.clear();
    handler.onPayload(
        rawPub(requester),
        SearchPayloadCodec.encodeTorrentMetadataRequest(withdrawn),
        0,
        MeshProtocolId.METADATA);
    assertTrue(transport.sent.isEmpty(), "metadata replay does not re-serve even errors");
  }

  @Test
  void streamingPreservesSeederEndpoints() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    KeyPair requester = generateEd25519KeyPair();
    InMemoryLocalIndex index = new InMemoryLocalIndex();
    for (int i = 0; i < 25; i++) index.torrents.add(torrent("ubuntu " + i, 100, 1));
    RelaySearchService service =
        new RelaySearchService(index, holder, ShareVisibilityPolicy.INCLUDE_ALL);
    service.setSeederEndpointProvider(() -> List.of("127.0.0.1:6881"));
    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, null, holder, index);
    RemoteSearchRequest request = signedRequest(requester, "ubuntu", 25, 1, new byte[0][]);
    handler.onPayload(rawPub(requester), SearchPayloadCodec.encodeRequest(request), 0);
    assertEquals(5, transport.sent.size());
    int rows = 0;
    for (CapturingTransport.SentPayload sent : transport.sent) {
      RemoteSearchResponse response = SearchPayloadCodec.decodeResponse(sent.payload);
      for (RemoteSearchResponse.Row row : response.rows()) {
        assertEquals(List.of("127.0.0.1:6881"), row.seederEndpoints);
        rows++;
      }
    }
    assertEquals(25, rows);
  }

  @Test
  void browseUsesSamePublicPolicyAsSearchAndStopsAfterWithdrawal() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    KeyPair requester = generateEd25519KeyPair();
    InMemoryLocalIndex index = new InMemoryLocalIndex();
    LocalSharedTorrent visibleRow = torrent("public-name", 100, 1);
    index.torrents.add(visibleRow);
    index.torrents.add(torrent("hidden-name", 100, 1));
    java.util.Set<String> visible = new java.util.HashSet<>();
    visible.add(visibleRow.infoHashHex());
    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(
            transport,
            new RelaySearchService(index, holder, visible::contains),
            null,
            holder,
            index);
    for (int round = 0; round < 2; round++) {
      RemoteCatalogBrowseRequest.Builder builder =
          RemoteCatalogBrowseRequest.builder()
              .requesterPub(rawPub(requester))
              .targetPub(holder.ed25519PubRaw())
              .nonce(randomBytes(32))
              .timestamp(System.currentTimeMillis() / 1000)
              .signature(new byte[64]);
      Signature signer = Signature.getInstance("Ed25519");
      signer.initSign(requester.getPrivate());
      signer.update(builder.build().canonicalBytes());
      handler.onPayload(
          rawPub(requester),
          SearchPayloadCodec.encodeCatalogBrowseRequest(builder.signature(signer.sign()).build()),
          0);
      assertEquals(1, transport.sent.size());
      String manifest =
          new String(transport.sent.get(0).payload, java.nio.charset.StandardCharsets.UTF_8);
      assertFalse(manifest.contains("hidden-name"));
      assertEquals(round == 0, manifest.contains("public-name"));
      transport.sent.clear();
      visible.clear();
    }
  }

  // --- helpers ---

  @Test
  void providerPermissionAloneDoesNotOverrideDefaultDeniedHandler() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    KeyPair requester = generateEd25519KeyPair();
    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(
            transport, new RelaySearchService(new InMemoryLocalIndex(), holder), null, holder);
    AtomicInteger reads = new AtomicInteger();
    handler.setTorrentMetadataProvider(
        publicProvider(
            hash -> {
              reads.incrementAndGet();
              return new byte[100];
            }));
    TorrentMetadataRequest request =
        signedMetadataRequest(requester, randomBytes(20), randomBytes(32));
    handler.onPayload(
        rawPub(requester),
        SearchPayloadCodec.encodeTorrentMetadataRequest(request),
        0,
        MeshProtocolId.METADATA);
    assertEquals(0, reads.get());
    assertEquals(0, handler.torrentCacheSize());
    assertEquals(
        TorrentMetadataResponse.ERR_NOT_FOUND,
        SearchPayloadCodec.decodeTorrentMetadataResponse(transport.sent.get(0).payload).error());
  }

  @Test
  void negativeResponsesAreFreshlySignedAndNeverCached() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    KeyPair requester = generateEd25519KeyPair();
    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(
            transport,
            new RelaySearchService(
                new InMemoryLocalIndex(), holder, ShareVisibilityPolicy.INCLUDE_ALL),
            null,
            holder);
    AtomicInteger reads = new AtomicInteger();
    handler.setTorrentMetadataProvider(
        publicProvider(
            hash -> {
              reads.incrementAndGet();
              return new byte[IncomingSearchRequestHandler.METADATA_MAX_BYTES + 1];
            }));
    byte[] hash = randomBytes(20);
    byte[] previousSignature = null;
    for (int i = 0; i < 2; i++) {
      TorrentMetadataRequest request = signedMetadataRequest(requester, hash, randomBytes(32));
      handler.onPayload(
          rawPub(requester),
          SearchPayloadCodec.encodeTorrentMetadataRequest(request),
          0,
          MeshProtocolId.METADATA);
      TorrentMetadataResponse response =
          SearchPayloadCodec.decodeTorrentMetadataResponse(transport.sent.get(i).payload);
      assertEquals(TorrentMetadataResponse.ERR_TOO_LARGE, response.error());
      assertArrayEquals(request.nonce(), response.nonce());
      assertTrue(response.verifySignature(holder.ed25519PubRaw()));
      if (previousSignature != null) {
        assertFalse(Arrays.equals(previousSignature, response.signature()));
      }
      previousSignature = response.signature();
      assertEquals(0, handler.torrentCacheSize());
    }
    assertEquals(2, reads.get(), "negative templates must not be reused");
  }

  @Test
  void providerReplacementDuringReadCannotPopulateOrServeOldCache() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    KeyPair requester = generateEd25519KeyPair();
    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(
            transport,
            new RelaySearchService(
                new InMemoryLocalIndex(), holder, ShareVisibilityPolicy.INCLUDE_ALL),
            null,
            holder);
    AtomicInteger newReads = new AtomicInteger();
    TorrentMetadataProvider replacement =
        publicProvider(
            hash -> {
              newReads.incrementAndGet();
              return TestTorrentMetadata.bytes(10);
            });
    handler.setTorrentMetadataProvider(
        publicProvider(
            hash -> {
              handler.setTorrentMetadataProvider(replacement);
              return TestTorrentMetadata.bytes(100);
            }));
    TorrentMetadataRequest old =
        signedMetadataRequest(requester, TestTorrentMetadata.infoHash(), randomBytes(32));
    handler.onPayload(
        rawPub(requester),
        SearchPayloadCodec.encodeTorrentMetadataRequest(old),
        0,
        MeshProtocolId.METADATA);
    assertEquals(0, handler.torrentCacheSize());
    assertTrue(transport.sent.isEmpty());
    TorrentMetadataRequest current =
        signedMetadataRequest(requester, TestTorrentMetadata.infoHash(), randomBytes(32));
    handler.onPayload(
        rawPub(requester),
        SearchPayloadCodec.encodeTorrentMetadataRequest(current),
        0,
        MeshProtocolId.METADATA);
    assertEquals(1, newReads.get());
    assertEquals(1, handler.torrentCacheSize());
    assertArrayEquals(
        TestTorrentMetadata.bytes(10),
        SearchPayloadCodec.decodeTorrentMetadataResponse(transport.sent.get(0).payload).data());
  }

  @Test
  void metadataSendRejectionAndWithdrawalStopRemainingChunks() throws Exception {
    for (boolean withdraw : new boolean[] {false, true}) {
      IdentityKeys holder = IdentityKeys.generate();
      KeyPair requester = generateEd25519KeyPair();
      CapturingTransport transport = new CapturingTransport();
      java.util.concurrent.atomic.AtomicBoolean visible =
          new java.util.concurrent.atomic.AtomicBoolean(true);
      IncomingSearchRequestHandler handler =
          new IncomingSearchRequestHandler(
              transport,
              new RelaySearchService(new InMemoryLocalIndex(), holder, hash -> visible.get()),
              null,
              holder);
      handler.setTorrentMetadataProvider(publicProvider(hash -> new byte[4096]));
      transport.acceptSend = withdraw;
      transport.afterSend =
          () -> {
            if (withdraw) visible.set(false);
          };
      TorrentMetadataRequest request =
          signedMetadataRequest(requester, randomBytes(20), randomBytes(32));
      handler.onPayload(
          rawPub(requester),
          SearchPayloadCodec.encodeTorrentMetadataRequest(request),
          0,
          MeshProtocolId.METADATA);
      assertEquals(1, transport.sent.size(), "no chunk after rejection or visibility withdrawal");
      if (withdraw) assertEquals(0, handler.torrentCacheSize());
    }
  }

  private static TorrentMetadataProvider publicProvider(TorrentMetadataProvider bytes) {
    return new TorrentMetadataProvider() {
      @Override
      public boolean isPubliclyShared(byte[] hash) {
        return true;
      }

      @Override
      public byte[] torrentBytes(byte[] hash) {
        return bytes.torrentBytes(hash);
      }
    };
  }

  private static byte[] randomBytes(int n) {
    byte[] b = new byte[n];
    java.security.SecureRandom rng = new java.security.SecureRandom();
    // BC DRBG caps a single nextBytes at 262144 bits (32768 bytes).
    byte[] chunk = new byte[Math.min(n, 32768)];
    for (int off = 0; off < n; ) {
      rng.nextBytes(chunk);
      int len = Math.min(chunk.length, n - off);
      System.arraycopy(chunk, 0, b, off, len);
      off += len;
    }
    return b;
  }

  private static TorrentMetadataRequest signedMetadataRequest(
      KeyPair requesterKey, byte[] infoHash, byte[] nonce) throws Exception {
    byte[] requesterPub = rawPub(requesterKey);
    long ts = System.currentTimeMillis() / 1000L;
    TorrentMetadataRequest unsigned =
        TorrentMetadataRequest.builder()
            .infoHash(infoHash)
            .nonce(nonce)
            .requesterPub(requesterPub)
            .timestamp(ts)
            .signature(new byte[64])
            .build();
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(requesterKey.getPrivate());
    signer.update(unsigned.canonicalBytes());
    byte[] sig = signer.sign();
    return TorrentMetadataRequest.builder()
        .infoHash(infoHash)
        .nonce(nonce)
        .requesterPub(requesterPub)
        .timestamp(ts)
        .signature(sig)
        .build();
  }

  private static KeyPair generateEd25519KeyPair() throws Exception {
    return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  private static byte[] rawPub(KeyPair kp) {
    return IdentityRecord.extractRawEd25519(kp.getPublic());
  }

  private static RemoteSearchRequest signedRequest(
      KeyPair requesterKey, String keywords, int limit, int ttl, byte[][] path) throws Exception {
    return signedRequest(requesterKey, keywords, limit, ttl, path, new byte[32]);
  }

  private static RemoteSearchRequest signedRequest(
      KeyPair requesterKey, String keywords, int limit, int ttl, byte[][] path, byte[] nonce)
      throws Exception {
    byte[] requesterPub = rawPub(requesterKey);
    long ts = System.currentTimeMillis() / 1000L;
    RemoteSearchRequest unsigned =
        RemoteSearchRequest.builder()
            .keywords(keywords)
            .limit(limit)
            .nonce(nonce)
            .ttl(ttl)
            .requesterPub(requesterPub)
            .path(path)
            .timestamp(ts)
            .signature(new byte[64])
            .build();
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(requesterKey.getPrivate());
    signer.update(unsigned.canonicalBytes());
    byte[] sig = signer.sign();
    return RemoteSearchRequest.builder()
        .keywords(keywords)
        .limit(limit)
        .nonce(nonce)
        .ttl(ttl)
        .requesterPub(requesterPub)
        .path(path)
        .timestamp(ts)
        .signature(sig)
        .build();
  }

  private static boolean verifySignature(RemoteSearchRequest request, byte[] expectedPubRaw)
      throws Exception {
    PublicKey pub = SearchResponseVerifier.rawEd25519ToPublicKey(expectedPubRaw);
    Signature verifier = Signature.getInstance("Ed25519");
    verifier.initVerify(pub);
    verifier.update(request.canonicalBytes());
    return verifier.verify(request.signature());
  }

  private static List<RemoteSearchRequest> extractForwardedRequests(CapturingTransport transport) {
    List<RemoteSearchRequest> out = new ArrayList<>();
    for (CapturingTransport.SentPayload sp : transport.sent) {
      RemoteSearchRequest req = SearchPayloadCodec.decodeRequest(sp.payload);
      if (req != null) {
        out.add(req);
      }
    }
    return out;
  }

  private static CapturingTransport.SentPayload findForwardTo(
      CapturingTransport transport, byte[] expectedTarget) {
    for (CapturingTransport.SentPayload sp : transport.sent) {
      if (Arrays.equals(sp.targetPub, expectedTarget)) {
        RemoteSearchRequest req = SearchPayloadCodec.decodeRequest(sp.payload);
        if (req != null) {
          return sp;
        }
      }
    }
    return null;
  }

  private static LocalSharedTorrent torrent(String name, long size, int fileCount) {
    byte[] hash = new byte[20];
    int n = HASH_COUNTER.incrementAndGet();
    hash[0] = (byte) (n >>> 24);
    hash[1] = (byte) (n >>> 16);
    hash[2] = (byte) (n >>> 8);
    hash[3] = (byte) n;
    byte[] nodeId = new byte[20];
    byte[] pub = new byte[32];
    pub[31] = (byte) n;
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

  private static final class CapturingTransport implements DistributedSearchTransport {
    private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    final List<SentPayload> sent = new CopyOnWriteArrayList<>();
    boolean acceptSend = true;
    Runnable afterSend = () -> {};

    void deliver(byte[] sourcePub, byte[] payload) {
      for (PayloadListener l : listeners) {
        l.onPayload(sourcePub, payload, System.currentTimeMillis());
      }
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      sent.add(new SentPayload(targetPub, payload));
      afterSend.run();
      return acceptSend;
    }

    @Override
    public void addListener(PayloadListener listener) {
      listeners.add(listener);
    }

    @Override
    public void removeListener(PayloadListener listener) {
      listeners.remove(listener);
    }

    static final class SentPayload {
      final byte[] targetPub;
      final byte[] payload;

      SentPayload(byte[] targetPub, byte[] payload) {
        this.targetPub = targetPub.clone();
        this.payload = payload.clone();
      }
    }
  }

  private static final class InMemoryLocalIndex implements LocalIndex {
    final List<LocalSharedTorrent> torrents = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void upsert(LocalSharedTorrent torrent) {
      torrents.removeIf(r -> r.infoHashHex().equals(torrent.infoHashHex()));
      torrents.add(torrent);
    }

    @Override
    public void delete(String infoHashHex) {
      torrents.removeIf(r -> r.infoHashHex().equalsIgnoreCase(infoHashHex));
    }

    @Override
    public Optional<LocalSharedTorrent> get(String infoHashHex) {
      for (LocalSharedTorrent r : torrents) {
        if (r.infoHashHex().equalsIgnoreCase(infoHashHex)) {
          return Optional.of(r);
        }
      }
      return Optional.empty();
    }

    @Override
    public List<LocalSharedTorrent> search(String query, int limit) {
      if (query == null || query.isEmpty()) {
        return Collections.emptyList();
      }
      String q = query.toLowerCase();
      List<LocalSharedTorrent> out = new ArrayList<>();
      for (LocalSharedTorrent r : torrents) {
        if (r.name().toLowerCase().contains(q)) {
          out.add(r);
          if (out.size() >= limit) break;
        }
      }
      return out;
    }

    @Override
    public List<LocalSharedTorrent> listAll() {
      return new ArrayList<>(torrents);
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {}

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
      return Collections.emptyList();
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {}

    @Override
    public int size() {
      return torrents.size();
    }
  }

  private static final class NoOpKarmaCache extends PeerKarmaCache {
    NoOpKarmaCache() {
      super(
          new RemoteKarmaChainFetcher(
              new KarmaChainSource() {
                @Override
                public Entry fetchManifest(byte[] peerPub) {
                  return null;
                }
              }));
    }

    @Override
    public long getKarma(byte[] peerPub) {
      return 0;
    }
  }
}
