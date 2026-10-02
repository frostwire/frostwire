/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import com.frostwire.search.relay.icebridge.client.PeerRegistrySync;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.LocalSearchEngineWire;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Signature;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedTorrentDigestInvalidationTest {

  private static final String NAME = "El General - Te Ves Buena (audio).webm";

  @TempDir Path tempDir;

  @Test
  void createdTorrentBecomesHolderAndAnswersSignedQueryWithoutPeriodicSync() throws Exception {
    IdentityKeys desktop = IdentityKeys.generate(0);
    IdentityKeys relay = IdentityKeys.generate(0);
    PeerDirectory relayDirectory = directory();
    relayDirectory.upsertVerified(
        desktop.ed25519PubRaw(), "127.0.0.1", 6888, 6889, NodeCapabilities.DEFAULT_PEER);
    for (int i = 1; i <= 40; i++) {
      byte[] other = new byte[32];
      other[0] = (byte) i;
      relayDirectory.upsertVerified(other, "127.0.0.1", 6888, 6889, NodeCapabilities.DEFAULT_PEER);
      relayDirectory.setIndexDigest(other, IndexDigest.build(List.of("unrelated")).toBytes());
    }
    CapturingTransport relayTransport = new CapturingTransport();
    RelaySearchService relayService =
        new RelaySearchService(new InMemoryIndex(), relay, ShareVisibilityPolicy.INCLUDE_ALL);
    IncomingSearchRequestHandler relayHandler =
        new IncomingSearchRequestHandler(relayTransport, relayService, relayDirectory, relay);
    try (AutoCloseable stopRelayHandler = relayHandler::stop;
        LocalIndexTable index = LocalIndexTable.open(tempDir.resolve("index.db").toFile());
        DigestControl control = new DigestControl(desktop.ed25519PubRaw(), relayTransport)) {
      relayHandler.start();
      relayHandler.setMaxForwardTargets(1);
      PeerDirectory desktopDirectory = directory();
      desktopDirectory.upsertVerified(
          relay.ed25519PubRaw(), "127.0.0.1", 6888, 6889, NodeCapabilities.DEFAULT_FORWARDER);
      AtomicBoolean active = new AtomicBoolean(true);
      try (PeerRegistrySync sync =
          new PeerRegistrySync(
              control.client,
              desktopDirectory,
              "127.0.0.1",
              6889,
              desktop,
              IceBridgeConfig.Role.CLIENT,
              index,
              active::get)) {
        SharedTorrentIndexer producer =
            new SharedTorrentIndexer(index, desktop, LocalSearchEngineWire::indexChanged);
        assertEquals(
            IndexResult.UPSERTED,
            producer.indexTorrentInfo(torrentInfo("older-keyword.webm"), "older-keyword.webm"));
        try (AutoCloseable binding = LocalSearchEngineWire.bindIndexDigest(sync, active::get)) {
          // Binding's startup rebuild must finish before the new write, or it could mask a
          // missing producer callback. No periodic sync is started anywhere in this test.
          await(() -> control.digests.size() == 1);
          assertEquals(1, relayDirectory.matchingHolders("older", 8, Set.of()).size());
          assertTrue(relayDirectory.matchingHolders("general", 8, Set.of()).isEmpty());

          TorrentInfo created = torrentInfo(NAME);
          assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(created, NAME));
          assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(created, NAME));
          assertEquals(NAME, index.search("general", 10).get(0).name());
          await(
              () ->
                  control.digests.size() == 2
                      && relayDirectory.matchingHolders("general", 8, Set.of()).size() == 1);
          assertEquals(
              2, control.digests.size(), "producer must force a fresh digest within seconds");
          byte[] holder = relayDirectory.matchingHolders("general", 8, Set.of()).get(0).peerPub();
          assertArrayEquals(desktop.ed25519PubRaw(), holder);

          CapturingTransport desktopTransport = new CapturingTransport();
          RelaySearchService desktopService =
              new RelaySearchService(index, desktop, ShareVisibilityPolicy.INCLUDE_ALL);
          IncomingSearchRequestHandler desktopHandler =
              new IncomingSearchRequestHandler(
                  desktopTransport, desktopService, directory(), desktop);
          try (AutoCloseable stopDesktopHandler = desktopHandler::stop) {
            desktopHandler.setForwardingEnabled(false);
            desktopHandler.setPublicCatalogEnabled(false);
            desktopHandler.start();
            relayTransport.routePub = holder;
            relayTransport.routeSource = relay.ed25519PubRaw();
            relayTransport.routeTransport = desktopTransport;
            RemoteSearchRequest request = signedRequest(IdentityKeys.generate(0));
            relayTransport.deliver(
                request.requesterPub(),
                SearchPayloadCodec.encodeRequest(request),
                MeshProtocolId.SEARCH);
            assertEquals(
                2, relayTransport.sent.size(), "relay local reply plus one holder forward");
            assertArrayEquals(holder, relayTransport.targets.get(1));
            RemoteSearchRequest forwarded =
                SearchPayloadCodec.decodeRequest(relayTransport.sent.get(1));
            assertNotNull(forwarded);
            assertArrayEquals(request.signature(), forwarded.signature());
            assertEquals(1, desktopTransport.sent.size());
            RemoteSearchResponse response =
                SearchPayloadCodec.decodeResponse(desktopTransport.sent.get(0));
            assertTrue(SearchResponseVerifier.verify(response, request, holder));
            assertEquals(1, response.rows().size());
            assertEquals(NAME, response.rows().get(0).name);
            assertFalse(response.rows().get(0).publicCatalog);
            assertFalse(
                desktopService.isPublicCatalog(), "normal queries do not require catalog consent");
          }

          producer.indexExisting(List.of());
          assertEquals(0, index.size());
          await(() -> control.digests.size() == 3);
          assertTrue(relayDirectory.matchingHolders("general", 8, Set.of()).isEmpty());
          assertTrue(relayDirectory.matchingHolders("older", 8, Set.of()).isEmpty());

          active.set(false);
          assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(created, NAME));
          assertEquals(3, control.digests.size());
        }
      }
    }
  }

  @Test
  void writeDuringDigestSendSchedulesAnotherProducerDrivenSnapshot() throws Exception {
    IdentityKeys desktop = IdentityKeys.generate(0);
    IdentityKeys relay = IdentityKeys.generate(0);
    PeerDirectory relayDirectory = directory();
    CapturingTransport transport = new CapturingTransport();
    transport.addListener(
        (source, payload, received) -> relayDirectory.setIndexDigest(source, payload));
    PeerDirectory desktopDirectory = directory();
    desktopDirectory.upsertVerified(
        relay.ed25519PubRaw(), "127.0.0.1", 6888, 6889, NodeCapabilities.DEFAULT_FORWARDER);
    InMemoryIndex index = new InMemoryIndex();
    try (DigestControl control = new DigestControl(desktop.ed25519PubRaw(), transport);
        PeerRegistrySync sync =
            new PeerRegistrySync(
                control.client,
                desktopDirectory,
                "127.0.0.1",
                6889,
                desktop,
                IceBridgeConfig.Role.CLIENT,
                index)) {
      SharedTorrentIndexer producer =
          new SharedTorrentIndexer(index, desktop, sync::announceIndexDigestSoon);
      sync.announceIndexDigestSoon();
      await(() -> control.digests.size() == 1);
      control.blockSend = true;
      assertEquals(
          IndexResult.UPSERTED, producer.indexTorrentInfo(torrentInfo("older.webm"), "older.webm"));
      try {
        assertTrue(control.sendStarted.await(5, TimeUnit.SECONDS));
        assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(torrentInfo(NAME), NAME));
      } finally {
        control.sendRelease.countDown();
      }
      await(() -> control.digests.size() == 3);
      assertEquals(1, relayDirectory.matchingHolders("general", 8, Set.of()).size());
    }
  }

  @Test
  void producerNotifiesOnlyAfterSuccessfulWritesAndListenerFailureDoesNotUndoCommit() {
    InMemoryIndex index = new InMemoryIndex();
    AtomicInteger notifications = new AtomicInteger();
    AtomicBoolean committedAtNotification = new AtomicBoolean();
    SharedTorrentIndexer producer =
        new SharedTorrentIndexer(
            index,
            null,
            () -> {
              committedAtNotification.set(index.size() == 1);
              notifications.incrementAndGet();
              throw new IllegalStateException("notification failed");
            });
    assertEquals(IndexResult.NULL_INPUT, producer.indexTorrentInfo(null, NAME));
    assertEquals(0, notifications.get());
    index.failWrites = true;
    assertEquals(IndexResult.ERROR, producer.indexTorrentInfo(torrentInfo(NAME), NAME));
    assertEquals(0, notifications.get());
    index.failWrites = false;
    assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(torrentInfo(NAME), NAME));
    assertTrue(committedAtNotification.get());
    assertEquals(1, notifications.get());
    index.failWrites = true;
    producer.indexExisting(List.of());
    assertEquals(1, notifications.get(), "failed deletion must not notify");
    index.failWrites = false;
    producer.indexExisting(List.of());
    assertEquals(2, notifications.get());
    producer.indexExisting(List.of());
    assertEquals(2, notifications.get(), "no rows pruned means no change");
  }

  private static TorrentInfo torrentInfo(String name) {
    // Real native, one-byte, single-file v1 torrent; no session or disk hashing required.
    byte[] bytes =
        ("d4:infod6:lengthi1e4:name"
                + name.length()
                + ":"
                + name
                + "12:piece lengthi16384e6:pieces20:xxxxxxxxxxxxxxxxxxxxee")
            .getBytes(StandardCharsets.US_ASCII);
    return TorrentInfo.bdecode(bytes);
  }

  private static PeerDirectory directory() {
    return new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null)));
  }

  private static void await(BooleanSupplier condition) {
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          while (!condition.getAsBoolean()) Thread.sleep(10);
        });
  }

  private static RemoteSearchRequest signedRequest(IdentityKeys requester) throws Exception {
    long now = System.currentTimeMillis() / 1000L;
    RemoteSearchRequest.Builder builder =
        RemoteSearchRequest.builder()
            .keywords("general")
            .limit(10)
            .nonce(new byte[32])
            .ttl(2)
            .requesterPub(requester.ed25519PubRaw())
            .timestamp(now)
            .signature(new byte[64]);
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    signer.initSign(requester.ed25519().getPrivate());
    signer.update(builder.build().canonicalBytes());
    return builder.signature(signer.sign()).build();
  }

  /** Captures the real client's HTTP send and delivers its payload through the relay handler. */
  private static final class DigestControl implements AutoCloseable {
    final HttpServer server;
    final IceBridgeClient client;
    final List<byte[]> digests = new CopyOnWriteArrayList<>();
    final CountDownLatch sendStarted = new CountDownLatch(1);
    final CountDownLatch sendRelease = new CountDownLatch(1);
    volatile boolean blockSend;

    DigestControl(byte[] source, CapturingTransport relayTransport) throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/send",
          exchange -> {
            JsonObject json =
                new Gson()
                    .fromJson(
                        new String(
                            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                        JsonObject.class);
            int protocol = json.get("protocolId").getAsInt();
            byte[] payload = Base64.getUrlDecoder().decode(json.get("payload").getAsString());
            if (blockSend) {
              blockSend = false;
              sendStarted.countDown();
              try {
                sendRelease.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            relayTransport.deliver(source, payload, protocol);
            if (protocol == MeshProtocolId.INDEX_DIGEST) digests.add(payload);
            byte[] reply = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) {
              out.write(reply);
            } finally {
              exchange.close();
            }
          });
      server.start();
      client = new IceBridgeClient(server.getAddress().getPort());
    }

    @Override
    public void close() {
      client.close();
      server.stop(0);
    }
  }

  private static final class CapturingTransport implements DistributedSearchTransport {
    final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    final List<byte[]> sent = new CopyOnWriteArrayList<>();
    final List<byte[]> targets = new CopyOnWriteArrayList<>();
    byte[] routePub;
    byte[] routeSource;
    CapturingTransport routeTransport;

    public boolean send(byte[] target, int protocol, byte[] payload) {
      sent.add(payload.clone());
      targets.add(target.clone());
      if (routeTransport != null && Arrays.equals(target, routePub)) {
        routeTransport.deliver(routeSource, payload, protocol);
      }
      return true;
    }

    public void addListener(PayloadListener listener) {
      listeners.add(listener);
    }

    public void removeListener(PayloadListener listener) {
      listeners.remove(listener);
    }

    void deliver(byte[] source, byte[] payload, int protocol) {
      for (PayloadListener listener : listeners) {
        listener.onPayload(source, payload, System.currentTimeMillis(), protocol);
      }
    }
  }

  private static final class InMemoryIndex implements LocalIndex {
    final List<LocalSharedTorrent> rows = new CopyOnWriteArrayList<>();
    boolean failWrites;

    public void upsert(LocalSharedTorrent row) {
      if (failWrites) throw new IllegalStateException("write failed");
      rows.removeIf(old -> old.infoHashHex().equals(row.infoHashHex()));
      rows.add(row);
    }

    public void delete(String hash) {
      if (failWrites) throw new IllegalStateException("delete failed");
      rows.removeIf(row -> row.infoHashHex().equals(hash));
    }

    public Optional<LocalSharedTorrent> get(String hash) {
      return rows.stream().filter(row -> row.infoHashHex().equals(hash)).findFirst();
    }

    public List<LocalSharedTorrent> search(String query, int limit) {
      return rows.stream().filter(row -> row.name().contains(query)).limit(limit).toList();
    }

    public List<LocalSharedTorrent> listAll() {
      return List.copyOf(rows);
    }

    public void markPublished(String hash, long timestamp) {}

    public List<String> needsRepublish(long now, long threshold) {
      return List.of();
    }

    public void updateLastSeen(String hash, long timestamp) {}

    public int size() {
      return rows.size();
    }
  }
}
