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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Search performer that merges local {@link LocalIndex} results with
 * results returned from directly authenticated peers.
 *
 * <p>This is the narrow v1 distributed search performer described in
 * the relay design doc: it queries only {@link PeerDirectory#topByTrustVerified(int)}
 * peers, sends signed direct requests with {@code ttl=0} (forwarding disabled),
 * and verifies every response against the expected responder's Ed25519 public
 * key before accepting any rows.
 *
 * <p>Fail-closed: unreachable peers, invalid signatures, stale responses,
 * wrong nonces, and rate-limited peers simply contribute no results. The
 * listener always receives a single {@code onResults} callback with the merged
 * local + peer result set, even if every peer fails.
 *
 * <p>Source label for peer-originated results: {@link #SOURCE_NAME}.
 */
public final class DistributedSearchPerformer implements ISearchPerformer {

    public static final String SOURCE_NAME = "Distributed";

    private static final Logger LOG = Logger.getLogger(DistributedSearchPerformer.class);
    private static final int DEFAULT_MAX_PEERS = 5;
    private static final int DEFAULT_LOCAL_LIMIT = 50;
    private static final int DEFAULT_PEER_LIMIT = 25;
    private static final int DEFAULT_PEER_TIMEOUT_SEC = 10;

    private final long token;
    private final String keywords;
    private final LocalIndex localIndex;
    private final PeerDirectory peerDirectory;
    private final IdentityKeys identity;
    private final OutgoingRelayClient client;
    private final int maxPeers;
    private final int localLimit;
    private final int peerLimit;
    private final int peerTimeoutSec;

    private volatile boolean stopped;
    private volatile SearchListener listener;
    private volatile ExecutorService executor;

    public DistributedSearchPerformer(long token, String keywords,
                                        LocalIndex localIndex,
                                        PeerDirectory peerDirectory,
                                        IdentityKeys identity,
                                        OutgoingRelayClient client) {
        this(token, keywords, localIndex, peerDirectory, identity, client,
                DEFAULT_MAX_PEERS, DEFAULT_LOCAL_LIMIT, DEFAULT_PEER_LIMIT, DEFAULT_PEER_TIMEOUT_SEC);
    }

    public DistributedSearchPerformer(long token, String keywords,
                                        LocalIndex localIndex,
                                        PeerDirectory peerDirectory,
                                        IdentityKeys identity,
                                        OutgoingRelayClient client,
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
        if (client == null) {
            throw new IllegalArgumentException("client is null");
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
        this.client = client;
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
        List<FileSearchResult> merged = new ArrayList<>();
        ExecutorService exec = null;
        try {
            List<FileSearchResult> local = queryLocal();
            if (stopped) {
                return;
            }
            merged.addAll(local);

            List<PeerDirectory.PeerInfo> peers = peerDirectory.topByTrustVerified(maxPeers);
            if (!peers.isEmpty()) {
                exec = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "distributed-search-" + token);
                    t.setDaemon(true);
                    return t;
                });
                this.executor = exec;
                List<Future<List<FileSearchResult>>> futures = new ArrayList<>(peers.size());
                for (PeerDirectory.PeerInfo peer : peers) {
                    futures.add(exec.submit(() -> queryPeer(peer)));
                }
                for (Future<List<FileSearchResult>> f : futures) {
                    if (stopped) {
                        break;
                    }
                    try {
                        List<FileSearchResult> peerResults = f.get(peerTimeoutSec, TimeUnit.SECONDS);
                        if (peerResults != null) {
                            merged.addAll(peerResults);
                        }
                    } catch (java.util.concurrent.TimeoutException e) {
                        f.cancel(true);
                    } catch (Throwable t) {
                        LOG.debug("Peer query task failed for token " + token, t);
                    }
                }
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
        } finally {
            if (exec != null) {
                exec.shutdownNow();
                this.executor = null;
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
        ExecutorService exec = executor;
        if (exec != null) {
            exec.shutdownNow();
        }
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

    private List<FileSearchResult> queryLocal() {
        List<LocalSharedTorrent> rows = localIndex.search(keywords, localLimit);
        List<FileSearchResult> out = new ArrayList<>(rows.size());
        for (LocalSharedTorrent t : rows) {
            out.add(LocalSharedTorrentSearchPerformer.toResult(t, SOURCE_NAME));
        }
        return out;
    }

    private List<FileSearchResult> queryPeer(PeerDirectory.PeerInfo peer) {
        try {
            RemoteSearchRequest request = buildSignedRequest(keywords, peerLimit);
            Optional<RemoteSearchResponse> response = client.send(
                    peer.hostname(), peer.utpPort(), request, peer.peerPub());
            if (!response.isPresent()) {
                return Collections.emptyList();
            }
            return toResults(response.get());
        } catch (Throwable t) {
            LOG.debug("Peer query failed for " + peer.hostname() + ":" + peer.utpPort()
                    + " token=" + token, t);
            return Collections.emptyList();
        }
    }

    private RemoteSearchRequest buildSignedRequest(String keywords, int limit) throws GeneralSecurityException {
        byte[] nonce = new byte[32];
        SecureRandom.getInstanceStrong().nextBytes(nonce);
        long timestamp = System.currentTimeMillis() / 1000L;
        RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                .keywords(keywords)
                .limit(limit)
                .nonce(nonce)
                .ttl(0)
                .requesterPub(identity.ed25519PubRaw())
                .path(new byte[0][])
                .timestamp(timestamp)
                .signature(new byte[64])
                .build();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(unsigned.canonicalBytes());
        byte[] sig = signer.sign();
        return RemoteSearchRequest.builder()
                .keywords(keywords)
                .limit(limit)
                .nonce(nonce)
                .ttl(0)
                .requesterPub(identity.ed25519PubRaw())
                .path(new byte[0][])
                .timestamp(timestamp)
                .signature(sig)
                .build();
    }

    private static List<FileSearchResult> toResults(RemoteSearchResponse response) {
        List<FileSearchResult> out = new ArrayList<>(response.rows().size());
        for (RemoteSearchResponse.Row row : response.rows()) {
            out.add(toResult(row));
        }
        return out;
    }

    private static CompositeFileSearchResult toResult(RemoteSearchResponse.Row row) {
        String name = row.name;
        String infoHashHex = Hex.encode(row.infoHash);
        String magnet = UrlUtils.buildMagnetUrl(infoHashHex, name, DefaultTrackers.MAGNET_URL_PARAMETERS);
        return CompositeFileSearchResult.builder()
                .displayName(name)
                .filename(name + ".torrent")
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
            Optional<String> hashOpt = c.getTorrentHash();
            if (!hashOpt.isPresent()) {
                continue;
            }
            String hash = hashOpt.get();
            if (!seen.containsKey(hash)) {
                seen.put(hash, r);
            }
        }
        return new ArrayList<>(seen.values());
    }
}
