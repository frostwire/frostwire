/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.EmptyLocalIndex;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.RelaySearchService;
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

  /** Local control fixture: no UDP, discovery, or external network participation. */
  private static final class Fixture implements AutoCloseable {
    final ConcurrentLinkedQueue<Map<String, Object>> messages = new ConcurrentLinkedQueue<>();
    final AtomicInteger sends = new AtomicInteger();
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

    void offer(byte[] payload) {
      messages.add(
          Map.of(
              "sourcePub",
              Base64.getUrlEncoder().encodeToString(new byte[32]),
              "payload",
              Base64.getUrlEncoder().encodeToString(payload),
              "receivedMs",
              System.currentTimeMillis(),
              "protocolId",
              MeshProtocolId.METADATA));
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
