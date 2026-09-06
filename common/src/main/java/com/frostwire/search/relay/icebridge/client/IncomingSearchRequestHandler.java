/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.EmptyLocalIndex;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LeafPromotionManager;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.NodeCapabilities;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.RateLimiter;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteCatalogBrowseRequest;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.search.relay.RemoteSearchRequest;
import com.frostwire.search.relay.RemoteSearchResponse;
import com.frostwire.search.relay.SearchPayloadCodec;
import com.frostwire.search.relay.ShareVisibility;
import com.frostwire.search.relay.ShareVisibilityPolicy;
import com.frostwire.search.relay.TorrentMetadataProvider;
import com.frostwire.search.relay.TorrentMetadataRequest;
import com.frostwire.search.relay.TorrentMetadataResponse;
import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listens for incoming search requests on a {@link DistributedSearchTransport}
 * and dispatches them to the local {@link RelaySearchService}.
 *
 * <p>Registered as a permanent listener on the transport. When a payload
 * arrives that decodes as a valid {@link RemoteSearchRequest}, the handler
 * processes it through the search service, signs the response, and sends it
 * back to the requester via the transport. If the payload decodes as a
 * {@link RemoteCatalogBrowseRequest} instead, the handler responds with the
 * local index contents as a signed JSON manifest. Payloads that do not
 * decode as either request type (e.g. responses to our own searches) are
 * silently ignored — the
 * {@link com.frostwire.search.relay.DistributedSearchPerformer}'s transient
 * listener handles those.
 *
 * <p><b>Multi-hop (dual-envelope):</b> the requester signature covers only the
 * immutable query envelope. Forwarding uses {@link RemoteSearchRequest#withNextHop}
 * which preserves that signature and only mutates {@code ttl}/{@code path}.
 * Do <b>not</b> re-sign with the forwarder key.
 *
 * <p>Rate-limits per-source to prevent flood/amplification attacks. Each
 * source public key is limited to {@link #MAX_REQUESTS_PER_MINUTE} search
 * requests per minute.
 *
 * <p>Also demuxes {@link MeshProtocolId#METADATA} (TORRENT_FETCH). Dropping
 * non-SEARCH frames here would make mesh metadata fetch time out and fall
 * back to a magnet that cannot complete over cellular.
 */
public final class IncomingSearchRequestHandler implements DistributedSearchTransport.PayloadListener,
        LeafPromotionManager.ForwardingTarget {

    private static final Logger LOG = Logger.getLogger(IncomingSearchRequestHandler.class);

    /**
     * Dual-envelope multi-hop enabled (DESIGN_RELAY_REGISTRY §8.4 / §14).
     * Forward preserves requester query signature; hop fields only.
     */
    public static final boolean MULTI_HOP_FORWARDING_ENABLED = true;

    /** Maximum incoming search requests per source per minute. */
    private static final int MAX_REQUESTS_PER_MINUTE = 30;

    /**
     * Maximum total .torrent bytes served per metadata request
     * (anti-amplification: bounds chunked RELAY sends per request).
     * Search responses are naturally tiny (rows capped by request limit) and
     * never approach this; only full .torrent payloads (Protocol #3 METADATA)
     * are measured against it. Over-cap responses are REJECTED with a single
     * signed TOO_LARGE error frame (fail closed) — matches
     * {@link TorrentMetadataResponse#MAX_TORRENT_BYTES} (256KB) so legitimate
     * large torrents flow while unbounded amplification stays capped.
     */
    public static final int METADATA_MAX_BYTES = 256 * 1024;

    /**
     * Maximum peers a single request is forwarded to (M — anti-amplification).
     * Live value: {@link IceBridgeTopology#searchPeerFanout()}.
     */
    private static final int MAX_FORWARD_TARGETS =
            IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT;

    private final DistributedSearchTransport transport;
    private final RelaySearchService searchService;
    private final PeerDirectory peerDirectory;
    private final IdentityKeys identity;
    private final LocalIndex localIndex;
    private final ShareVisibilityPolicy visibility;
    private volatile boolean stopped;
    private final ThreadLocal<Long> responseDeadline = new ThreadLocal<>();
    private final ThreadLocal<Long> responseGeneration = new ThreadLocal<>();
    private final AtomicLong generation = new AtomicLong();
    private final Set<DistributedSearchTransport.SendOperation> activeSends = new HashSet<>();
    /**
     * Role-gated forwarding (Gnutella leaf model). When false, this node
     * answers from its local index but {@link #forwardRequest} drops every
     * forward. Defaults to true (historical behavior); wiring sets it from
     * the configured node role ({@code forwardingEnabled = role != CLIENT}).
     */
    private volatile boolean forwardingEnabled = true;
    /**
     * Forward fanout cap for promoted leaves. Non-positive means the live
     * topology default.
     */
    private volatile int maxForwardTargets;
    private volatile TorrentMetadataProvider torrentMetadataProvider;
    private final RateLimiter rateLimiter = new RateLimiter(MAX_REQUESTS_PER_MINUTE, 0.5);
    private final RateLimiter ingress = new RateLimiter(100, 100, 1, 60_000);
    private final RateLimiter metadataWork = new RateLimiter(4 * METADATA_MAX_BYTES, METADATA_MAX_BYTES, 1, 60_000);
    private final RateLimiter outboundBytes = new RateLimiter(2 * 1024 * 1024, 1024 * 1024, 1, 60_000);
    private final Map<String, Long> replay = new LinkedHashMap<>();
    private static final int MAX_REPLAY_ENTRIES = 4096;

    /**
     * Bounded LRU of fully-signed .torrent responses keyed by infohash hex.
     * Positive signed chunks are reusable across requests because the v3 signature
     * domain excludes the per-request nonce and timestamp (content is
     * immutable per infohash — replay can only deliver identical bytes).
     * A hit serves without provider I/O, re-splitting, re-signing, or
     * re-encoding the signature: templates are restamped with the live nonce
     * via {@link TorrentMetadataResponse#withNonceTimestamp} and re-encoded.
     * Negative responses are never cached. Max 32 x 256KB payload bytes,
     * plus bounded chunk/signature overhead; entries expire after five minutes.
     */
    private static final int TORRENT_CACHE_MAX_ENTRIES = 32;
    private final Map<String, CachedMetadata> torrentCache =
            Collections.synchronizedMap(new LinkedHashMap<String, CachedMetadata>(
                    TORRENT_CACHE_MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedMetadata> eldest) {
                    return size() > TORRENT_CACHE_MAX_ENTRIES;
                }
            });

    /** Cached fully-signed response template. Misses are not cached. */
    private static final class CachedMetadata {
        final List<TorrentMetadataResponse> signedChunks;
        final int length;
        final TorrentMetadataProvider provider;
        final long expiresNanos = System.nanoTime() + 300_000_000_000L;

        CachedMetadata(List<TorrentMetadataResponse> signedChunks, int length, TorrentMetadataProvider provider) {
            this.signedChunks = signedChunks;
            this.length = length;
            this.provider = provider;
        }
    }

    public IncomingSearchRequestHandler(DistributedSearchTransport transport,
                                        RelaySearchService searchService) {
        this(transport, searchService, null, null, null);
    }

    public IncomingSearchRequestHandler(DistributedSearchTransport transport,
                                        RelaySearchService searchService,
                                        PeerDirectory peerDirectory,
                                        IdentityKeys identity) {
        this(transport, searchService, peerDirectory, identity, null);
    }

    public IncomingSearchRequestHandler(DistributedSearchTransport transport,
                                        RelaySearchService searchService,
                                        PeerDirectory peerDirectory,
                                        IdentityKeys identity,
                                        LocalIndex localIndex) {
        this(transport, searchService, peerDirectory, identity, localIndex,
                searchService == null ? null : searchService::isPubliclyShared);
    }

    public IncomingSearchRequestHandler(DistributedSearchTransport transport,
                                        RelaySearchService searchService,
                                        PeerDirectory peerDirectory,
                                        IdentityKeys identity,
                                        LocalIndex localIndex,
                                        ShareVisibilityPolicy visibility) {
        if (transport == null) {
            throw new IllegalArgumentException("transport is null");
        }
        if (searchService == null) {
            throw new IllegalArgumentException("searchService is null");
        }
        this.transport = transport;
        this.searchService = searchService;
        this.peerDirectory = peerDirectory;
        this.identity = identity;
        this.localIndex = localIndex;
        this.visibility = visibility;
    }

    public void start() {
        stopped = false;
        transport.addListener(this);
        LOG.info("IncomingSearchRequestHandler started");
    }

    /** Answerer for TORRENT_FETCH (Protocol #3 METADATA) requests, or null. */
    public void setTorrentMetadataProvider(TorrentMetadataProvider provider) {
        synchronized (torrentCache) {
            this.torrentMetadataProvider = provider;
            torrentCache.clear();
        }
    }

    /**
     * Enables or disables multi-hop forwarding. A CLIENT-role (leaf) node
     * calls {@code setForwardingEnabled(false)} so incoming requests are
     * still answered locally but never forwarded. Promoted leaves re-enable
     * with {@link #setMaxForwardTargets} capped.
     */
    @Override
    public void setForwardingEnabled(boolean forwardingEnabled) {
        this.forwardingEnabled = forwardingEnabled;
    }

    /**
     * Caps forward targets below the topology default. Non-positive restores
     * the live topology value.
     */
    @Override
    public void setMaxForwardTargets(int maxForwardTargets) {
        if (maxForwardTargets < 0) {
            throw new IllegalArgumentException("maxForwardTargets must be >= 0");
        }
        this.maxForwardTargets = maxForwardTargets;
    }

    public void stop() {
        stopped = true;
        generation.incrementAndGet();
        transport.removeListener(this);
        synchronized (activeSends) {
            for (DistributedSearchTransport.SendOperation operation : activeSends) {
                operation.cancel();
            }
        }
        torrentCache.clear();
        LOG.info("IncomingSearchRequestHandler stopped");
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs) {
        onPayload(sourcePub, payload, receivedMs, MeshProtocolId.SEARCH);
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs, int protocolId) {
        onPayloadBefore(sourcePub, payload, protocolId, System.nanoTime() + 30_000_000_000L, generation.get());
    }

    long generation() {
        return generation.get();
    }

    void onPayloadBefore(byte[] sourcePub, byte[] payload, int protocolId, long deadlineNanos, long workGeneration) {
        if (stopped || payload == null || payload.length > 16 * 1024
                || workGeneration != generation.get() || System.nanoTime() - deadlineNanos >= 0) {
            return;
        }
        responseDeadline.set(deadlineNanos);
        responseGeneration.set(workGeneration);
        try {
            if (canSend()) {
                handlePayload(sourcePub, payload, protocolId);
            }
        } finally {
            responseDeadline.remove();
            responseGeneration.remove();
        }
    }

    private void handlePayload(byte[] sourcePub, byte[] payload, int protocolId) {
        if (MeshProtocolId.effective(protocolId) == MeshProtocolId.METADATA) {
            handleTorrentMetadataPayload(sourcePub, payload);
            return;
        }
        // Only Protocol #1 (search) is handled here; other protocols are ignored.
        if (MeshProtocolId.effective(protocolId) != MeshProtocolId.SEARCH) {
            return;
        }
        RemoteSearchRequest request = SearchPayloadCodec.decodeRequest(payload);
        if (request != null) {
            handleSearchRequest(request, sourcePub);
            return;
        }

        RemoteCatalogBrowseRequest browseRequest =
                SearchPayloadCodec.decodeCatalogBrowseRequest(payload);
        if (browseRequest != null) {
            handleCatalogBrowseRequest(browseRequest, sourcePub);
        }
    }

    private void handleTorrentMetadataPayload(byte[] sourcePub, byte[] payload) {
        TorrentMetadataRequest request = SearchPayloadCodec.decodeTorrentMetadataRequest(payload);
        if (request == null) {
            return;
        }
        if (identity == null) {
            return;
        }
        try {
            long nowSec = System.currentTimeMillis() / 1000L;
            if (request.nonce().length != 32
                    || request.timestamp() < nowSec - TorrentMetadataRequest.MAX_TIMESTAMP_SKEW_SEC
                    || request.timestamp() > nowSec + TorrentMetadataRequest.MAX_TIMESTAMP_SKEW_SEC
                    || !ingress.tryAcquire("requests")) {
                return;
            }
            if (!request.verifySignature()) {
                LOG.debug("Rejected torrent metadata request: bad signature ih="
                        + request.infoHashHex() + " requester=" + Hex.encode(request.requesterPub())
                        + " timeWindowSkewUnknown");
                return;
            }
            if (!admit("metadata", request.requesterPub(), request.nonce())) {
                return;
            }
            sendTorrentMetadataResponse(request);
        } catch (Throwable t) {
            LOG.warn("Failed to process torrent metadata request", t);
        }
    }

    /**
     * Answer a verified {@link TorrentMetadataRequest} with the holder-signed
     * full .torrent bytes in chunks sized for the ~1 KB mesh RELAY frame, or
     * a signed error frame the requester can fast-fallback on.
     */
    private void sendTorrentMetadataResponse(TorrentMetadataRequest request) throws GeneralSecurityException {
        String ihHex = request.infoHashHex();
        long ts = System.currentTimeMillis() / 1000L;
        TorrentMetadataProvider provider = torrentMetadataProvider;
        if (!isMetadataPublic(provider, request.infoHash())) {
            torrentCache.remove(ihHex);
            sendSignedMetadataChunk(TorrentMetadataResponse.buildError(
                    request.nonce(), request.infoHash(), ts, TorrentMetadataResponse.ERR_NOT_FOUND),
                    request.requesterPub());
            return;
        }
        CachedMetadata cached = torrentCache.get(ihHex);
        if (cached != null && cached.provider == provider && System.nanoTime() - cached.expiresNanos < 0) {
            LOG.debug("TORRENT_FETCH answer (cached) ih=" + ihHex + " bytes=" + cached.length);
            sendCachedTemplates(cached, request, ts);
            return;
        }
        torrentCache.remove(ihHex);
        // Reserve the worst-case signing cost before provider/native work. No refund on failure.
        if (!metadataWork.tryAcquire("metadata", METADATA_MAX_BYTES)) {
            return;
        }
        byte[] torrentBytes = provider.torrentBytes(request.infoHash());
        if (!isMetadataPublic(provider, request.infoHash())) {
            torrentCache.remove(ihHex);
            return;
        }
        if (torrentBytes == null) {
            // Never cache misses: the torrent may arrive after this request.
            LOG.info("TORRENT_FETCH miss ih=" + ihHex
                    + " requester=" + Hex.encode(request.requesterPub()).substring(0, 12));
            sendSignedMetadataChunk(TorrentMetadataResponse.buildError(
                    request.nonce(), request.infoHash(), ts, TorrentMetadataResponse.ERR_NOT_FOUND),
                    request.requesterPub());
            return;
        }
        if (torrentBytes.length > METADATA_MAX_BYTES) {
            // Negative signatures bind the current request; never restamp/cache them.
            sendSignedMetadataChunk(TorrentMetadataResponse.buildError(
                    request.nonce(), request.infoHash(), ts, TorrentMetadataResponse.ERR_TOO_LARGE),
                    request.requesterPub());
            LOG.info("TORRENT_FETCH over cap ih=" + ihHex
                    + " bytes=" + torrentBytes.length);
            return;
        }
        List<TorrentMetadataResponse> signed = new ArrayList<>();
        for (TorrentMetadataResponse chunk :
                TorrentMetadataResponse.buildChunks(request.nonce(), request.infoHash(), ts, torrentBytes)) {
            if (!canSend() || !isMetadataPublic(provider, request.infoHash())) {
                return;
            }
            signed.add(signMetadataChunk(chunk));
        }
        CachedMetadata hit = new CachedMetadata(
                Collections.unmodifiableList(signed), torrentBytes.length, provider);
        synchronized (torrentCache) {
            if (provider != torrentMetadataProvider || !canSend()) {
                return;
            }
            torrentCache.put(ihHex, hit);
        }
        LOG.info("TORRENT_FETCH answer ih=" + ihHex
                + " bytes=" + torrentBytes.length
                + " requester=" + Hex.encode(request.requesterPub()).substring(0, 12));
        sendCachedTemplates(hit, request, ts);
    }

    /** Visible for tests: current torrent payload cache size. */
    public int torrentCacheSize() {
        return torrentCache.size();
    }

    /**
     * Serve cached signed templates to a new request: restamp each with the
     * live nonce/timestamp (outside the positive v3 signature domain), encode,
     * and send. Zero Ed25519 SIGNs on a hit.
     */
    private void sendCachedTemplates(
            CachedMetadata cached, TorrentMetadataRequest request, long ts) {
        for (TorrentMetadataResponse template : cached.signedChunks) {
            if (!isMetadataPublic(cached.provider, request.infoHash())) {
                torrentCache.remove(request.infoHashHex());
                return;
            }
            if (!sendPresignedChunk(
                    template.withNonceTimestamp(request.nonce(), ts), request.requesterPub())) {
                return;
            }
        }
    }

    /** Sign one unsigned chunk and send it (cache-miss path only). */
    private void sendSignedMetadataChunk(TorrentMetadataResponse unsigned, byte[] requesterPub)
            throws GeneralSecurityException {
        if (canSend()) {
            sendPresignedChunk(signMetadataChunk(unsigned), requesterPub);
        }
    }

    private TorrentMetadataResponse signMetadataChunk(TorrentMetadataResponse unsigned)
            throws GeneralSecurityException {
        Signature signer = IdentityKeys.softwareSignature("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(unsigned.canonicalBytes());
        return TorrentMetadataResponse.builder()
                .version(unsigned.version())
                .nonce(unsigned.nonce())
                .infoHash(unsigned.infoHash())
                .payloadDigest(unsigned.payloadDigest())
                .chunkIndex(unsigned.chunkIndex())
                .finalChunk(unsigned.isFinalChunk())
                .timestamp(unsigned.timestamp())
                .data(unsigned.data())
                .error(unsigned.error())
                .signature(signer.sign())
                .build();
    }

    private boolean sendPresignedChunk(TorrentMetadataResponse signed, byte[] requesterPub) {
        byte[] bytes = SearchPayloadCodec.encodeTorrentMetadataResponse(signed);
        if (!send(requesterPub, MeshProtocolId.METADATA, bytes)) {
            LOG.debug("Could not route torrent metadata chunk ci=" + signed.chunkIndex()
                    + " to requester " + Hex.encode(requesterPub));
            return false;
        }
        return true;
    }

    private boolean isMetadataPublic(TorrentMetadataProvider provider, byte[] hash) {
        if (provider == null || provider != torrentMetadataProvider || !canSend()) {
            return false;
        }
        try {
            return ShareVisibility.isPubliclyShared(Hex.encode(hash), visibility)
                    && provider.isPubliclyShared(hash);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean canSend() {
        Long deadline = responseDeadline.get();
        Long workGeneration = responseGeneration.get();
        return !stopped && !Thread.currentThread().isInterrupted()
                && (workGeneration == null || workGeneration == generation.get())
                && (deadline == null || System.nanoTime() - deadline < 0);
    }

    private boolean send(byte[] target, int protocol, byte[] payload) {
        if (!canSend() || !outboundBytes.tryAcquire("outbound", payload.length)) {
            return false;
        }
        Long deadline = responseDeadline.get();
        DistributedSearchTransport.SendOperation operation = transport.createSend(target, protocol, payload,
                deadline == null ? System.nanoTime() + 30_000_000_000L : deadline);
        synchronized (activeSends) {
            if (!canSend() || activeSends.size() >= 64) {
                operation.cancel();
                return false;
            }
            activeSends.add(operation);
        }
        try {
            return operation.execute();
        } finally {
            synchronized (activeSends) {
                activeSends.remove(operation);
            }
        }
    }

    /**
     * Cheap pre-verify spam drop via the peer directory (null-guarded —
     * constructors allow a null directory, which means no spam knowledge).
     * Unknown peers are never spam.
     */
    private boolean isKnownSpam(RemoteSearchRequest request) {
        PeerDirectory directory = this.peerDirectory;
        if (directory == null) {
            return false;
        }
        byte[] requesterPub = request.requesterPub();
        if (requesterPub == null) {
            return false;
        }
        Optional<PeerDirectory.PeerInfo> info = directory.get(requesterPub);
        return info.isPresent() && info.get().isSpam();
    }

    private void handleSearchRequest(RemoteSearchRequest request, byte[] sourcePub) {
        if (request == null) {
            return;
        }
        // Cheap rejects first: a ttl-exhausted request can neither be
        // answered usefully nor forwarded, and a known spammer is dropped
        // before paying verify + index work.
        if (request.ttl() <= 0) {
            LOG.debug("Dropping search request: ttl exhausted keywords=\""
                    + request.keywords() + "\"");
            return;
        }
        if (isKnownSpam(request)) {
            LOG.debug("Dropping search request from known spam peer keywords=\""
                    + request.keywords() + "\"");
            return;
        }
        // Admission authenticates before charging the requester and deduplicates lookup/fanout.
        try {
            Optional<RemoteSearchResponse> response = searchService.handle(request);
            if (response.isEmpty()) {
                return;
            }
            if (response.isPresent()) {
                RemoteSearchResponse r = response.get();
                // Pure FORWARDER / EmptyLocalIndex: never send 0-row finals. An empty
                // final from the hub completes DistributedSearchPerformer's latch for
                // that peer and discards the later signed answer from index holders
                // (3-node topology: Android → EC2 → desktop). Still forward below.
                boolean pureForwarder = localIndex instanceof EmptyLocalIndex;
                if (!pureForwarder || !r.rows().isEmpty()) {
                    sendSearchResponse(request.requesterPub(), r);
                } else {
                    LOG.debug(
                            "Suppressing empty search response from pure forwarder keywords=\""
                                    + request.keywords()
                                    + "\"");
                }
            }
        } catch (Throwable t) {
            LOG.debug("IncomingSearchRequestHandler failed to process request", t);
            return;
        }

        if (MULTI_HOP_FORWARDING_ENABLED
                && request.ttl() > 1
                && peerDirectory != null
                && identity != null
                && searchService.claimForward(request)) {
            try {
                forwardRequest(request, sourcePub);
            } catch (Throwable t) {
                LOG.debug("IncomingSearchRequestHandler forwarding failed", t);
            }
        }
    }

    /**
     * Stream large result sets as signed RESULT chunks ending with
     * {@code final=true}. Small sets stay a single frame.
     */
    private void sendSearchResponse(byte[] requesterPub, RemoteSearchResponse full) {
        List<RemoteSearchResponse.Row> rows = full.rows();
        for (RemoteSearchResponse.Row row : rows) {
            if (!searchService.isPubliclyShared(Hex.encode(row.infoHash)) || !canSend()) {
                return;
            }
        }
        int chunkSize = RemoteSearchResponse.DEFAULT_STREAM_CHUNK_SIZE;
        if (rows.size() <= chunkSize || identity == null) {
            byte[] responseBytes = SearchPayloadCodec.encodeResponse(full);
            if (!send(requesterPub, MeshProtocolId.SEARCH, responseBytes)) {
                LOG.debug("Could not route search response to requester "
                        + Hex.encode(requesterPub));
            }
            return;
        }
        int total = rows.size();
        int chunks = (total + chunkSize - 1) / chunkSize;
        long ts = full.timestamp();
        byte[] nonce = full.nonce();
        for (int i = 0; i < chunks; i++) {
            if (!canSend()) {
                return;
            }
            int from = i * chunkSize;
            int to = Math.min(from + chunkSize, total);
            boolean isFinal = i == chunks - 1;
            try {
                RemoteSearchResponse.Builder b = RemoteSearchResponse.builder()
                        .nonce(nonce)
                        .timestamp(ts)
                        .chunkIndex(i)
                        .finalChunk(isFinal);
                for (int r = from; r < to; r++) {
                    RemoteSearchResponse.Row row = rows.get(r);
                    if (!searchService.isPubliclyShared(Hex.encode(row.infoHash))) {
                        return;
                    }
                    b.addRow(row.infoHash, row.name, row.sizeBytes, row.fileCount,
                            row.publisherEd25519Pub, row.publisherNodeId, row.matchedFile, row.seederEndpoints);
                }
                RemoteSearchResponse unsigned = b.signature(new byte[64]).build();
                Signature signer = IdentityKeys.softwareSignature("Ed25519");
                signer.initSign(identity.ed25519().getPrivate());
                signer.update(unsigned.canonicalBytes());
                RemoteSearchResponse chunk = b.signature(signer.sign()).build();
                byte[] bytes = SearchPayloadCodec.encodeResponse(chunk);
                if (!send(requesterPub, MeshProtocolId.SEARCH, bytes)) {
                    LOG.debug("Could not route search chunk " + i + " to "
                            + Hex.encode(requesterPub));
                    return;
                }
            } catch (Throwable t) {
                LOG.debug("Failed to stream search chunk " + i, t);
                return;
            }
        }
    }

    private void handleCatalogBrowseRequest(RemoteCatalogBrowseRequest request,
                                            byte[] sourcePub) {
        if (localIndex == null || identity == null
                || !Arrays.equals(request.targetPub(), identity.ed25519PubRaw())) {
            return;
        }

        try {
            long nowSec = System.currentTimeMillis() / 1000L;
            if (request.nonce().length != 32
                    || request.timestamp() < nowSec - RemoteCatalogBrowseRequest.MAX_TIMESTAMP_SKEW_SEC
                    || request.timestamp() > nowSec + RemoteCatalogBrowseRequest.MAX_TIMESTAMP_SKEW_SEC
                    || !ingress.tryAcquire("requests")) {
                return;
            }
            if (!verifyCatalogBrowseSignature(request)) {
                LOG.debug("Rejected catalog browse: bad signature");
                return;
            }
            // Rate-limit only after verify, by requesterPub (authoritative identity).
            String requesterKey = Hex.encode(request.requesterPub());
            if (!admit("browse", request.requesterPub(), request.nonce())) {
                LOG.debug("IncomingSearchRequestHandler: rate-limited catalog browse from "
                        + requesterKey);
                return;
            }
            byte[] responseBytes = buildCatalogBrowseResponse();
            if (responseBytes != null) {
                send(request.requesterPub(), MeshProtocolId.SEARCH, responseBytes);
            }
        } catch (Throwable t) {
            LOG.debug("IncomingSearchRequestHandler failed to process catalog browse", t);
        }
    }

    private byte[] buildCatalogBrowseResponse() {
        try {
            List<LocalSharedTorrent> torrents = localIndex.listAll();
            if (torrents == null) {
                torrents = new ArrayList<>();
            }
            List<RemoteIndexFetcher.RemoteTorrentEntry> entries = new ArrayList<>();
            for (LocalSharedTorrent t : torrents) {
                if (entries.size() >= RemoteSearchRequest.MAX_LIMIT || !canSend()) {
                    break;
                }
                if (t == null || !ShareVisibility.isPubliclyShared(t.infoHashHex(), visibility)) {
                    continue;
                }
                entries.add(new RemoteIndexFetcher.RemoteTorrentEntry(
                        t.infoHashHex(), t.name(), t.sizeBytes(), t.fileCount()));
            }
            String pubB64 = Base64.getEncoder().withoutPadding()
                    .encodeToString(identity.ed25519PubRaw());
            long ts = System.currentTimeMillis() / 1000L;
            byte[] canonical = RemoteIndexFetcher.manifestCanonicalBytes(
                    RemoteIndexFetcher.MANIFEST_VERSION, pubB64, ts, entries);
            PrivateKey priv = identity.ed25519().getPrivate();
            Signature signer = IdentityKeys.softwareSignature("Ed25519");
            signer.initSign(priv);
            signer.update(canonical);
            byte[] sig = signer.sign();
            return RemoteIndexFetcher.buildManifestJson(
                    RemoteIndexFetcher.MANIFEST_VERSION, pubB64, ts, entries, sig);
        } catch (Throwable t) {
            LOG.debug("buildCatalogBrowseResponse failed", t);
            return null;
        }
    }

    private static boolean verifyCatalogBrowseSignature(RemoteCatalogBrowseRequest request) {
        try {
            byte[] raw = request.requesterPub();
            if (raw == null || raw.length != 32) {
                return false;
            }
            byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
            byte[] encoded = new byte[prefix.length + raw.length];
            System.arraycopy(prefix, 0, encoded, 0, prefix.length);
            System.arraycopy(raw, 0, encoded, prefix.length, raw.length);
            PublicKey pub = IdentityKeys.softwareKeyFactory("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            Signature verifier = IdentityKeys.softwareSignature("Ed25519");
            verifier.initVerify(pub);
            verifier.update(request.canonicalBytes());
            return verifier.verify(request.signature());
        } catch (GeneralSecurityException e) {
            LOG.debug("Catalog browse signature verification threw", e);
            return false;
        }
    }

    private void forwardRequest(RemoteSearchRequest request, byte[] sourcePub) {
        if (!forwardingEnabled) {
            LOG.debug("Dropping search forward: forwarding disabled (CLIENT leaf role)");
            return;
        }
        byte[] ownPub = identity.ed25519PubRaw();
        int hopsSoFar = request.path() != null ? request.path().length : 0;
        int newTtl = IceBridgeTopology.get().clampRemainingTtl(hopsSoFar, request.ttl() - 1);
        if (newTtl <= 0 || request.isLoop(ownPub)) {
            return;
        }
        int m = IceBridgeTopology.get().searchPeerFanout();
        if (maxForwardTargets > 0) {
            m = Math.min(m, maxForwardTargets);
        }
        Set<String> excludeHex = new HashSet<>();
        if (request.path() != null) {
            for (byte[] hop : request.path()) {
                if (hop != null) {
                    excludeHex.add(Hex.encode(hop));
                }
            }
        }
        if (sourcePub != null) {
            excludeHex.add(Hex.encode(sourcePub));
        }
        if (request.requesterPub() != null) {
            excludeHex.add(Hex.encode(request.requesterPub()));
        }
        if (ownPub != null) {
            excludeHex.add(Hex.encode(ownPub));
        }
        List<PeerDirectory.PeerInfo> sampled =
                peerDirectory.sampleVerified(m, excludeHex, NodeCapabilities.NONE,
                        ThreadLocalRandom.current());
        int forwarded = 0;
        for (PeerDirectory.PeerInfo peer : sampled) {
            if (forwarded >= m) {
                break;
            }
            byte[] peerPub = peer.peerPub();
            try {
                // Dual-envelope: preserve requester query signature; only hop fields change.
                RemoteSearchRequest nextHop = request.withNextHop(ownPub, newTtl);
                byte[] forwardedPayload = SearchPayloadCodec.encodeRequest(nextHop);
                if (send(peerPub, MeshProtocolId.SEARCH, forwardedPayload)) {
                    forwarded++;
                    LOG.debug("Forwarded search hop ttl=" + newTtl + " to "
                            + Hex.encode(peerPub).substring(0, 12) + "…");
                }
            } catch (Throwable t) {
                LOG.debug("Failed to forward search request to peer", t);
            }
        }
    }

    private boolean admit(String protocol, byte[] requester, byte[] nonce) {
        String key = protocol + ':' + Hex.encode(requester) + ':' + Hex.encode(nonce);
        synchronized (replay) {
            long now = System.nanoTime();
            replay.values().removeIf(expiry -> now - expiry >= 0);
            if (replay.containsKey(key) || replay.size() >= MAX_REPLAY_ENTRIES
                    || !rateLimiter.tryAcquire(requester)) {
                return false;
            }
            replay.put(key, now + (2 * RemoteSearchRequest.MAX_TIMESTAMP_SKEW_SEC + 1) * 1_000_000_000L);
            return true;
        }
    }

    /** Called by the transport maintenance timer, including during idle periods. */
    public void evictIdle() {
        searchService.evictIdle();
        rateLimiter.evictIdle(10 * 60_000L);
        synchronized (replay) {
            long now = System.nanoTime();
            replay.values().removeIf(expiry -> now - expiry >= 0);
        }
        synchronized (torrentCache) {
            long now = System.nanoTime();
            torrentCache.values().removeIf(entry -> now - entry.expiresNanos >= 0);
        }
    }
}
