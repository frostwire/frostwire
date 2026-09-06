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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.search.relay.icebridge.MeshProtocolId;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * RELAY_RESPONSE attributes the hop (EC2) as transport sourcePub, not the
 * holder. Chunks must still assemble when the holder signature verifies.
 */
class MeshTorrentMetadataFetcherHopTest {

  @Test
  void assemblesChunksAttributedToRelayHop() throws Exception {
    IdentityKeys requester = IdentityKeys.generate(0);
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] hopPub = new byte[32];
    new SecureRandom().nextBytes(hopPub);
    byte[] torrent = TestTorrentMetadata.bytes(500);
    byte[] infoHash = TestTorrentMetadata.infoHash();

    HopTransport transport = new HopTransport(holder, hopPub, torrent);
    byte[] fetched =
        MeshTorrentMetadataFetcher.fetch(
            transport, requester, holder.ed25519PubRaw(), infoHash, 5_000);

    assertNotNull(fetched);
    assertArrayEquals(torrent, fetched);
  }

  @Test
  void rejectsGenuineTorrentWithDifferentSelectedHash() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] selected = TestTorrentMetadata.infoHash();
    selected[0] ^= 1;
    assertNull(MeshTorrentMetadataFetcher.fetch(
        new HopTransport(holder, new byte[32], TestTorrentMetadata.bytes(500)), IdentityKeys.generate(0),
        holder.ed25519PubRaw(), selected, 1000));
  }

  @Test
  void acceptsFinalBeforePrefixAndIgnoresDuplicate() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    HopTransport transport = new HopTransport(holder, new byte[32], TestTorrentMetadata.bytes(500));
    transport.reverse = true;
    assertArrayEquals(TestTorrentMetadata.bytes(500), MeshTorrentMetadataFetcher.fetch(
        transport, IdentityKeys.generate(0), holder.ed25519PubRaw(), TestTorrentMetadata.infoHash(), 1000));
  }

  @Test
  void nativeVerificationMatchesV1V2AndHybridOriginalHashes() throws Exception {
    assertTrue(MeshTorrentMetadataFetcher.matchesInfoHash(
        TestTorrentMetadata.bytes(0), TestTorrentMetadata.infoHash()));
    for (boolean hybrid : new boolean[]{false, true}) {
      byte[] torrent = TestTorrentMetadata.v2Bytes(hybrid);
      byte[] v2 = java.security.MessageDigest.getInstance("SHA-256")
          .digest(TestTorrentMetadata.v2Info(hybrid));
      assertTrue(MeshTorrentMetadataFetcher.matchesInfoHash(torrent, v2));
      assertTrue(MeshTorrentMetadataFetcher.matchesInfoHash(torrent, java.util.Arrays.copyOf(v2, 20)));
      byte[] v1 = java.security.MessageDigest.getInstance("SHA-1")
          .digest(TestTorrentMetadata.v2Info(hybrid));
      assertEquals(hybrid, MeshTorrentMetadataFetcher.matchesInfoHash(torrent, v1));
      v2[0] ^= 1;
      assertFalse(MeshTorrentMetadataFetcher.matchesInfoHash(torrent, v2));
    }
    assertFalse(MeshTorrentMetadataFetcher.matchesInfoHash(new byte[]{1}, new byte[20]));
    assertFalse(MeshTorrentMetadataFetcher.matchesInfoHash(
        new byte[(int) TorrentMetadataResponse.MAX_TORRENT_BYTES + 1], new byte[20]));
  }

  @Test
  void gapOrConflictingDuplicateFailsWithoutPublishingBytes() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    HopTransport gap = new HopTransport(holder, new byte[32], TestTorrentMetadata.bytes(500));
    gap.onlyFinal = true;
    assertNull(MeshTorrentMetadataFetcher.fetch(gap, IdentityKeys.generate(0),
        holder.ed25519PubRaw(), TestTorrentMetadata.infoHash(), 200));
    assertTrue(gap.listeners.isEmpty());
    HopTransport conflict = new HopTransport(holder, new byte[32], TestTorrentMetadata.bytes(500));
    conflict.conflict = true;
    assertNull(MeshTorrentMetadataFetcher.fetch(conflict, IdentityKeys.generate(0),
        holder.ed25519PubRaw(), TestTorrentMetadata.infoHash(), 1000));
    assertTrue(conflict.listeners.isEmpty());
  }

  @Test
  void staleSignedErrorCannotAbortCurrentPositiveStream() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    HopTransport transport = new HopTransport(holder, new byte[32], TestTorrentMetadata.bytes(500));
    transport.staleError = true;
    assertArrayEquals(TestTorrentMetadata.bytes(500), MeshTorrentMetadataFetcher.fetch(transport,
        IdentityKeys.generate(0), holder.ed25519PubRaw(), TestTorrentMetadata.infoHash(), 1000));
  }

  @Test
  void totalDeadlineIncludesBlockedSend() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    HopTransport transport = new HopTransport(holder, new byte[32], TestTorrentMetadata.bytes(500));
    transport.blockSend = new java.util.concurrent.CountDownLatch(1);
    long start = System.nanoTime();
    try {
      assertNull(MeshTorrentMetadataFetcher.fetch(transport, IdentityKeys.generate(0),
          holder.ed25519PubRaw(), TestTorrentMetadata.infoHash(), 200));
      assertTrue(System.nanoTime() - start < java.util.concurrent.TimeUnit.SECONDS.toNanos(2));
      assertTrue(transport.listeners.isEmpty());
    } finally {
      transport.blockSend.countDown();
    }
  }

  @Test
  void interruptCancelsActiveSendAndRemovesListener() throws Exception {
    java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch cancelled = new java.util.concurrent.CountDownLatch(1);
    List<DistributedSearchTransport.PayloadListener> listeners = new CopyOnWriteArrayList<>();
    DistributedSearchTransport transport = new DistributedSearchTransport() {
      public boolean send(byte[] pub, int protocol, byte[] payload) { return false; }
      public SendOperation createSend(byte[] pub, int protocol, byte[] payload, long deadline) {
        return new SendOperation() {
          public boolean execute() {
            entered.countDown();
            try { cancelled.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return false;
          }
          public void cancel() { cancelled.countDown(); }
        };
      }
      public void addListener(PayloadListener listener) { listeners.add(listener); }
      public void removeListener(PayloadListener listener) { listeners.remove(listener); }
    };
    IdentityKeys requester = IdentityKeys.generate(0);
    java.util.concurrent.atomic.AtomicReference<byte[]> result = new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
    Thread worker = new Thread(() -> {
      result.set(MeshTorrentMetadataFetcher.fetch(
          transport, requester, new byte[32], TestTorrentMetadata.infoHash(), 10_000));
      interrupted.set(Thread.currentThread().isInterrupted());
    });
    worker.start();
    try {
      assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
      worker.interrupt();
      worker.join(1000);
      assertFalse(worker.isAlive());
      assertTrue(interrupted.get());
      assertEquals(0, cancelled.getCount());
      assertTrue(listeners.isEmpty());
      assertNull(result.get());
    } finally {
      worker.interrupt();
      cancelled.countDown();
      worker.join(1000);
    }
  }

  private static final class HopTransport implements DistributedSearchTransport {
    private final IdentityKeys holder;
    private final byte[] hopPub;
    private final byte[] torrent;
    private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    private boolean reverse;
    private boolean onlyFinal;
    private boolean conflict;
    private boolean staleError;
    private java.util.concurrent.CountDownLatch blockSend;

    private HopTransport(IdentityKeys holder, byte[] hopPub, byte[] torrent) {
      this.holder = holder;
      this.hopPub = hopPub;
      this.torrent = torrent;
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      if (blockSend != null) {
        try {
          blockSend.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      TorrentMetadataRequest request = SearchPayloadCodec.decodeTorrentMetadataRequest(payload);
      if (request == null) {
        return false;
      }
      try {
        List<TorrentMetadataResponse> unsigned =
            TorrentMetadataResponse.buildChunks(
                request.nonce(), request.infoHash(), System.currentTimeMillis() / 1000L, torrent);
        List<TorrentMetadataResponse> signed = signAll(unsigned, holder);
        if (staleError) {
          signed.add(0, signAll(List.of(TorrentMetadataResponse.buildError(request.nonce(), request.infoHash(),
              System.currentTimeMillis() / 1000L - 2 * TorrentMetadataRequest.MAX_TIMESTAMP_SKEW_SEC,
              TorrentMetadataResponse.ERR_NOT_FOUND)), holder).get(0));
        }
        if (reverse) {
          java.util.Collections.reverse(signed);
          signed.add(0, signed.get(0));
        }
        if (onlyFinal) {
          signed = List.of(signed.get(signed.size() - 1));
        } else if (conflict) {
          byte[] other = torrent.clone();
          other[0] ^= 1;
          signed.add(1, signAll(TorrentMetadataResponse.buildChunks(
              request.nonce(), request.infoHash(), System.currentTimeMillis() / 1000L, other), holder).get(0));
        }
        for (TorrentMetadataResponse chunk : signed) {
          byte[] wire = SearchPayloadCodec.encodeTorrentMetadataResponse(chunk);
          for (PayloadListener listener : listeners) {
            listener.onPayload(hopPub, wire, System.currentTimeMillis(), MeshProtocolId.METADATA);
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
