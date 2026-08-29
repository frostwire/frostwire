/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches a torrent's full .torrent bytes from its holder over the IceBridge
 * mesh (Protocol #3 METADATA) — the NAT-proof path for torrents whose seeder
 * is unreachable directly (cellular/CGNAT, home NAT without port mapping).
 *
 * <p>Sends a signed, targeted {@link TorrentMetadataRequest} to the holder's
 * Ed25519 pub, collects the holder-signed chunked
 * {@link TorrentMetadataResponse} frames via a temporary transport listener,
 * verifies every chunk under the holder's key, and returns the reassembled
 * bytes only when the whole-payload digest matches.
 *
 * <p>Fail-fast: a signed holder error frame (NOT_FOUND/TOO_LARGE) returns
 * null immediately instead of waiting out the timeout.
 */
public final class MeshTorrentMetadataFetcher implements DistributedSearchTransport.PayloadListener {

    private static final Logger LOG = Logger.getLogger(MeshTorrentMetadataFetcher.class);

    /** How long to wait for the holder's full chunked answer. */
    public static final int DEFAULT_TIMEOUT_SEC = 5;

    private final DistributedSearchTransport transport;
    private final IdentityKeys identity;
    private final byte[] holderPub;
    private final byte[] infoHash;
    private final long timeoutMs;

    private final byte[] nonce;
    private final CountDownLatch done = new CountDownLatch(1);
    private final ConcurrentMap<Integer, TorrentMetadataResponse> chunks = new ConcurrentHashMap<>();
    private final AtomicReference<byte[]> result = new AtomicReference<>();

    private MeshTorrentMetadataFetcher(DistributedSearchTransport transport,
                                        IdentityKeys identity,
                                        byte[] holderPub,
                                        byte[] infoHash,
                                        long timeoutMs) {
        this.transport = transport;
        this.identity = identity;
        this.holderPub = holderPub.clone();
        this.infoHash = infoHash.clone();
        this.timeoutMs = timeoutMs;
        byte[] n = new byte[32];
        new java.security.SecureRandom().nextBytes(n);
        this.nonce = n;
    }

    /**
     * Send a TORRENT_FETCH request and block up to the timeout for the
     * verified full .torrent bytes.
     *
     * @return the holder-signed full .torrent bytes, or null on timeout,
     *         transport failure, verification failure, or holder error.
     */
    public static byte[] fetch(DistributedSearchTransport transport,
                               IdentityKeys identity,
                               byte[] holderPub,
                               byte[] infoHash,
                               long timeoutMs) {
        if (transport == null || identity == null
                || holderPub == null || holderPub.length != 32
                || infoHash == null || infoHash.length != 20) {
            return null;
        }
        MeshTorrentMetadataFetcher fetcher = new MeshTorrentMetadataFetcher(
                transport, identity, holderPub, infoHash, timeoutMs);
        return fetcher.fetchNow();
    }

    private byte[] fetchNow() {
        transport.addListener(this);
        try {
            TorrentMetadataRequest request = buildSignedRequest();
            if (request == null) {
                return null;
            }
            byte[] payload = SearchPayloadCodec.encodeTorrentMetadataRequest(request);
            boolean sent = transport.send(holderPub, MeshProtocolId.METADATA, payload);
            if (!sent) {
                LOG.info("MeshTorrentMetadataFetcher: send failed to holder "
                        + Hex.encode(holderPub).substring(0, 8) + " ih=" + Hex.encode(infoHash));
                return null;
            }
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                LOG.info("MeshTorrentMetadataFetcher: timed out ih=" + Hex.encode(infoHash));
                return null;
            }
            return result.get();
        } catch (Throwable t) {
            LOG.warn("MeshTorrentMetadataFetcher failed ih=" + Hex.encode(infoHash), t);
            return null;
        } finally {
            transport.removeListener(this);
        }
    }

    private TorrentMetadataRequest buildSignedRequest() {
        try {
            TorrentMetadataRequest.Builder b = TorrentMetadataRequest.builder()
                    .infoHash(infoHash)
                    .nonce(nonce)
                    .requesterPub(identity.ed25519PubRaw())
                    .timestamp(System.currentTimeMillis() / 1000L);
            TorrentMetadataRequest unsigned = b.signature(new byte[64]).build();
            Signature signer = IdentityKeys.softwareSignature("Ed25519");
            signer.initSign(identity.ed25519().getPrivate());
            signer.update(unsigned.canonicalBytes());
            return b.signature(signer.sign()).build();
        } catch (Throwable t) {
            LOG.warn("MeshTorrentMetadataFetcher could not sign request", t);
            return null;
        }
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs) {
        // METADATA frames always carry the protocol id; the 3-arg legacy
        // delivery carries only SEARCH payloads, so nothing to do here.
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs, int protocolId) {
        if (MeshProtocolId.effective(protocolId) != MeshProtocolId.METADATA) {
            return;
        }
        if (!Arrays.equals(sourcePub, holderPub)) {
            return;
        }
        TorrentMetadataResponse response = SearchPayloadCodec.decodeTorrentMetadataResponse(payload);
        if (response == null
                || !Arrays.equals(response.nonce(), nonce)
                || !Arrays.equals(response.infoHash(), infoHash)
                || !response.verifySignature(holderPub)) {
            LOG.debug("MeshTorrentMetadataFetcher: dropped unverified metadata frame");
            return;
        }
        if (response.isError()) {
            LOG.info("MeshTorrentMetadataFetcher: holder error " + response.error()
                    + " ih=" + Hex.encode(infoHash));
            done.countDown();
            return;
        }
        chunks.put(response.chunkIndex(), response);
        if (response.isFinalChunk()) {
            List<TorrentMetadataResponse> ordered = new ArrayList<>(chunks.values());
            ordered.sort(java.util.Comparator.comparingInt(TorrentMetadataResponse::chunkIndex));
            byte[] assembled = TorrentMetadataResponse.assemble(ordered);
            if (assembled != null) {
                result.set(assembled);
            } else {
                LOG.warn("MeshTorrentMetadataFetcher: assembly failed ih=" + Hex.encode(infoHash));
            }
            done.countDown();
        }
    }
}