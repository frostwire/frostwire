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
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteCatalogBrowseRequest;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.search.relay.RemoteSearchRequest;
import com.frostwire.search.relay.RemoteSearchResponse;
import com.frostwire.search.relay.SearchPayloadCodec;
import com.frostwire.search.relay.TorrentMetadataProvider;
import com.frostwire.search.relay.TorrentMetadataRequest;
import com.frostwire.search.relay.TorrentMetadataResponse;
import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
    private final ConcurrentHashMap<String, RateBucket> rateMap = new ConcurrentHashMap<>();

    /**
     * Bounded LRU of fully-signed .torrent responses keyed by infohash hex.
     * Signed chunks are reusable across requests because the v2 signature
     * domain excludes the per-request nonce and timestamp (content is
     * immutable per infohash — replay can only deliver identical bytes).
     * A hit serves without provider I/O, re-splitting, re-signing, or
     * re-encoding the signature: templates are restamped with the live nonce
     * via {@link TorrentMetadataResponse#withNonceTimestamp} and re-encoded.
     * Only hits and over-cap templates are cached. Misses are never cached —
     * a torrent may arrive after the first miss. Max 32 x 256KB = 8MB.
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
        final List<TorrentMetadataResponse> signedChunks; // 1 frame when tooLarge
        final boolean tooLarge;
        final int length;

        CachedMetadata(List<TorrentMetadataResponse> signedChunks, boolean tooLarge, int length) {
            this.signedChunks = signedChunks;
            this.tooLarge = tooLarge;
            this.length = length;
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
    }

    public void start() {
        transport.addListener(this);
        LOG.info("IncomingSearchRequestHandler started");
    }

    /** Answerer for TORRENT_FETCH (Protocol #3 METADATA) requests, or null. */
    public void setTorrentMetadataProvider(TorrentMetadataProvider provider) {
        this.torrentMetadataProvider = provider;
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
        transport.removeListener(this);
        LOG.info("IncomingSearchRequestHandler stopped");
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs) {
        onPayload(sourcePub, payload, receivedMs, MeshProtocolId.SEARCH);
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs, int protocolId) {
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
            String requesterKey = Hex.encode(request.requesterPub());
            if (!tryAcquire(requesterKey)) {
                LOG.debug("Rate-limited torrent metadata request from " + requesterKey);
                return;
            }
            if (!request.verifySignature()) {
                LOG.debug("Rejected torrent metadata request: bad signature ih="
                        + request.infoHashHex() + " requester=" + Hex.encode(request.requesterPub())
                        + " timeWindowSkewUnknown");
                return;
            }
            long nowSec = System.currentTimeMillis() / 1000L;
            long skew = Math.abs(nowSec - request.timestamp());
            if (skew > TorrentMetadataRequest.MAX_TIMESTAMP_SKEW_SEC) {
                LOG.debug("Rejected torrent metadata request: timestamp skew " + skew
                        + "s (max " + TorrentMetadataRequest.MAX_TIMESTAMP_SKEW_SEC
                        + "s) ih=" + request.infoHashHex()
                        + " requester=" + Hex.encode(request.requesterPub()).substring(0, 12));
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
        CachedMetadata cached = torrentCache.get(ihHex);
        if (cached != null) {
            if (cached.tooLarge) {
                LOG.info("TORRENT_FETCH over cap (cached) ih=" + ihHex
                        + " bytes=" + cached.length);
            } else {
                LOG.info("TORRENT_FETCH answer (cached) ih=" + ihHex
                        + " bytes=" + cached.length
                        + " requester=" + Hex.encode(request.requesterPub()).substring(0, 12));
            }
            sendCachedTemplates(cached, request, ts);
            return;
        }
        TorrentMetadataProvider provider = torrentMetadataProvider;
        byte[] torrentBytes = provider == null ? null : provider.torrentBytes(request.infoHash());
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
            // Safe to cache: infohash is the content hash, so these bytes
            // are immutable and every future request for ihHex is over cap.
            CachedMetadata overCap = new CachedMetadata(
                    java.util.Collections.singletonList(signMetadataChunk(
                            TorrentMetadataResponse.buildError(
                                    request.nonce(), request.infoHash(), ts,
                                    TorrentMetadataResponse.ERR_TOO_LARGE))),
                    true, torrentBytes.length);
            torrentCache.put(ihHex, overCap);
            LOG.info("TORRENT_FETCH over cap ih=" + ihHex
                    + " bytes=" + torrentBytes.length);
            sendCachedTemplates(overCap, request, ts);
            return;
        }
        List<TorrentMetadataResponse> signed = new ArrayList<>();
        for (TorrentMetadataResponse chunk :
                TorrentMetadataResponse.buildChunks(request.nonce(), request.infoHash(), ts, torrentBytes)) {
            signed.add(signMetadataChunk(chunk));
        }
        CachedMetadata hit = new CachedMetadata(
                java.util.Collections.unmodifiableList(signed), false, torrentBytes.length);
        torrentCache.put(ihHex, hit);
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
     * live nonce/timestamp (free — outside the v2 signature domain), encode,
     * and send. Zero Ed25519 SIGNs on a hit.
     */
    private void sendCachedTemplates(
            CachedMetadata cached, TorrentMetadataRequest request, long ts) {
        for (TorrentMetadataResponse template : cached.signedChunks) {
            sendPresignedChunk(
                    template.withNonceTimestamp(request.nonce(), ts), request.requesterPub());
        }
    }

    /** Sign one unsigned chunk and send it (cache-miss path only). */
    private void sendSignedMetadataChunk(TorrentMetadataResponse unsigned, byte[] requesterPub)
            throws GeneralSecurityException {
        sendPresignedChunk(signMetadataChunk(unsigned), requesterPub);
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

    private void sendPresignedChunk(TorrentMetadataResponse signed, byte[] requesterPub) {
        byte[] bytes = SearchPayloadCodec.encodeTorrentMetadataResponse(signed);
        if (!transport.send(requesterPub, MeshProtocolId.METADATA, bytes)) {
            LOG.debug("Could not route torrent metadata chunk ci=" + signed.chunkIndex()
                    + " to requester " + Hex.encode(requesterPub));
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
        // Rate-limit is applied inside RelaySearchService before signature
        // verify, keyed by requesterPub (not transport sourcePub).
        try {
            Optional<RemoteSearchResponse> response = searchService.handle(request);
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
        }

        if (MULTI_HOP_FORWARDING_ENABLED
                && request.ttl() > 0
                && peerDirectory != null
                && identity != null) {
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
        int chunkSize = RemoteSearchResponse.DEFAULT_STREAM_CHUNK_SIZE;
        if (rows.size() <= chunkSize || identity == null) {
            byte[] responseBytes = SearchPayloadCodec.encodeResponse(full);
            if (!transport.send(requesterPub, MeshProtocolId.SEARCH, responseBytes)) {
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
                    b.addRow(row.infoHash, row.name, row.sizeBytes, row.fileCount,
                            row.publisherEd25519Pub, row.publisherNodeId, row.matchedFile);
                }
                RemoteSearchResponse unsigned = b.signature(new byte[64]).build();
                Signature signer = IdentityKeys.softwareSignature("Ed25519");
                signer.initSign(identity.ed25519().getPrivate());
                signer.update(unsigned.canonicalBytes());
                RemoteSearchResponse chunk = b.signature(signer.sign()).build();
            byte[] bytes = SearchPayloadCodec.encodeResponse(chunk);
            if (!transport.send(requesterPub, MeshProtocolId.SEARCH, bytes)) {
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
        if (localIndex == null || identity == null) {
            return;
        }

        try {
            if (!verifyCatalogBrowseSignature(request)) {
                LOG.debug("Rejected catalog browse: bad signature");
                return;
            }
            long nowSec = System.currentTimeMillis() / 1000L;
            long skew = Math.abs(nowSec - request.timestamp());
            if (skew > RemoteCatalogBrowseRequest.MAX_TIMESTAMP_SKEW_SEC) {
                LOG.debug("Rejected catalog browse: timestamp skew " + skew + "s");
                return;
            }
            // Rate-limit only after verify, by requesterPub (authoritative identity).
            String requesterKey = Hex.encode(request.requesterPub());
            if (!tryAcquire(requesterKey)) {
                LOG.debug("IncomingSearchRequestHandler: rate-limited catalog browse from "
                        + requesterKey);
                return;
            }
            byte[] responseBytes = buildCatalogBrowseResponse();
            if (responseBytes != null) {
                transport.send(request.requesterPub(), responseBytes);
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
            List<RemoteIndexFetcher.RemoteTorrentEntry> entries = new ArrayList<>(torrents.size());
            for (LocalSharedTorrent t : torrents) {
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
        // Caller guarantees ttl > 0. Clamping may reduce the remaining ttl
        // to 0; this hop still forwards, and the next hop's ttl guard stops
        // further forwarding (soft-max horizon, LimeWire semantics).
        int newTtl = IceBridgeTopology.get().clampRemainingTtl(hopsSoFar, request.ttl() - 1);
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
                if (transport.send(peerPub, forwardedPayload)) {
                    forwarded++;
                    LOG.debug("Forwarded search hop ttl=" + newTtl + " to "
                            + Hex.encode(peerPub).substring(0, 12) + "…");
                }
            } catch (Throwable t) {
                LOG.debug("Failed to forward search request to peer", t);
            }
        }
    }

    private boolean tryAcquire(String sourceKey) {
        long now = System.currentTimeMillis();
        RateBucket bucket = rateMap.computeIfAbsent(sourceKey, k -> new RateBucket());
        return bucket.tryAcquire(now);
    }

    /** Simple sliding-window rate limiter per source. */
    private static final class RateBucket {
        private final long[] timestamps = new long[MAX_REQUESTS_PER_MINUTE];
        private int index = 0;

        synchronized boolean tryAcquire(long now) {
            long cutoff = now - 60_000;
            // Check if the slot at current index is older than 1 minute.
            if (timestamps[index] < cutoff) {
                timestamps[index] = now;
                index = (index + 1) % MAX_REQUESTS_PER_MINUTE;
                return true;
            }
            return false;
        }
    }
}
