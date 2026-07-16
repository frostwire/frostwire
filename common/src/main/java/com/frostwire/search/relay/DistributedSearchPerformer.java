/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.DefaultTrackers;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Search performer that merges local {@link LocalIndex} results with
 * results returned from authenticated peers via a {@link DistributedSearchTransport}.
 *
 * <p>Unlike the previous direct-TCP implementation, this performer sends signed
 * requests through an asynchronous transport (IceBridge) and collects responses
 * via a temporary listener registered for the duration of the search. The
 * transport's internal poller thread delivers inbound payloads to all
 * registered listeners; the performer filters by nonce and verifies every
 * response against the expected responder's Ed25519 public key.
 *
 * <p>Fail-closed: unreachable peers, invalid signatures, stale responses,
 * wrong nonces, and rate-limited peers simply contribute no results. The
 * listener always receives a single {@code onResults} callback with the merged
 * local + peer result set, even if every peer fails.
 *
 * <p>Source label for all results: {@link #SOURCE_NAME}.
 */
public final class DistributedSearchPerformer implements ISearchPerformer {

    public static final String SOURCE_NAME = "Distributed";

    private static final Logger LOG = Logger.getLogger(DistributedSearchPerformer.class);
    /** M — default search peer fanout from {@link IceBridgeTopology}. */
    private static final int DEFAULT_MAX_PEERS =
            IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT;
    private static final int DEFAULT_LOCAL_LIMIT = 50;
    private static final int DEFAULT_PEER_LIMIT = 25;
    private static final int DEFAULT_PEER_TIMEOUT_SEC = 10;

    private final long token;
    private final String keywords;
    private final LocalIndex localIndex;
    private final PeerDirectory peerDirectory;
    private final IdentityKeys identity;
    private final DistributedSearchTransport transport;
    private final int maxPeers;
    private final int localLimit;
    private final int peerLimit;
    private final int peerTimeoutSec;

    private volatile boolean stopped;
    private volatile SearchListener listener;

    public DistributedSearchPerformer(long token, String keywords,
                                       LocalIndex localIndex,
                                       PeerDirectory peerDirectory,
                                       IdentityKeys identity,
                                       DistributedSearchTransport transport) {
        this(token, keywords, localIndex, peerDirectory, identity, transport,
                DEFAULT_MAX_PEERS, DEFAULT_LOCAL_LIMIT, DEFAULT_PEER_LIMIT, DEFAULT_PEER_TIMEOUT_SEC);
    }

    public DistributedSearchPerformer(long token, String keywords,
                                       LocalIndex localIndex,
                                       PeerDirectory peerDirectory,
                                       IdentityKeys identity,
                                       DistributedSearchTransport transport,
                                       int maxPeers,
                                       int localLimit,
                                       int peerLimit,
                                       int peerTimeoutSec) {
        if (token < 0) {
            throw new IllegalArgumentException("token must be >= 0");
        }
        if (keywords == null) {
            throw new IllegalArgumentException("keywords is null");
        }
        if (localIndex == null) {
            throw new IllegalArgumentException("localIndex is null");
        }
        if (peerDirectory == null) {
            throw new IllegalArgumentException("peerDirectory is null");
        }
        if (identity == null) {
            throw new IllegalArgumentException("identity is null");
        }
        if (transport == null) {
            throw new IllegalArgumentException("transport is null");
        }
        if (maxPeers <= 0) {
            throw new IllegalArgumentException("maxPeers must be > 0");
        }
        if (localLimit <= 0) {
            throw new IllegalArgumentException("localLimit must be > 0");
        }
        if (peerLimit <= 0 || peerLimit > RemoteSearchRequest.MAX_LIMIT) {
            throw new IllegalArgumentException("peerLimit must be in (0, "
                    + RemoteSearchRequest.MAX_LIMIT + "]");
        }
        if (peerTimeoutSec <= 0) {
            throw new IllegalArgumentException("peerTimeoutSec must be > 0");
        }
        this.token = token;
        this.keywords = keywords;
        this.localIndex = localIndex;
        this.peerDirectory = peerDirectory;
        this.identity = identity;
        this.transport = transport;
        this.maxPeers = maxPeers;
        this.localLimit = localLimit;
        this.peerLimit = peerLimit;
        this.peerTimeoutSec = peerTimeoutSec;
    }

    @Override
    public long getToken() {
        return token;
    }

    public String getKeywords() {
        return keywords;
    }

    @Override
    public void perform() {
        SearchListener l = listener;
        if (stopped || l == null) {
            return;
        }
        try {
            List<FileSearchResult> merged = new ArrayList<>(queryLocal());
            if (stopped) {
                return;
            }

            // Prefer peers that advertise SEARCH/INDEX, then rank by keyspace
            // XOR distance so eventual responsibility routing has a foundation.
            List<PeerDirectory.PeerInfo> peers =
                    peerDirectory.topByTrustVerified(maxPeers * 3, NodeCapabilities.SEARCH);
            if (peers.isEmpty()) {
                peers = peerDirectory.topByTrustVerified(maxPeers);
            }
            peers = KeyspaceRouter.rankByKeyspace(keywords, peers);
            if (peers.size() > maxPeers) {
                peers = peers.subList(0, maxPeers);
            }
            if (!peers.isEmpty()) {
                merged.addAll(queryPeers(peers));
            }
            if (stopped) {
                return;
            }
            List<FileSearchResult> deduped = dedupeByInfoHash(merged);
            List<SearchResult> widened = new ArrayList<>(deduped.size());
            widened.addAll(deduped);
            l.onResults(token, widened);
        } catch (Throwable t) {
            LOG.warn("DistributedSearchPerformer failed for token " + token, t);
            if (listener != null && !stopped) {
                listener.onError(token, new SearchError(-1, t.getMessage()));
            }
        }
    }

    @Override
    public void crawl(com.frostwire.search.CrawlableSearchResult sr) {
        // Distributed results are complete; nothing to crawl.
    }

    @Override
    public void stop() {
        stopped = true;
        try {
            if (listener != null) {
                listener.onStopped(token);
            }
        } catch (Throwable t) {
            LOG.warn("Error stopping distributed search: " + t.getMessage());
        }
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public SearchListener getListener() {
        return listener;
    }

    @Override
    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return false;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }

    /**
     * Query the local index and wrap each row as a {@link CompositeFileSearchResult}
     * tagged with {@link #SOURCE_NAME}.
     */
    private List<FileSearchResult> queryLocal() {
        List<LocalSharedTorrent> rows = localIndex.search(keywords, localLimit);
        List<FileSearchResult> out = new ArrayList<>(rows.size());
        for (LocalSharedTorrent t : rows) {
            out.add(LocalSharedTorrentSearchPerformer.toResult(t, SOURCE_NAME));
        }
        return out;
    }

    /**
     * Send signed requests to all peers in parallel, then wait for responses
     * to arrive on the transport within the timeout window.
     *
     * <p>A temporary {@link DistributedSearchTransport.PayloadListener} is
     * registered for the duration of the search. It decodes each inbound
     * payload as a {@link RemoteSearchResponse}, matches the nonce to a
     * pending request, verifies the signature against the expected peer's
     * public key, and collects verified rows.
     */
    private List<FileSearchResult> queryPeers(List<PeerDirectory.PeerInfo> peers) {
        // The latch covers every peer: successful sends will be counted down
        // when a response arrives (or times out); failed sends are counted
        // down immediately. The listener is registered BEFORE any sends so
        // that responses delivered by the transport's poller thread are not
        // missed.
        int totalPeers = peers.size();
        Map<String, PendingRequest> pending = new ConcurrentHashMap<>();
        List<FileSearchResult> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(totalPeers);

        DistributedSearchTransport.PayloadListener responseListener =
                (sourcePub, payload, receivedMs) -> {
                    RemoteSearchResponse response = SearchPayloadCodec.decodeResponse(payload);
                    if (response == null) {
                        return;
                    }
                    String nonceKey = Hex.encode(response.nonce());
                    PendingRequest req = pending.get(nonceKey);
                    if (req == null) {
                        return; // not our response / already completed
                    }
                    if (!SearchResponseVerifier.verify(response, req.request, req.peer.peerPub())) {
                        LOG.warn("DistributedSearchPerformer: response verify failed from "
                                + req.peer.hostname() + " rows=" + response.rows().size()
                                + " final=" + response.isFinalChunk());
                        // Bad frame: drop but keep waiting for a good final
                        // or timeout unless this claimed to be final.
                        if (response.isFinalChunk()) {
                            if (pending.remove(nonceKey, req)) {
                                latch.countDown();
                            }
                        }
                        return;
                    }
                    try {
                        List<FileSearchResult> converted = toResults(response);
                        results.addAll(converted);
                        LOG.info("DistributedSearchPerformer: accepted " + converted.size()
                                + " row(s) from " + req.peer.hostname());
                    } catch (Throwable t) {
                        LOG.warn("Failed to convert search response rows", t);
                    }
                    // Stream: only complete the peer when final=true. Intermediate RESULT chunks accumulate.
                    if (response.isFinalChunk()) {
                        if (pending.remove(nonceKey, req)) {
                            latch.countDown();
                        }
                    }
                };

        transport.addListener(responseListener);
        try {
            for (PeerDirectory.PeerInfo peer : peers) {
                if (stopped) {
                    latch.countDown();
                    continue;
                }
                try {
                    RemoteSearchRequest request = buildSignedRequest(keywords, peerLimit);
                    byte[] payload = SearchPayloadCodec.encodeRequest(request);
                    String nonce = Hex.encode(request.nonce());
                    pending.put(nonce, new PendingRequest(peer, request));
                    if (!transport.send(peer.peerPub(),
                            com.frostwire.search.relay.icebridge.MeshProtocolId.SEARCH, payload)) {
                        pending.remove(nonce);
                        latch.countDown(); // send failed — no response expected
                    }
                } catch (Throwable t) {
                    LOG.debug("Failed to send search request to peer "
                            + peer.hostname() + ":" + peer.utpPort()
                            + " token=" + token, t);
                    latch.countDown();
                }
            }
            latch.await(peerTimeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            transport.removeListener(responseListener);
            // Clear any unresponded entries to prevent memory leak.
            pending.clear();
        }
        return results;
    }

    private RemoteSearchRequest buildSignedRequest(String keywords, int limit) throws GeneralSecurityException {
        byte[] nonce = new byte[32];
        SecureRandom.getInstanceStrong().nextBytes(nonce);
        long timestamp = System.currentTimeMillis() / 1000L;
        byte[] ownPub = identity.ed25519PubRaw();
        // Dual-envelope (v2): sign query envelope only; ttl/path may hop.
        int searchTtl = IceBridgeTopology.get().searchTtl();
        RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                .keywords(keywords)
                .limit(limit)
                .nonce(nonce)
                .ttl(searchTtl)
                .requesterPub(ownPub)
                .path(new byte[][]{ownPub})
                .timestamp(timestamp)
                .signature(new byte[64])
                .build();
        Signature signer = IdentityKeys.softwareSignature("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(unsigned.queryCanonicalBytes());
        byte[] sig = signer.sign();
        return RemoteSearchRequest.builder()
                .keywords(keywords)
                .limit(limit)
                .nonce(nonce)
                .ttl(searchTtl)
                .requesterPub(ownPub)
                .path(new byte[][]{ownPub})
                .timestamp(timestamp)
                .signature(sig)
                .build();
    }

    private static List<FileSearchResult> toResults(RemoteSearchResponse response) {
        List<FileSearchResult> out = new ArrayList<>(response.rows().size());
        for (RemoteSearchResponse.Row row : response.rows()) {
            try {
                FileSearchResult result = toResult(row);
                if (result != null) {
                    out.add(result);
                }
            } catch (Throwable t) {
                LOG.debug("Skipping malformed search result row", t);
            }
        }
        return out;
    }

    private static CompositeFileSearchResult toResult(RemoteSearchResponse.Row row) {
        if (row.name == null || row.name.isEmpty() || row.name.length() > 2048) {
            return null;
        }
        if (row.infoHash == null || row.infoHash.length != 20) {
            return null;
        }
        String name = row.name;
        String infoHashHex = Hex.encode(row.infoHash);
        String magnet = UrlUtils.buildMagnetUrl(infoHashHex, name, DefaultTrackers.MAGNET_URL_PARAMETERS);
        // Validate matchedFile: reject null, empty, or pathologically long values.
        String matchedFile = row.matchedFile;
        if (matchedFile != null) {
            if (matchedFile.isEmpty() || matchedFile.length() > 4096) {
                matchedFile = null;
            }
        }
        String filename = matchedFile != null
                ? matchedFile
                : name + ".torrent";
        return CompositeFileSearchResult.builder()
                .displayName(name)
                .filename(filename)
                .size(row.sizeBytes)
                .detailsUrl(magnet)
                .source(SOURCE_NAME)
                .creationTime(System.currentTimeMillis())
                .preliminary(false)
                .torrent(magnet, infoHashHex, 0, magnet)
                .build();
    }

    /**
     * Deduplicate by infohash, preserving order. The first occurrence
     * (local results are queried first) wins.
     */
    private static List<FileSearchResult> dedupeByInfoHash(List<FileSearchResult> results) {
        Map<String, FileSearchResult> seen = new LinkedHashMap<>();
        for (FileSearchResult r : results) {
            if (!(r instanceof CompositeFileSearchResult)) {
                continue;
            }
            CompositeFileSearchResult c = (CompositeFileSearchResult) r;
            if (c.getTorrentHash().isEmpty()) {
                continue;
            }
            String hash = c.getTorrentHash().get();
            if (!seen.containsKey(hash)) {
                seen.put(hash, r);
            }
        }
        return new ArrayList<>(seen.values());
    }

    /** Associates a sent request with the peer it was sent to. */
    private record PendingRequest(PeerDirectory.PeerInfo peer, RemoteSearchRequest request) {
    }
}
