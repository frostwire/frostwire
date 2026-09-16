/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.MeshProtocolId;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Deterministic, network-free coverage of the fail-closed admission bounds in
 * {@link MeshTorrentMetadataFetcher}: the global in-flight gate, the
 * per-holder cap, and counter release after completion. Fetches are parked by
 * a fake transport whose {@code createSend(...).execute()} blocks on a latch
 * the test controls.
 */
class MeshTorrentMetadataFetcherConcurrencyTest {

  private static final long PARKED_TIMEOUT_MS = 30_000;

  @Test
  void concurrentFetchesBeyondGlobalCapFailFast() throws Exception {
    int cap = MeshTorrentMetadataFetcher.MAX_GLOBAL_IN_FLIGHT_FETCHES;
    BlockingTransport transport = new BlockingTransport(cap);
    IdentityKeys requester = IdentityKeys.generate(0);
    byte[] infoHash = TestTorrentMetadata.infoHash();
    List<Thread> workers = new ArrayList<>();

    for (int i = 0; i < cap; i++) {
      byte[] holder = randomHolder();
      Thread worker = new Thread(() -> MeshTorrentMetadataFetcher.fetch(
          transport, requester, holder, infoHash, PARKED_TIMEOUT_MS));
      worker.start();
      workers.add(worker);
    }

    try {
      assertTrue(transport.awaitCreated(5, TimeUnit.SECONDS),
          "all " + cap + " gate slots should be reserved");

      long start = System.nanoTime();
      byte[] overflow = MeshTorrentMetadataFetcher.fetch(
          transport, requester, randomHolder(), infoHash, PARKED_TIMEOUT_MS);
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      assertNull(overflow, "a fetch beyond the global cap must fail closed");
      assertTrue(elapsedMs < 1_000,
          "overflow fetch must not block, took " + elapsedMs + " ms");
    } finally {
      transport.release();
      for (Thread worker : workers) {
        worker.interrupt();
      }
      for (Thread worker : workers) {
        worker.join(5_000);
      }
    }
  }

  @Test
  void thirdConcurrentFetchToSameHolderFailsFast() throws Exception {
    BlockingTransport transport = new BlockingTransport(MeshTorrentMetadataFetcher.MAX_IN_FLIGHT_PER_HOLDER);
    IdentityKeys requester = IdentityKeys.generate(0);
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] holderPub = holder.ed25519PubRaw();
    byte[] infoHash = TestTorrentMetadata.infoHash();
    List<Thread> workers = new ArrayList<>();

    for (int i = 0; i < MeshTorrentMetadataFetcher.MAX_IN_FLIGHT_PER_HOLDER; i++) {
      Thread worker = new Thread(() -> MeshTorrentMetadataFetcher.fetch(
          transport, requester, holderPub, infoHash, PARKED_TIMEOUT_MS));
      worker.start();
      workers.add(worker);
    }

