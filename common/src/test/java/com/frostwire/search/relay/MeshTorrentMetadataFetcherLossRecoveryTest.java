/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.MeshProtocolId;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * A request or chunk lost above rUDP (at-most-once {@code /poll}, a saturated holder lane, a
 * dropped session, a holder send that fails midway) must not cost the whole TORRENT_FETCH: the
 * fetcher asks again for what is missing well before its timeout (frostwire/frostwire#1504).
 */
class MeshTorrentMetadataFetcherLossRecoveryTest {

  private static final long TIMEOUT_MS = 10_000;

  /** Recovery must land long before the timeout: first re-request plus delivery slack. */
  private static final long RECOVERY_BOUND_MS = 5_000;

  @Test
  void lostChunkIsReRequestedAndOnlyTheMissingChunkIsResent() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] torrent = TestTorrentMetadata.bytes(1000);
    LossyHolderTransport transport = new LossyHolderTransport(holder, torrent);
    transport.dropChunkOnFirstRequest = 1;

    long start = System.nanoTime();
    byte[] fetched =
        MeshTorrentMetadataFetcher.fetch(
            transport,
            IdentityKeys.generate(0),
            holder.ed25519PubRaw(),
            TestTorrentMetadata.infoHash(),
            TIMEOUT_MS);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertNotNull(fetched, "one lost chunk must not fail the fetch");
    assertArrayEquals(torrent, fetched);
    assertTrue(elapsedMs < RECOVERY_BOUND_MS, "recovery took " + elapsedMs + "ms");
    assertEquals(2, transport.requests.size(), "one re-request recovers one lost chunk");
    TorrentMetadataRequest retry = transport.requests.get(1);
    assertFalse(
        java.util.Arrays.equals(transport.requests.get(0).nonce(), retry.nonce()),
        "a re-request carries a fresh nonce so the holder's replay guard admits it");
    assertTrue(retry.verifySignature());
    int chunkCount = transport.chunkCount();
    assertTrue(chunkCount >= 3, "fixture must span several chunks, got " + chunkCount);
    for (int i = 0; i < chunkCount; i++) {
      assertEquals(i != 1, retry.hasChunk(i), "have bitmap for chunk " + i);
    }
    assertEquals(List.of(1), transport.deliveredOnRequest.get(1), "only the lost chunk resent");
    assertTrue(transport.listeners.isEmpty());
  }

  @Test
  void lostRequestIsResentInFull() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] torrent = TestTorrentMetadata.bytes(1000);
    LossyHolderTransport transport = new LossyHolderTransport(holder, torrent);
    transport.swallowFirstRequest = true;

    long start = System.nanoTime();
    byte[] fetched =
        MeshTorrentMetadataFetcher.fetch(
            transport,
            IdentityKeys.generate(0),
            holder.ed25519PubRaw(),
            TestTorrentMetadata.infoHash(),
            TIMEOUT_MS);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertArrayEquals(torrent, fetched);
    assertTrue(elapsedMs < RECOVERY_BOUND_MS, "recovery took " + elapsedMs + "ms");
    assertEquals(2, transport.requests.size());
    assertNull(transport.requests.get(1).have(), "nothing held yet, so ask for every chunk");
  }

  @Test
  void silentHolderGetsABoundedNumberOfReRequests() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    LossyHolderTransport transport =
        new LossyHolderTransport(holder, TestTorrentMetadata.bytes(1000));
    transport.swallowAll = true;

    long start = System.nanoTime();
    assertNull(
        MeshTorrentMetadataFetcher.fetch(
            transport,
            IdentityKeys.generate(0),
            holder.ed25519PubRaw(),
            TestTorrentMetadata.infoHash(),
            2_000));
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertTrue(elapsedMs < 3_500, "must still give up at the deadline, took " + elapsedMs + "ms");
    // 1.5 s of silence buys one re-request; the next would be due after 3 s more.
    assertEquals(2, transport.requests.size());
    assertTrue(transport.listeners.isEmpty());
  }

  /**
   * Fake holder: answers each request with holder-signed chunks for that request's nonce, honors
   * the {@code have} bitmap like {@code IncomingSearchRequestHandler}, and can lose frames.
   */
  private static final class LossyHolderTransport implements DistributedSearchTransport {
    private final IdentityKeys holder;
    private final byte[] torrent;
    final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    final List<TorrentMetadataRequest> requests = new CopyOnWriteArrayList<>();
    final List<List<Integer>> deliveredOnRequest = new CopyOnWriteArrayList<>();
    int dropChunkOnFirstRequest = -1;
    boolean swallowFirstRequest;
    boolean swallowAll;

    LossyHolderTransport(IdentityKeys holder, byte[] torrent) {
      this.holder = holder;
      this.torrent = torrent;
    }

    int chunkCount() {
      return (torrent.length + TorrentMetadataResponse.CHUNK_DATA_BYTES - 1)
          / TorrentMetadataResponse.CHUNK_DATA_BYTES;
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      TorrentMetadataRequest request = SearchPayloadCodec.decodeTorrentMetadataRequest(payload);
      if (request == null || !request.verifySignature()) {
        return false;
      }
      requests.add(request);
      boolean first = requests.size() == 1;
      List<Integer> delivered = new ArrayList<>();
      deliveredOnRequest.add(delivered);
      if (swallowAll || first && swallowFirstRequest) {
        return true; // accepted for delivery, then lost
      }
      try {
        List<TorrentMetadataResponse> unsigned =
            TorrentMetadataResponse.buildChunks(
                request.nonce(), request.infoHash(), System.currentTimeMillis() / 1000L, torrent);
        for (TorrentMetadataResponse chunk : unsigned) {
          int index = chunk.chunkIndex();
          if (request.hasChunk(index) || first && index == dropChunkOnFirstRequest) {
            continue;
          }
          delivered.add(index);
          byte[] wire = SearchPayloadCodec.encodeTorrentMetadataResponse(sign(chunk));
          for (PayloadListener listener : listeners) {
            listener.onPayload(
                holder.ed25519PubRaw(), wire, System.currentTimeMillis(), MeshProtocolId.METADATA);
          }
        }
        return true;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private TorrentMetadataResponse sign(TorrentMetadataResponse chunk) throws Exception {
      Signature signer = IdentityKeys.softwareSignature("Ed25519");
      signer.initSign(holder.ed25519().getPrivate());
      signer.update(chunk.canonicalBytes());
      return TorrentMetadataResponse.builder()
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
          .build();
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
}
