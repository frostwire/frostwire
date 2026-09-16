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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <p>Bounded concurrency (fail-closed): at most
 * {@link #MAX_GLOBAL_IN_FLIGHT_FETCHES} fetches may be in flight across the
 * whole process, and at most {@link #MAX_IN_FLIGHT_PER_HOLDER} of those may
 * target the same 32-byte holder pub. A call that cannot reserve either slot
 * returns {@code null} immediately: it never blocks and never occupies a
 * {@link #SENDERS} worker, so a metadata storm cannot exhaust threads. Callers
 * should treat {@code null} as retryable backpressure. Every reserved slot is
 * released in a {@code finally}, including on interrupt, timeout, and
 * rejection.
 *
 * <p>Chunks arriving via EC2 RELAY_RESPONSE are attributed to the hop pub, not
 * the holder. Auth is the holder Ed25519 signature on each chunk — never
 * require {@code sourcePub == holderPub} or cellular TORRENT_FETCH times out.
 */
public final class MeshTorrentMetadataFetcher implements DistributedSearchTransport.PayloadListener {

    private static final Logger LOG = Logger.getLogger(MeshTorrentMetadataFetcher.class);

    /** How long to wait for the holder's full chunked answer. */
    public static final int DEFAULT_TIMEOUT_SEC = 15;

    /**
     * Maximum number of metadata fetches allowed to be in flight process-wide.
     * A fair {@link Semaphore} enforces this; a caller that cannot acquire a
     * permit returns {@code null} without blocking (fail closed), so a storm of
     * concurrent {@code /torrent} or catalog metadata requests can never
     * exhaust caller threads or the {@link #SENDERS} pool. Sized slightly above
     * the pool's active thread count so the queue can absorb bursty traffic
     * without unbounded pile-up.
     */
    static final int MAX_GLOBAL_IN_FLIGHT_FETCHES = 8;

    /**
     * Maximum number of concurrent metadata fetches targeting the same 32-byte
     * holder pub. Extra requests to a hot holder return {@code null}
     * immediately (fail closed) so one holder cannot monopolize the global
     * gate.
     */
    static final int MAX_IN_FLIGHT_PER_HOLDER = 2;

    /**
     * Defensive ceiling on the number of distinct holders tracked in
     * {@link #HOLDER_IN_FLIGHT}. The global gate already bounds live entries by
     * {@link #MAX_GLOBAL_IN_FLIGHT_FETCHES}; this is a belt-and-suspenders cap
     * so a bug can never grow the map without bound.
     */
    static final int MAX_TRACKED_HOLDERS = 64;

    /**
     * Process-wide in-flight fetch gate. Fair so arrival order is preserved
     * under contention; acquisition is always non-blocking
     * ({@link Semaphore#tryAcquire()}).
     */
    private static final Semaphore GLOBAL_FETCH_GATE =
            new Semaphore(MAX_GLOBAL_IN_FLIGHT_FETCHES, true);

    /**
     * Live per-holder fetch counts keyed by hex-encoded holder pub. Entries are
     * removed as soon as a count returns to zero.
     */
    private static final ConcurrentHashMap<String, AtomicInteger> HOLDER_IN_FLIGHT =
            new ConcurrentHashMap<>();
    private static final AtomicInteger TRACKED_HOLDERS = new AtomicInteger();

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
     * <p>Fail-closed admission: this method first reserves a global in-flight
     * slot ({@link #MAX_GLOBAL_IN_FLIGHT_FETCHES}) and then a per-holder slot
     * ({@link #MAX_IN_FLIGHT_PER_HOLDER}). If either is exhausted it returns
     * {@code null} immediately without blocking. Both slots are released in
     * {@code finally} on every exit path.
     *
     * @return the holder-signed full .torrent bytes, or null on admission
     *         rejection (gate or per-holder cap), timeout, transport failure,
     *         verification failure, or holder error.
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
        if (!GLOBAL_FETCH_GATE.tryAcquire()) {
            LOG.info("MeshTorrentMetadataFetcher: global fetch gate saturated, failing closed ih="
                    + Hex.encode(infoHash));
            return null;
        }
        String holderHex = Hex.encode(holderPub);
        try {
            if (!tryAcquireHolderSlot(holderHex)) {
                LOG.info("MeshTorrentMetadataFetcher: per-holder fetch cap reached for holder "
                        + holderHex.substring(0, 8) + " ih=" + Hex.encode(infoHash));
                return null;
            }
            try {
                MeshTorrentMetadataFetcher fetcher = new MeshTorrentMetadataFetcher(
                        transport, identity, holderPub, infoHash, timeoutMs);
                return fetcher.fetchNow();
            } finally {
                releaseHolderSlot(holderHex);
            }
        } finally {
            GLOBAL_FETCH_GATE.release();
        }
    }

    /**
     * Atomically reserve one per-holder in-flight slot. Returns {@code false}
     * (fail closed, no slot held) when the holder already has
     * {@link #MAX_IN_FLIGHT_PER_HOLDER} fetches running or when the tracking
     * map has reached {@link #MAX_TRACKED_HOLDERS}.
     */
    private static boolean tryAcquireHolderSlot(String holderHex) {
        AtomicBoolean acquired = new AtomicBoolean(false);
        HOLDER_IN_FLIGHT.compute(holderHex, (key, counter) -> {
            if (counter == null) {
                if (TRACKED_HOLDERS.get() >= MAX_TRACKED_HOLDERS) {
                    return null;
                }
                TRACKED_HOLDERS.incrementAndGet();
                acquired.set(true);
                return new AtomicInteger(1);
            }
            if (counter.get() >= MAX_IN_FLIGHT_PER_HOLDER) {
                return counter;
            }
            counter.incrementAndGet();
            acquired.set(true);
            return counter;
        });
        return acquired.get();
    }

    /** Release one per-holder slot, removing the entry once the count hits zero. */
    private static void releaseHolderSlot(String holderHex) {
        HOLDER_IN_FLIGHT.computeIfPresent(holderHex, (key, counter) -> {
            if (counter.decrementAndGet() <= 0) {
                TRACKED_HOLDERS.decrementAndGet();
                return null;
            }
            return counter;
        });
    }

    /**
     * Submit to the bounded sender pool without ever blocking the caller. The
     * pool's bounded queue uses the default abort policy, so a full pool throws
     * {@link RejectedExecutionException}; that is caught and converted to
     * {@code null} so a storm fails closed instead of propagating to callers.
     * Interrupts and the caller's deadline are otherwise preserved unchanged.
     */
    private static Future<Boolean> submitOrNull(Callable<Boolean> task) {
        try {
            return SENDERS.submit(task);
        } catch (RejectedExecutionException e) {
            LOG.info("MeshTorrentMetadataFetcher: sender pool saturated, failing closed");
            return null;
        }
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
            sent = submitOrNull(send::execute);
            if (sent == null) {
                return null;
            }
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
            verified = submitOrNull(() -> !Thread.currentThread().isInterrupted()
                    && System.nanoTime() < deadlineNanos && matchesInfoHash(assembled, infoHash));
            if (verified == null) {
                return null;
            }
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