    try {
      assertTrue(transport.awaitCreated(5, TimeUnit.SECONDS),
          "two fetches to the same holder should be admitted");

      long start = System.nanoTime();
      byte[] third = MeshTorrentMetadataFetcher.fetch(
          transport, requester, holderPub, infoHash, PARKED_TIMEOUT_MS);
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      assertNull(third, "a third fetch to the same holder must fail closed");
      assertTrue(elapsedMs < 1_000,
          "per-holder rejection must not block, took " + elapsedMs + " ms");
    } finally {
      transport.release();
      for (Thread worker : workers) {
        worker.interrupt();
      }
      for (Thread worker : workers) {
        worker.join(5_000);
      }
    }
  }

  @Test
  void slotsAreReleasedAfterCompletionSoLaterFetchesSucceed() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    IdentityKeys requester = IdentityKeys.generate(0);
    byte[] torrent = TestTorrentMetadata.bytes(500);
    byte[] holderPub = holder.ed25519PubRaw();
    byte[] infoHash = TestTorrentMetadata.infoHash();
    CompletingTransport transport = new CompletingTransport(holder, torrent);

    int iterations = MeshTorrentMetadataFetcher.MAX_GLOBAL_IN_FLIGHT_FETCHES
        + MeshTorrentMetadataFetcher.MAX_IN_FLIGHT_PER_HOLDER + 1;
    for (int i = 0; i < iterations; i++) {
      byte[] fetched = MeshTorrentMetadataFetcher.fetch(
          transport, requester, holderPub, infoHash, 5_000);
      assertArrayEquals(torrent, fetched,
          "sequential fetch #" + i + " must succeed once slots are released");
    }
  }

  private static byte[] randomHolder() {
    byte[] holder = new byte[32];
    new SecureRandom().nextBytes(holder);
    return holder;
  }

  /**
   * Parks every admitted fetch inside {@code execute()} until {@link #release()}
   * is called, so a test can observe the admission gates while fetches are
   * genuinely in flight. {@link #created} is counted in {@code createSend},
   * which runs on the caller thread after the gate is reserved and before the
   * send is submitted.
   */
  private static final class BlockingTransport implements DistributedSearchTransport {
    private final CountDownLatch created;
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();

    private BlockingTransport(int expectedCalls) {
      this.created = new CountDownLatch(expectedCalls);
    }

    private boolean awaitCreated(long timeout, TimeUnit unit) throws InterruptedException {
      return created.await(timeout, unit);
    }

    private void release() {
      release.countDown();
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      return false;
    }

    @Override
    public SendOperation createSend(byte[] targetPub, int protocolId, byte[] payload, long deadlineNanos) {
      created.countDown();
      return new SendOperation() {
        @Override
        public boolean execute() {
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
          }
          return true;
        }

        @Override
        public void cancel() {
        }
      };
    }

    @Override
    public void addListener(PayloadListener listener) {
      listeners.add(listener);
    }

    @Override
    public void removeListener(PayloadListener listener) {
      listeners.remove(listener);
    }
  }

  /**
   * Serves a valid holder-signed chunk stream synchronously (the default
   * {@code createSend} delegates to {@link #send}), so a fetch completes and
   * releases its slots.
   */
  private static final class CompletingTransport implements DistributedSearchTransport {
    private final IdentityKeys holder;
    private final byte[] torrent;
    private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();

    private CompletingTransport(IdentityKeys holder, byte[] torrent) {
      this.holder = holder;
      this.torrent = torrent;
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      TorrentMetadataRequest request = SearchPayloadCodec.decodeTorrentMetadataRequest(payload);
      if (request == null) {
        return false;
      }
      try {
        List<TorrentMetadataResponse> signed = signAll(
            TorrentMetadataResponse.buildChunks(
                request.nonce(), request.infoHash(), System.currentTimeMillis() / 1000L, torrent),
            holder);
        for (TorrentMetadataResponse chunk : signed) {
          byte[] wire = SearchPayloadCodec.encodeTorrentMetadataResponse(chunk);
          for (PayloadListener listener : listeners) {
            listener.onPayload(holder.ed25519PubRaw(), wire, System.currentTimeMillis(),
                MeshProtocolId.METADATA);
          }
        }
        return true;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void addListener(PayloadListener listener) {
      listeners.add(listener);
    }

    @Override
    public void removeListener(PayloadListener listener) {
      listeners.remove(listener);
    }
  }

  private static List<TorrentMetadataResponse> signAll(
      List<TorrentMetadataResponse> unsigned, IdentityKeys holder) throws Exception {
    List<TorrentMetadataResponse> signed = new ArrayList<>();
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    for (TorrentMetadataResponse chunk : unsigned) {
      signer.initSign(holder.ed25519().getPrivate());
      signer.update(chunk.canonicalBytes());
      signed.add(
          TorrentMetadataResponse.builder()
              .version(chunk.version())
              .nonce(chunk.nonce())
              .infoHash(chunk.infoHash())
              .payloadDigest(chunk.payloadDigest())
              .chunkIndex(chunk.chunkIndex())
              .finalChunk(chunk.isFinalChunk())
              .timestamp(chunk.timestamp())
              .data(chunk.data())
              .error(chunk.error())
              .signature(signer.sign())
              .build());
    }
    return signed;
  }
}
