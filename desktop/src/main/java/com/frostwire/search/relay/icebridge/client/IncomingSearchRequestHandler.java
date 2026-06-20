/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteCatalogBrowseRequest;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.search.relay.RemoteSearchRequest;
import com.frostwire.search.relay.RemoteSearchResponse;
import com.frostwire.search.relay.SearchPayloadCodec;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p><b>Multi-hop forwarding:</b> after the local response is sent, if the
 * request's {@code ttl > 0} and a {@link PeerDirectory} and {@link IdentityKeys}
 * are configured, the handler forwards the request to up to
 * {@link #MAX_FORWARD_TARGETS} trusted verified peers not already in the
 * request's path. Each forwarded request is re-signed with this node's
 * Ed25519 key. Forwarding is best-effort: failures (no peers, transport
 * errors) do not affect the local response.
 *
 * <p>Rate-limits per-source to prevent flood/amplification attacks. Each
 * source public key is limited to {@link #MAX_REQUESTS_PER_MINUTE} search
 * requests per minute.
 */
public final class IncomingSearchRequestHandler implements DistributedSearchTransport.PayloadListener {

    private static final Logger LOG = Logger.getLogger(IncomingSearchRequestHandler.class);

    /** Maximum incoming search requests per source per minute. */
    private static final int MAX_REQUESTS_PER_MINUTE = 30;

    /** Maximum peers a single request is forwarded to (anti-amplification). */
    private static final int MAX_FORWARD_TARGETS = 3;

    private final DistributedSearchTransport transport;
    private final RelaySearchService searchService;
    private final PeerDirectory peerDirectory;
    private final IdentityKeys identity;
    private final LocalIndex localIndex;
    private final ConcurrentHashMap<String, RateBucket> rateMap = new ConcurrentHashMap<>();

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

    public void stop() {
        transport.removeListener(this);
        LOG.info("IncomingSearchRequestHandler stopped");
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs) {
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

    private void handleSearchRequest(RemoteSearchRequest request, byte[] sourcePub) {
        String sourceKey = Hex.encode(sourcePub);
        if (!tryAcquire(sourceKey)) {
            LOG.debug("IncomingSearchRequestHandler: rate-limited request from " + sourceKey);
            return;
        }

        try {
            Optional<RemoteSearchResponse> response = searchService.handle(request);
            if (response.isEmpty()) {
                return;
            }
            byte[] responseBytes = SearchPayloadCodec.encodeResponse(response.get());
            transport.send(request.requesterPub(), responseBytes);
        } catch (Throwable t) {
            LOG.debug("IncomingSearchRequestHandler failed to process request", t);
        }

        if (request.ttl() > 0 && peerDirectory != null && identity != null) {
            try {
                forwardRequest(request, sourcePub);
            } catch (Throwable t) {
                LOG.debug("IncomingSearchRequestHandler forwarding failed", t);
            }
        }
    }

    private void handleCatalogBrowseRequest(RemoteCatalogBrowseRequest request,
                                            byte[] sourcePub) {
        if (localIndex == null || identity == null) {
            return;
        }

        String sourceKey = Hex.encode(sourcePub);
        if (!tryAcquire(sourceKey)) {
            LOG.debug("IncomingSearchRequestHandler: rate-limited catalog browse from " + sourceKey);
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
            Signature signer = Signature.getInstance("Ed25519");
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
            PublicKey pub = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(pub);
            verifier.update(request.canonicalBytes());
            return verifier.verify(request.signature());
        } catch (GeneralSecurityException e) {
            LOG.debug("Catalog browse signature verification threw", e);
            return false;
        }
    }

    private void forwardRequest(RemoteSearchRequest request, byte[] sourcePub) {
        byte[] ownPub = identity.ed25519PubRaw();
        int newTtl = request.ttl() - 1;
        List<PeerDirectory.PeerInfo> candidates =
                peerDirectory.topByTrustVerified(MAX_FORWARD_TARGETS * 3);
        int forwarded = 0;
        for (PeerDirectory.PeerInfo peer : candidates) {
            if (forwarded >= MAX_FORWARD_TARGETS) {
                break;
            }
            byte[] peerPub = peer.peerPub();
            if (request.isLoop(peerPub)) {
                continue;
            }
            if (Arrays.equals(peerPub, sourcePub)) {
                continue;
            }
            if (Arrays.equals(peerPub, request.requesterPub())) {
                continue;
            }
            try {
                RemoteSearchRequest nextHop = request.withNextHop(ownPub, newTtl);
                RemoteSearchRequest signed = signForwardedRequest(nextHop);
                byte[] forwardedPayload = SearchPayloadCodec.encodeRequest(signed);
                transport.send(peerPub, forwardedPayload);
                forwarded++;
            } catch (Throwable t) {
                LOG.debug("Failed to forward search request to peer", t);
            }
        }
    }

    private RemoteSearchRequest signForwardedRequest(RemoteSearchRequest unsigned)
            throws GeneralSecurityException {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(unsigned.canonicalBytes());
        byte[] sig = signer.sign();
        return RemoteSearchRequest.builder()
                .keywords(unsigned.keywords())
                .limit(unsigned.limit())
                .nonce(unsigned.nonce())
                .ttl(unsigned.ttl())
                .requesterPub(unsigned.requesterPub())
                .path(unsigned.path())
                .timestamp(unsigned.timestamp())
                .signature(sig)
                .build();
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
