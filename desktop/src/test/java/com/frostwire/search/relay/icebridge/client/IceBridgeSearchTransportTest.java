/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.EmptyLocalIndex;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IndexDigest;
import com.frostwire.search.relay.KarmaChainSource;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.search.relay.RemoteSearchRequest;
import com.frostwire.search.relay.SearchPayloadCodec;
import com.frostwire.search.relay.ShareVisibilityPolicy;
import com.frostwire.search.relay.TorrentMetadataProvider;
import com.frostwire.search.relay.TorrentMetadataRequest;
import com.frostwire.search.relay.TorrentMetadataResponse;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IceBridgeSearchTransportTest {

  @Test
  void blockedRequestWorkersDoNotBlockResponsesAndQueueRemainsBounded() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    IdentityKeys requester = IdentityKeys.generate();
    CountDownLatch entered = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger reads = new AtomicInteger();
    AtomicInteger responses = new AtomicInteger();
    try (Fixture fixture = new Fixture()) {
      IceBridgeSearchTransport transport = fixture.transport;
      IncomingSearchRequestHandler handler =
          new IncomingSearchRequestHandler(
              transport,
              new RelaySearchService(
                  new EmptyLocalIndex(), holder, ShareVisibilityPolicy.INCLUDE_ALL),
              null,
              holder);
      handler.setTorrentMetadataProvider(
          new TorrentMetadataProvider() {
            @Override
            public boolean isPubliclyShared(byte[] hash) {
              return true;
            }

            @Override
            public byte[] torrentBytes(byte[] hash) {
              reads.incrementAndGet();
              entered.countDown();
              try {
                release.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return null;
            }
          });
      handler.start();
      transport.addListener(
          (source, payload, received) -> {
            if (SearchPayloadCodec.decodeTorrentMetadataResponse(payload) != null) {
              responses.incrementAndGet();
            }
          });
      for (int i = 0; i < 2; i++) fixture.offer(request(requester, i));
      fixture.poll();
      assertTrue(entered.await(3, TimeUnit.SECONDS), "both provider workers entered");
      byte[] response =
          SearchPayloadCodec.encodeTorrentMetadataResponse(
              TorrentMetadataResponse.buildError(
                  new byte[32],
                  new byte[20],
                  System.currentTimeMillis() / 1000L,
                  TorrentMetadataResponse.ERR_NOT_FOUND));
      for (int i = 0; i < 100; i++) fixture.offer(response);
      fixture.poll();
      // Response delivery runs on a bounded lane off the poller now; await it while the provider
      // workers remain blocked (isolation, not synchronous delivery, is the property under test).
      long responseDeadline = System.currentTimeMillis() + 3000;
      while (responses.get() < 100 && System.currentTimeMillis() < responseDeadline) {
        Thread.sleep(20);
      }
      assertEquals(100, responses.get(), "responses delivered while providers remain blocked");
      ThreadPoolExecutor workers = fixture.workers();
      assertEquals(0, workers.getQueue().size(), "response frames never occupy request capacity");
      for (int i = 2; i < 82; i++) fixture.offer(request(requester, i));
      fixture.poll();
      assertEquals(64, workers.getQueue().size());
      assertEquals(2, reads.get(), "queued or rejected requests cannot enter providers");
      transport.close();
      release.countDown();
      assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
      assertTrue(workers.getQueue().isEmpty());
      assertEquals(2, reads.get(), "close discards queued requests before provider work");
      assertEquals(0, fixture.sends.get(), "stopped work cannot respond");
    } finally {
      release.countDown();
    }
  }

  @Test
  void demuxAdmitsAnnouncementsAndKeepsRepliesOffTheRequestLane() {
    byte[] json =
        "{\"v\":1,\"k\":\"miami\",\"pub\":\"p\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(IceBridgeSearchTransport.isRequest(json, MeshProtocolId.SEARCH));
    assertFalse(
        IceBridgeSearchTransport.isRequest(
            "{\"v\":1,\"nonce\":\"n\",\"rows\":[],\"sig\":\"s\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            MeshProtocolId.SEARCH));
    assertTrue(
        IceBridgeSearchTransport.isRequest(
            "{\"v\":1,\"ih\":\"ab\",\"pub\":\"p\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            MeshProtocolId.METADATA));
    assertFalse(
        IceBridgeSearchTransport.isRequest(
            "{\"v\":1,\"nonce\":\"n\",\"ih\":\"ab\",\"ts\":1,\"sig\":\"s\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            MeshProtocolId.METADATA));
    assertTrue(IceBridgeSearchTransport.isRequest(new byte[] {1}, MeshProtocolId.TELEMETRY));
    assertTrue(
        IceBridgeSearchTransport.isRequest(
            IndexDigest.build(java.util.List.of("miami")).toBytes(), MeshProtocolId.INDEX_DIGEST));
    assertTrue(
        IceBridgeSearchTransport.isRequest(
            IndexDigest.aggregate(
                    java.util.List.of(IndexDigest.build(java.util.List.of("beatles"))))
                .toBytes(),
            MeshProtocolId.CLUSTER_DIGEST));
    assertTrue(
        IceBridgeSearchTransport.isRequest(
            com.frostwire.search.relay.icebridge.NodeMetaPayload.encode(
                com.frostwire.search.relay.NodeCapabilities.DEFAULT_BOTH),
            MeshProtocolId.NODE_META));
    assertTrue(
        IceBridgeSearchTransport.isRequest(
            "{\"v\":1,\"target\":\"abc\",\"pub\":\"p\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            MeshProtocolId.CATALOG));
    assertFalse(
        IceBridgeSearchTransport.isRequest(
            "{\"v\":1,\"pub\":\"p\",\"rows\":[],\"ts\":1,\"sig\":\"s\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            MeshProtocolId.CATALOG));
    for (int reserved :
        new int[] {
          MeshProtocolId.CHAT, MeshProtocolId.PUBSUB, MeshProtocolId.AI, MeshProtocolId.FILESYNC
        }) {
      assertFalse(
          IceBridgeSearchTransport.isRequest(new byte[] {1}, reserved),
          "reserved id " + reserved + " must not occupy the request lane");
    }
  }

  @Test
  void pollDeliversEveryImplementedAnnouncementToTheHandler() throws Exception {
    IdentityKeys self = IdentityKeys.generate();
    IdentityKeys ping = IdentityKeys.generate();
    IdentityKeys meta = IdentityKeys.generate();
    IdentityKeys holder = IdentityKeys.generate();
    IdentityKeys cluster = IdentityKeys.generate();
    try (Fixture fixture = new Fixture()) {
      PeerDirectory directory = newDirectory();
      directory.upsertVerified(ping.ed25519PubRaw(), "10.0.0.1", 6889);
      directory.upsertVerified(
          meta.ed25519PubRaw(),
          "10.0.0.2",
          6889,
          6889,
          com.frostwire.search.relay.NodeCapabilities.NONE);
      directory.upsertVerified(holder.ed25519PubRaw(), "10.0.0.3", 6889);
      directory.upsertVerified(
          cluster.ed25519PubRaw(),
          "10.0.0.4",
          6889,
          6889,
          com.frostwire.search.relay.NodeCapabilities.RELAY);
      IncomingSearchRequestHandler handler =
          new IncomingSearchRequestHandler(
              fixture.transport,
              new RelaySearchService(
                  new EmptyLocalIndex(), self, ShareVisibilityPolicy.INCLUDE_ALL),
              directory,
              self);
      handler.start();
      fixture.offer(ping.ed25519PubRaw(), new byte[] {0x01}, MeshProtocolId.TELEMETRY);
      fixture.offer(
          meta.ed25519PubRaw(),
          com.frostwire.search.relay.icebridge.NodeMetaPayload.encode(
              com.frostwire.search.relay.NodeCapabilities.DEFAULT_BOTH),
          MeshProtocolId.NODE_META);
      fixture.offer(
          holder.ed25519PubRaw(),
          IndexDigest.build(java.util.List.of("beatles anthology")).toBytes(),
          MeshProtocolId.INDEX_DIGEST);
      fixture.offer(
          cluster.ed25519PubRaw(),
          IndexDigest.aggregate(java.util.List.of(IndexDigest.build(java.util.List.of("beatles"))))
              .toBytes(),
          MeshProtocolId.CLUSTER_DIGEST);
      fixture.poll();
      long deadline = System.currentTimeMillis() + 5_000;
      while (System.currentTimeMillis() < deadline
          && !(directory.isLive(ping.ed25519PubRaw())
              && directory.get(meta.ed25519PubRaw()).orElseThrow().capabilities()
                  == com.frostwire.search.relay.NodeCapabilities.DEFAULT_BOTH
              && directory.hasIndexDigest(holder.ed25519PubRaw())
              && !directory.clusterAllows(
                  cluster.ed25519PubRaw(), java.util.List.of("zzzznotpresent")))) {
        Thread.sleep(10);
      }
      assertTrue(
          directory.isLive(ping.ed25519PubRaw()), "TELEMETRY must mark contact via the poll path");
      assertEquals(
          com.frostwire.search.relay.NodeCapabilities.DEFAULT_BOTH,
          directory.get(meta.ed25519PubRaw()).orElseThrow().capabilities(),
          "NODE_META must be applied via the poll path");
      assertTrue(
          directory.hasIndexDigest(holder.ed25519PubRaw()),
          "INDEX_DIGEST must be stored via the poll path");
      assertTrue(
          directory.clusterAllows(cluster.ed25519PubRaw(), java.util.List.of("beatles")),
          "CLUSTER_DIGEST must be stored via the poll path");
      assertFalse(
          directory.clusterAllows(cluster.ed25519PubRaw(), java.util.List.of("zzzznotpresent")));
    }
  }

  @Test
  void pollAnswersSearchCatalogAndMetadataOnTheirOwnProtocol() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    IdentityKeys requester = IdentityKeys.generate();
    byte[] requesterPub = requester.ed25519PubRaw();
    try (Fixture fixture = new Fixture()) {
      SilentIndex index = new SilentIndex();
      IncomingSearchRequestHandler handler =
          new IncomingSearchRequestHandler(
              fixture.transport,
              new RelaySearchService(index, holder, ShareVisibilityPolicy.INCLUDE_ALL),
              newDirectory(),
              holder,
              index);
      handler.setPublicCatalogEnabled(true);
      handler.start();
      long now = System.currentTimeMillis() / 1000L;
      fixture.offer(requesterPub, signedSearch(requester, now), MeshProtocolId.SEARCH);
      fixture.offer(
          requesterPub,
          signedCatalog(requester, holder.ed25519PubRaw(), now),
          MeshProtocolId.CATALOG);
      fixture.offer(requesterPub, signedMetadata(requester, now), MeshProtocolId.METADATA);
      fixture.poll();
      long deadline = System.currentTimeMillis() + 5_000;
      while (System.currentTimeMillis() < deadline
          && !(fixture.sentProtocols.contains(MeshProtocolId.SEARCH)
              && fixture.sentProtocols.contains(MeshProtocolId.CATALOG)
              && fixture.sentProtocols.contains(MeshProtocolId.METADATA))) {
        Thread.sleep(10);
      }
      assertTrue(
          fixture.sentProtocols.contains(MeshProtocolId.SEARCH),
          "a search request must be answered on SEARCH, got " + fixture.sentProtocols);
      assertTrue(
          fixture.sentProtocols.contains(MeshProtocolId.CATALOG),
          "a catalog request must be answered on CATALOG, got " + fixture.sentProtocols);
      assertTrue(
          fixture.sentProtocols.contains(MeshProtocolId.METADATA),
          "a metadata request must be answered on METADATA, got " + fixture.sentProtocols);
    }
  }

  private static byte[] signedSearch(IdentityKeys requester, long now) throws Exception {
    byte[] pub = requester.ed25519PubRaw();
    RemoteSearchRequest unsigned =
        RemoteSearchRequest.builder()
            .keywords("beatles")
            .limit(5)
            .nonce(new byte[32])
            .ttl(1)
            .requesterPub(pub)
            .path(new byte[][] {pub})
            .timestamp(now)
            .signature(new byte[64])
            .build();
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    signer.initSign(requester.ed25519().getPrivate());
    signer.update(unsigned.canonicalBytes());
    return SearchPayloadCodec.encodeRequest(
        RemoteSearchRequest.builder()
            .keywords("beatles")
            .limit(5)
            .nonce(new byte[32])
            .ttl(1)
            .requesterPub(pub)
            .path(new byte[][] {pub})
            .timestamp(now)
            .signature(signer.sign())
            .build());
  }

  private static byte[] signedCatalog(IdentityKeys requester, byte[] target, long now)
      throws Exception {
    com.frostwire.search.relay.RemoteCatalogBrowseRequest.Builder builder =
        com.frostwire.search.relay.RemoteCatalogBrowseRequest.builder()
            .requesterPub(requester.ed25519PubRaw())
            .targetPub(target)
            .nonce(new byte[32])
            .timestamp(now)
            .signature(new byte[64]);
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    signer.initSign(requester.ed25519().getPrivate());
    signer.update(builder.build().canonicalBytes());
    return SearchPayloadCodec.encodeCatalogBrowseRequest(builder.signature(signer.sign()).build());
  }

  private static byte[] signedMetadata(IdentityKeys requester, long now) throws Exception {
    byte[] hash = new byte[20];
    hash[0] = 7;
    com.frostwire.search.relay.TorrentMetadataRequest.Builder builder =
        com.frostwire.search.relay.TorrentMetadataRequest.builder()
            .infoHash(hash)
            .nonce(new byte[32])
            .requesterPub(requester.ed25519PubRaw())
            .timestamp(now)
            .signature(new byte[64]);
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    signer.initSign(requester.ed25519().getPrivate());
    signer.update(builder.build().canonicalBytes());
    return SearchPayloadCodec.encodeTorrentMetadataRequest(
        builder.signature(signer.sign()).build());
  }

  /** Not an EmptyLocalIndex, so an empty search still produces a signed reply. */
  private static final class SilentIndex implements com.frostwire.search.relay.LocalIndex {
    @Override
    public void upsert(com.frostwire.search.relay.LocalSharedTorrent torrent) {}

    @Override
    public void delete(String infoHashHex) {}

    @Override
    public java.util.Optional<com.frostwire.search.relay.LocalSharedTorrent> get(
        String infoHashHex) {
      return java.util.Optional.empty();
    }

    @Override
    public List<com.frostwire.search.relay.LocalSharedTorrent> search(String query, int limit) {
      return List.of();
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {}

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
      return List.of();
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {}

    @Override
    public int size() {
      return 0;
    }
  }

  @Test
  void closeCancelsOwnedHttpSendWithoutWaitingForResponse() throws Exception {
    java.util.concurrent.ExecutorService sender =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    try (Fixture fixture = new Fixture()) {
      fixture.blockSend = true;
      java.util.concurrent.Future<Boolean> result =
          sender.submit(
              () -> fixture.transport.send(new byte[32], MeshProtocolId.SEARCH, new byte[] {1}));
      assertTrue(fixture.sendStarted.await(3, TimeUnit.SECONDS));
      fixture.transport.close();
      assertFalse(result.get(2, TimeUnit.SECONDS));
      assertEquals(1, fixture.sends.get());
    } finally {
      sender.shutdownNow();
      assertTrue(sender.awaitTermination(3, TimeUnit.SECONDS));
    }
  }

  @Test
  void expiredAndPreviousGenerationWorkCannotEnterProvider() throws Exception {
    IdentityKeys holder = IdentityKeys.generate();
    AtomicInteger reads = new AtomicInteger();
    try (Fixture fixture = new Fixture()) {
      IncomingSearchRequestHandler handler =
          new IncomingSearchRequestHandler(
              fixture.transport,
              new RelaySearchService(
                  new EmptyLocalIndex(), holder, ShareVisibilityPolicy.INCLUDE_ALL),
              null,
              holder);
      handler.setTorrentMetadataProvider(
          new TorrentMetadataProvider() {
            @Override
            public boolean isPubliclyShared(byte[] hash) {
              return true;
            }

            @Override
            public byte[] torrentBytes(byte[] hash) {
              reads.incrementAndGet();
              return null;
            }
          });
      byte[] payload = request(IdentityKeys.generate(), 1);
      long oldGeneration = handler.generation();
      handler.onPayloadBefore(
          new byte[32], payload, MeshProtocolId.METADATA, System.nanoTime() - 1, oldGeneration);
      handler.stop();
      handler.start();
      handler.onPayloadBefore(
          new byte[32],
          payload,
          MeshProtocolId.METADATA,
          System.nanoTime() + TimeUnit.SECONDS.toNanos(1),
          oldGeneration);
      assertEquals(0, reads.get());
      assertEquals(0, fixture.sends.get());
      assertEquals(0, handler.torrentCacheSize());
      assertFalse(
          fixture
              .transport
              .createSend(
                  new byte[32], MeshProtocolId.SEARCH, new byte[] {1}, System.nanoTime() - 1)
              .execute());
      fixture.transport.close();
      assertFalse(fixture.transport.send(new byte[32], MeshProtocolId.SEARCH, new byte[] {1}));
      assertEquals(0, fixture.sends.get());
    }
  }

  private static byte[] request(IdentityKeys requester, int sequence) throws Exception {
    byte[] nonce = new byte[32];
    nonce[0] = (byte) sequence;
    TorrentMetadataRequest.Builder builder =
        TorrentMetadataRequest.builder()
            .infoHash(new byte[20])
            .nonce(nonce)
            .requesterPub(requester.ed25519PubRaw())
            .timestamp(System.currentTimeMillis() / 1000L)
            .signature(new byte[64]);
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    signer.initSign(requester.ed25519().getPrivate());
    signer.update(builder.build().canonicalBytes());
    return SearchPayloadCodec.encodeTorrentMetadataRequest(
        builder.signature(signer.sign()).build());
  }

  private static PeerDirectory newDirectory() {
    return new PeerDirectory(
        new PeerKarmaCache(
            new RemoteKarmaChainFetcher(
                new KarmaChainSource() {
                  @Override
                  public Entry fetchManifest(byte[] peerPub) {
                    return null;
                  }
                })));
  }

  @Test
  void indexDigestFramesAreDeliveredAndStoredForHolderRouting() throws Exception {
    IdentityKeys self = IdentityKeys.generate();
    IdentityKeys sender = IdentityKeys.generate();
    IdentityKeys other = IdentityKeys.generate();
    try (Fixture fixture = new Fixture()) {
      PeerDirectory directory = newDirectory();
      directory.upsertVerified(sender.ed25519PubRaw(), "10.0.0.9", 6889);
      directory.upsertVerified(other.ed25519PubRaw(), "10.0.0.10", 6889);
      IncomingSearchRequestHandler handler =
          new IncomingSearchRequestHandler(
              fixture.transport,
              new RelaySearchService(
                  new EmptyLocalIndex(), self, ShareVisibilityPolicy.INCLUDE_ALL),
              directory,
              self);
      handler.start();

      byte[] digest = IndexDigest.build(List.of("Inglés En Miami (audio).webm")).toBytes();
      fixture.offer(sender.ed25519PubRaw(), digest, MeshProtocolId.INDEX_DIGEST);
      fixture.poll();

      // pollAndDispatch hands the frame to a worker thread; wait for it to be stored.
      long deadline = System.currentTimeMillis() + 5_000;
      while (!directory.hasIndexDigest(sender.ed25519PubRaw())
          && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      assertTrue(directory.hasIndexDigest(sender.ed25519PubRaw()), "digest delivered and stored");
      assertTrue(directory.isLive(sender.ed25519PubRaw()), "inbound frame counts as contact");
      List<PeerDirectory.PeerInfo> holders =
          directory.sampleHolders(
              "miami",
              4,
              java.util.Set.of(),
              com.frostwire.search.relay.NodeCapabilities.NONE,
              new java.util.Random(1),
              1);
      assertFalse(holders.isEmpty());
      assertArrayEquals(
          sender.ed25519PubRaw(),
          holders.get(0).peerPub(),
          "the peer that announced a matching digest is routed to first");
    }
  }

  /** Local control fixture: no UDP, discovery, or external network participation. */
  private static final class Fixture implements AutoCloseable {
    final ConcurrentLinkedQueue<Map<String, Object>> messages = new ConcurrentLinkedQueue<>();
    final AtomicInteger sends = new AtomicInteger();
    final List<Integer> sentProtocols = new CopyOnWriteArrayList<>();
    final CountDownLatch sendStarted = new CountDownLatch(1);
    final CountDownLatch sendRelease = new CountDownLatch(1);
    volatile boolean blockSend;
    final HttpServer server;
    final IceBridgeClient client;
    final IceBridgeSearchTransport transport;

    Fixture() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            List<Map<String, Object>> batch = new ArrayList<>();
            if (exchange.getRequestURI().getPath().equals("/poll")) {
              Map<String, Object> message;
              while (batch.size() < 256 && (message = messages.poll()) != null) batch.add(message);
            } else if (exchange.getRequestURI().getPath().equals("/send")) {
              String posted =
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
              try {
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = new Gson().fromJson(posted, Map.class);
                Object id = fields == null ? null : fields.get("protocolId");
                if (id instanceof Number) {
                  sentProtocols.add(((Number) id).intValue());
                }
              } catch (RuntimeException ignored) {
              }
              sends.incrementAndGet();
              sendStarted.countDown();
              if (blockSend) {
                try {
                  sendRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
            }
            byte[] body =
                new Gson()
                    .toJson(Map.of("ok", true, "data", batch))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
              output.write(body);
            } finally {
              exchange.close();
            }
          });
      server.start();
      client = new IceBridgeClient(server.getAddress().getPort());
      transport = new IceBridgeSearchTransport(client);
    }

    void offer(byte[] sourcePub, byte[] payload, int protocolId) {
      messages.add(
          Map.of(
              "sourcePub",
              Base64.getUrlEncoder().encodeToString(sourcePub),
              "payload",
              Base64.getUrlEncoder().encodeToString(payload),
              "receivedMs",
              System.currentTimeMillis(),
              "protocolId",
              protocolId));
    }

    void offer(byte[] payload) {
      offer(new byte[32], payload, MeshProtocolId.METADATA);
    }

    void poll() throws Exception {
      Method poll = IceBridgeSearchTransport.class.getDeclaredMethod("pollAndDispatch");
      poll.setAccessible(true);
      poll.invoke(transport);
    }

    ThreadPoolExecutor workers() throws Exception {
      Field field = IceBridgeSearchTransport.class.getDeclaredField("requestWorkers");
      field.setAccessible(true);
      return (ThreadPoolExecutor) field.get(transport);
    }

    @Override
    public void close() {
      transport.close();
      client.close();
      sendRelease.countDown();
      server.stop(0);
    }
  }
}
