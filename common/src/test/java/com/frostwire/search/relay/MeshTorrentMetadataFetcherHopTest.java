/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    byte[] infoHash = new byte[20];
    new SecureRandom().nextBytes(infoHash);
    byte[] torrent = new byte[TorrentMetadataResponse.CHUNK_DATA_BYTES + 80];
    new SecureRandom().nextBytes(torrent);

    HopTransport transport = new HopTransport(holder, hopPub, torrent);
    byte[] fetched =
        MeshTorrentMetadataFetcher.fetch(
            transport, requester, holder.ed25519PubRaw(), infoHash, 5_000);

    assertNotNull(fetched);
    assertArrayEquals(torrent, fetched);
  }

  private static final class HopTransport implements DistributedSearchTransport {
    private final IdentityKeys holder;
    private final byte[] hopPub;
    private final byte[] torrent;
    private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();

    private HopTransport(IdentityKeys holder, byte[] hopPub, byte[] torrent) {
      this.holder = holder;
      this.hopPub = hopPub;
      this.torrent = torrent;
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      TorrentMetadataRequest request = SearchPayloadCodec.decodeTorrentMetadataRequest(payload);
      if (request == null) {
        return false;
      }
      try {
        List<TorrentMetadataResponse> unsigned =
            TorrentMetadataResponse.buildChunks(
                request.nonce(), request.infoHash(), System.currentTimeMillis() / 1000L, torrent);
        for (TorrentMetadataResponse chunk : signAll(unsigned, holder)) {
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
              .signature(signer.sign())
              .build());
    }
    return signed;
  }
}
