/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.control.PeerInfo;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IceBridgeClientTest {

  private IdentityKeys identity;
  private PeerRegistry registry;
  private IceBridgeMetrics metrics;
  private RudpSessionManager rudpSessionManager;
  private InboundMessageQueue inboundQueue;
  private ControlServer server;
  private IceBridgeClient client;
  private String authToken;

  @BeforeEach
  void startServer() throws Exception {
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
    inboundQueue = new InboundMessageQueue();
    rudpSessionManager = new RudpSessionManager(identity, registry, metrics, inboundQueue);
    byte[] tokenBytes = new byte[32];
    new java.security.SecureRandom().nextBytes(tokenBytes);
    authToken = com.frostwire.util.Hex.encode(tokenBytes);
    java.io.File tmpTokens = java.io.File.createTempFile("ice-client-test-tokens-", ".txt");
    tmpTokens.deleteOnExit();
    IceBridgeTokens tokens = new IceBridgeTokens(tmpTokens);
    tokens.addRuntimeToken(authToken);
    server = new ControlServer(registry, metrics, config, rudpSessionManager, inboundQueue, tokens);
    server.start();
    client = new IceBridgeClient(server.port());
    client.setAuthToken(authToken);
  }

  @AfterEach
  void stopServer() {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      server.close();
    }
  }

  @Test
  void healthReturnsOk() {
    assertTrue(client.health());
  }

  @Test
  void remoteControlRequiresTls() {
    assertThrows(
        IllegalArgumentException.class, () -> new IceBridgeClient("http://192.0.2.1:8080"));
    assertThrows(
        IllegalArgumentException.class, () -> new IceBridgeClient("http://example.invalid"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new IceBridgeClient("https://user:secret@example.invalid"));
    try (IceBridgeClient tls = new IceBridgeClient("https://example.invalid")) {
      assertNotNull(tls);
    }
    try (IceBridgeClient loopback = new IceBridgeClient("http://[::1]:8080")) {
      assertNotNull(loopback);
    }
  }

  @Test
  void identityPollingContinuesBeyondLifetimeSharedQueueCapacity() {
    assertTrue(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.BOTH));
    for (int i = 0; i < 520; i++) {
      assertTrue(
          inboundQueue.offerFromRudp(
              identity.ed25519PubRaw(), identity.ed25519PubRaw(), new byte[] {1}));
      assertEquals(1, client.poll(1).size());
    }
    assertTrue(inboundQueue.poll(1).isEmpty());
  }

  @Test
  void registerReassertsSameIdentityAfterServerLosesSubscription() {
    byte[] pub = identity.ed25519PubRaw();
    assertTrue(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.BOTH));
    assertTrue(inboundQueue.unregisterConsumer(pub));
    assertFalse(inboundQueue.offerForTarget(pub, pub, new byte[] {1}));
    assertTrue(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.BOTH));
    assertTrue(inboundQueue.offerForTarget(pub, pub, new byte[] {2}));
    assertArrayEquals(new byte[] {2}, client.poll(1).get(0).payload());
  }

  @Test
  void failedResubscriptionNeverPollsSharedAndCanRecover() {
    byte[] pub = identity.ed25519PubRaw();
    assertTrue(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.BOTH));
    assertTrue(inboundQueue.unregisterConsumer(pub));
    for (int i = 0; i < 256; i++) {
      byte[] other = new byte[32];
      other[0] = (byte) i;
      assertTrue(inboundQueue.registerConsumer(other));
    }
    inboundQueue.onMessage(pub, new byte[] {3});
    assertFalse(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.BOTH));
    assertTrue(client.poll(1).isEmpty());
    assertEquals(1, inboundQueue.poll(1).size(), "shared ownership must not be stolen on failure");
    assertTrue(inboundQueue.unregisterConsumer(new byte[32]));
    assertTrue(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.BOTH));
    assertTrue(inboundQueue.offerForTarget(pub, pub, new byte[] {4}));
    assertArrayEquals(new byte[] {4}, client.poll(1).get(0).payload());
  }

  @Test
  void failedInitialRegistrationDoesNotSelectSharedPolling() {
    inboundQueue.onMessage(identity.ed25519PubRaw(), new byte[] {7});
    client.setAuthToken("invalid-test-token");
    assertFalse(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.BOTH));
    client.setAuthToken(authToken);
    assertTrue(client.poll(1).isEmpty());
    assertEquals(1, inboundQueue.poll(1).size());
  }

  @Test
  void refusedIdentityReleaseKeepsAcceptedQueueOwned() {
    byte[] pub = identity.ed25519PubRaw();
    client.setOwnPub(pub);
    assertTrue(inboundQueue.offerForTarget(pub, pub, new byte[] {5}));
    client.setOwnPub(null);
    assertArrayEquals(new byte[] {5}, client.poll(1).get(0).payload());
    assertThrows(IllegalArgumentException.class, () -> client.setOwnPub(new byte[31]));
    client.close();
    assertFalse(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.BOTH));
  }

  @Test
  void cancelledAndExpiredSendsDoNotExecute() {
    com.frostwire.search.relay.DistributedSearchTransport.SendOperation expired =
        client.createSend(identity.ed25519PubRaw(), 1, new byte[] {1}, System.nanoTime() - 1);
    assertFalse(expired.execute());
    com.frostwire.search.relay.DistributedSearchTransport.SendOperation cancelled =
        client.createSend(
            identity.ed25519PubRaw(),
            1,
            new byte[] {1},
            System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
    cancelled.cancel();
    assertFalse(cancelled.execute());
  }

  @Test
  void cancellationClosesInFlightHttpCall() throws Exception {
    com.sun.net.httpserver.HttpServer slow =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    slow.createContext(
        "/send",
        exchange -> {
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    slow.start();
    try (IceBridgeClient sending = new IceBridgeClient(slow.getAddress().getPort())) {
      com.frostwire.search.relay.DistributedSearchTransport.SendOperation operation =
          sending.createSend(
              identity.ed25519PubRaw(),
              1,
              new byte[] {1},
              System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
      java.util.concurrent.atomic.AtomicBoolean accepted =
          new java.util.concurrent.atomic.AtomicBoolean(true);
      Thread worker = new Thread(() -> accepted.set(operation.execute()));
      worker.start();
      try {
        assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        operation.cancel();
        worker.join(1000);
        assertFalse(worker.isAlive());
        assertFalse(accepted.get());
      } finally {
        operation.cancel();
        release.countDown();
        worker.join(1000);
      }
    } finally {
      release.countDown();
      slow.stop(0);
    }
  }

  @Test
  void unknownLengthResponseStopsReadingAtByteCap() throws Exception {
    int limit = 16 * 1024 * 1024;
    java.util.concurrent.atomic.AtomicInteger readBytes =
        new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();
    okio.BufferedSource source =
        okio.Okio.buffer(
            new okio.Source() {
              private final byte[] block = new byte[8192];

              @Override
              public long read(okio.Buffer sink, long count) {
                int n = (int) Math.min(count, block.length);
                readBytes.addAndGet(n);
                sink.write(block, 0, n);
                return n;
              }

              @Override
              public okio.Timeout timeout() {
                return okio.Timeout.NONE;
              }

              @Override
              public void close() {
                closed.set(true);
              }
            });
    okhttp3.ResponseBody body =
        new okhttp3.ResponseBody() {
          @Override
          public okhttp3.MediaType contentType() {
            return okhttp3.MediaType.get("application/json");
          }

          @Override
          public long contentLength() {
            return -1;
          }

          @Override
          public okio.BufferedSource source() {
            return source;
          }
        };
    try (okhttp3.Response response =
        new okhttp3.Response.Builder()
            .request(new okhttp3.Request.Builder().url("http://127.0.0.1/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()) {
      java.lang.reflect.Method readBody =
          IceBridgeClient.class.getDeclaredMethod("readBody", okhttp3.Response.class);
      readBody.setAccessible(true);
      assertNull(readBody.invoke(null, response));
      assertTrue(readBytes.get() <= limit + 8192);
      assertTrue(closed.get());
    }
  }

  @Test
  void registerAndLookupForwarder() {
    assertTrue(client.register(identity, "127.0.0.1", 6888, IceBridgeConfig.Role.FORWARDER));
    List<PeerInfo> peers = client.lookup(10);
    assertEquals(1, peers.size());
    assertEquals("127.0.0.1", peers.get(0).host);
    assertEquals(6888, peers.get(0).rudpPort);
  }

  @Test
  void sendAndPollRoundTrip() {
    byte[] targetPub = identity.ed25519PubRaw();
    byte[] payload = "search query".getBytes(StandardCharsets.UTF_8);
    inboundQueue.onMessage(
        targetPub,
        com.frostwire.search.relay.icebridge.MeshEnvelope.encodeForWire(
            com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH, payload));
    List<IceBridgeClient.InboundMessage> messages = client.poll(10);
    assertEquals(1, messages.size());
    assertArrayEquals(targetPub, messages.get(0).sourcePub());
    assertArrayEquals(payload, messages.get(0).payload());
    assertEquals(
        com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH, messages.get(0).protocolId());
  }

  @Test
  void barePayloadIsAcceptedAsSearchFallback() {
    // Intentional: local RELAY delivery may hand bare app payloads;
    // InboundMessageQueue treats them as SEARCH (MeshProtocolId.SEARCH).
    byte[] source = identity.ed25519PubRaw();
    byte[] bare = "not-framed".getBytes(StandardCharsets.UTF_8);
    inboundQueue.onMessage(source, bare);
    List<IceBridgeClient.InboundMessage> messages = client.poll(10);
    assertEquals(1, messages.size());
    assertArrayEquals(bare, messages.get(0).payload());
    assertEquals(
        com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH, messages.get(0).protocolId());
  }

  @Test
  void inboundMessage_defensiveCopies_preventAliasing() {
    byte[] sourcePub = new byte[32];
    byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
    IceBridgeClient.InboundMessage msg =
        new IceBridgeClient.InboundMessage(sourcePub, payload, 123L);

    byte[] gotSource = msg.sourcePub();
    byte[] gotPayload = msg.payload();
    gotSource[0] = (byte) 0xFF;
    gotPayload[0] = (byte) 0xFF;

    assertArrayEquals(sourcePub, msg.sourcePub());
    assertArrayEquals(payload, msg.payload());
    assertEquals(123L, msg.receivedMs());
  }

  @Test
  void inboundMessage_handlesNullArrays() {
    IceBridgeClient.InboundMessage msg = new IceBridgeClient.InboundMessage(null, null, 0L);
    assertEquals(0, msg.sourcePub().length);
    assertEquals(0, msg.payload().length);
    assertEquals(0L, msg.receivedMs());
  }

  @Test
  void close_doesNotThrow() {
    client.health();
    client.close();
    client.close();
  }

  @Test
  void route_addsPeerToRegistry() {
    byte[] peerPub = new byte[32];
    peerPub[0] = 0x42;
    assertTrue(client.route(peerPub, "10.0.0.5", 6889, IceBridgeConfig.Role.BOTH));
  }

  private static int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
