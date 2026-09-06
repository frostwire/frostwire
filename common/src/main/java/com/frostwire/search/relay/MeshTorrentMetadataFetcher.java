/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
 *
 * <p>Chunks arriving via EC2 RELAY_RESPONSE are attributed to the hop pub, not
 * the holder. Auth is the holder Ed25519 signature on each chunk — never
 * require {@code sourcePub == holderPub} or cellular TORRENT_FETCH times out.
 */
public final class MeshTorrentMetadataFetcher implements DistributedSearchTransport.PayloadListener {

    private static final Logger LOG = Logger.getLogger(MeshTorrentMetadataFetcher.class);

    /** How long to wait for the holder's full chunked answer. */
    public static final int DEFAULT_TIMEOUT_SEC = 15;
    private static final ThreadPoolExecutor SENDERS = new ThreadPoolExecutor(
            4, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
                Thread thread = new Thread(r, "mesh-metadata-send");
                thread.setDaemon(true);
                return thread;
            });

    private final DistributedSearchTransport transport;
    private final IdentityKeys identity;
    private final byte[] holderPub;
    private final byte[] infoHash;
    private final long timeoutMs;

    private final byte[] nonce;
    private final CountDownLatch done = new CountDownLatch(1);
    private final Map<Integer, TorrentMetadataResponse> chunks = new HashMap<>();
    private int finalIndex = -1;
    private int retainedBytes;
    private byte[] payloadDigest;
    private boolean failed;
    private volatile boolean closed;
    private long deadlineNanos;

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
                || infoHash == null || infoHash.length != 20 || timeoutMs <= 0
                || Thread.currentThread().isInterrupted()) {
            return null;
        }
        MeshTorrentMetadataFetcher fetcher = new MeshTorrentMetadataFetcher(
                transport, identity, holderPub, infoHash, timeoutMs);
        return fetcher.fetchNow();
    }

    private byte[] fetchNow() {
        deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.min(timeoutMs, 60_000));
        DistributedSearchTransport.SendOperation operation = null;
        Future<Boolean> sent = null;
        Future<Boolean> verified = null;
        transport.addListener(this);
        try {
            TorrentMetadataRequest request = buildSignedRequest();
            if (request == null) {
                return null;
            }
            byte[] payload = SearchPayloadCodec.encodeTorrentMetadataRequest(request);
            operation = transport.createSend(holderPub, MeshProtocolId.METADATA, payload, deadlineNanos);
            DistributedSearchTransport.SendOperation send = operation;
            sent = SENDERS.submit(send::execute);
            if (!sent.get(Math.max(1, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                LOG.info("MeshTorrentMetadataFetcher: send failed to holder "
                        + Hex.encode(holderPub).substring(0, 8) + " ih=" + Hex.encode(infoHash));
                return null;
            }
            if (!done.await(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                LOG.info("MeshTorrentMetadataFetcher: timed out ih=" + Hex.encode(infoHash));
                return null;
            }
            List<TorrentMetadataResponse> ordered;
            synchronized (this) {
                if (failed || closed || finalIndex < 0) {
                    return null;
                }
                ordered = new ArrayList<>(chunks.values());
            }
            ordered.sort(java.util.Comparator.comparingInt(TorrentMetadataResponse::chunkIndex));
            byte[] assembled = TorrentMetadataResponse.assemble(ordered);
            if (assembled == null || Thread.currentThread().isInterrupted()
                    || System.nanoTime() >= deadlineNanos) {
                return null;
            }
            // JNI cannot be interrupted, so bound both its admission and the caller's wait.
            verified = SENDERS.submit(() -> !Thread.currentThread().isInterrupted()
                    && System.nanoTime() < deadlineNanos && matchesInfoHash(assembled, infoHash));
            return verified.get(Math.max(1, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)
                    && !Thread.currentThread().isInterrupted() && System.nanoTime() < deadlineNanos
                    ? assembled : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception | LinkageError t) {
            LOG.warn("MeshTorrentMetadataFetcher failed ih=" + Hex.encode(infoHash), t);
            return null;
        } finally {
            closed = true;
            if (operation != null) {
                operation.cancel();
            }
            if (sent != null) {
                sent.cancel(true);
            }
            if (verified != null) {
                verified.cancel(true);
            }
            SENDERS.purge();
            transport.removeListener(this);
            synchronized (this) {
                chunks.clear();
            }
        }
    }

    /**
     * Validate bounded metadata against the originally selected hash off the UI/poller.
     * A 20-byte request matches v1 or the BEP 52 truncated v2 hash; a 32-byte
     * request matches the full v2 hash. Hybrid torrents may match either identity.
     */
    public static boolean matchesInfoHash(byte[] torrentBytes, byte[] selectedHash) {
        if (torrentBytes == null || torrentBytes.length == 0
                || torrentBytes.length > TorrentMetadataResponse.MAX_TORRENT_BYTES
                || selectedHash == null || (selectedHash.length != 20 && selectedHash.length != 32)) {
            return false;
        }
        TorrentInfo info = null;
        try {
            info = TorrentInfo.bdecode(torrentBytes);
            if (!info.isValid()) {
                return false;
            }
            String expected = Hex.encode(selectedHash);
            com.frostwire.jlibtorrent.swig.info_hash_t hashes = info.infoHashType();
            try {
                return selectedHash.length == 20 && hashes.has_v1()
                        && expected.equals(hashes.getV1().to_hex())
                        || hashes.has_v2()
                        && expected.equals(hashes.getV2().to_hex().substring(0, expected.length()));
            } finally {
                hashes.delete();
            }
        } catch (Exception | LinkageError e) {
            LOG.warn("Invalid torrent metadata", e);
            return false;
        } finally {
            if (info != null) {
                info.swig().delete();
            }
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
    public synchronized void onPayload(byte[] sourcePub, byte[] payload, long receivedMs, int protocolId) {
        if (closed || failed || done.getCount() == 0 || System.nanoTime() >= deadlineNanos
                || payload == null || payload.length > 2048
                || MeshProtocolId.effective(protocolId) != MeshProtocolId.METADATA) {
            return;
        }
        TorrentMetadataResponse response = SearchPayloadCodec.decodeTorrentMetadataResponse(payload);
        if (response == null) {
            LOG.warn("MeshTorrentMetadataFetcher: undecodable metadata frame");
            return;
        }
        if (!Arrays.equals(response.nonce(), nonce) || !Arrays.equals(response.infoHash(), infoHash)) {
            LOG.warn("MeshTorrentMetadataFetcher: metadata frame nonce/ih mismatch — stale or crossed fetch");
            return;
        }
        if (!response.verifySignature(holderPub)) {
            LOG.warn("MeshTorrentMetadataFetcher: metadata frame failed holder signature verification");
            return;
        }
        if (response.isError()) {
            long now = System.currentTimeMillis() / 1000L;
            if (response.timestamp() < now - TorrentMetadataRequest.MAX_TIMESTAMP_SKEW_SEC
                    || response.timestamp() > now + TorrentMetadataRequest.MAX_TIMESTAMP_SKEW_SEC) {
                return;
            }
            LOG.info("MeshTorrentMetadataFetcher: holder error " + response.error()
                    + " ih=" + Hex.encode(infoHash));
            failed = true;
            done.countDown();
            return;
        }
        int index = response.chunkIndex();
        TorrentMetadataResponse previous = chunks.get(index);
        if (previous != null) {
            if (!Arrays.equals(previous.canonicalBytes(), response.canonicalBytes())
                    || !Arrays.equals(previous.data(), response.data())) {
                failed = true;
                done.countDown();
            }
            return;
        }
        byte[] data = response.data();
        if (index >= TorrentMetadataResponse.MAX_CHUNKS
                || (payloadDigest != null && !Arrays.equals(payloadDigest, response.payloadDigest()))
                || data.length > TorrentMetadataResponse.MAX_TORRENT_BYTES - retainedBytes
                || (finalIndex >= 0 && (index > finalIndex || response.isFinalChunk() && index != finalIndex))
                || response.isFinalChunk() && chunks.keySet().stream().anyMatch(i -> i > index)) {
            failed = true;
            done.countDown();
            return;
        }
        payloadDigest = response.payloadDigest();
        retainedBytes += data.length;
        chunks.put(index, response);
        if (response.isFinalChunk()) {
            finalIndex = index;
        }
        if (finalIndex >= 0 && chunks.size() == finalIndex + 1) {
            done.countDown();
        }
    }
}
