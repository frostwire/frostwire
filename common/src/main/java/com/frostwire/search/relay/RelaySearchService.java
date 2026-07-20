/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-process handler for incoming {@link RemoteSearchRequest}s.
 * Performs the four steps needed to turn a request into a signed
 * response:
 * <ol>
 *   <li>Verify the requester's Ed25519 signature over the
 *       request's canonical bytes.</li>
 *   <li>Check timestamp skew (anti-replay).</li>
 *   <li>Rate-limit per requester (token bucket).</li>
 *   <li>Query the local {@link LocalIndex} and build a
 *       {@link RemoteSearchResponse} signed by this node's key.</li>
 * </ol>
 *
 * <p>Returns {@link Optional#empty()} for any rejection. The
 * transport layer can map empty to a generic error reply without
 * leaking the rejection reason (which would help attackers tune
 * their bypasses).
 *
 * <p>Fail-closed: any error in the verification or query path
 * returns empty and is logged. A malicious or buggy peer can
 * never crash the responder.
 */
public final class RelaySearchService {

    private static final Logger LOG = Logger.getLogger(RelaySearchService.class);

    private static final int RESULT_LIMIT_CAP = RemoteSearchRequest.MAX_LIMIT;
    private static final long MAX_TIMESTAMP_SKEW_MS =
            RemoteSearchRequest.MAX_TIMESTAMP_SKEW_SEC * 1000L;

    private final LocalIndex index;
    private final IdentityKeys identity;
    private final RateLimiter rateLimiter;
    private final ShareVisibilityPolicy visibility;
    private volatile SeederEndpointProvider seederEndpointProvider = SeederEndpointProvider.NONE;

    public RelaySearchService(LocalIndex index, IdentityKeys identity) {
        this(index, identity, new RateLimiter(
                RelayConstants.DEFAULT_MAX_QPS,
                RelayConstants.DEFAULT_MAX_QPS), null);
    }

    public RelaySearchService(LocalIndex index, IdentityKeys identity,
                              RateLimiter rateLimiter) {
        this(index, identity, rateLimiter, null);
    }

    public RelaySearchService(LocalIndex index, IdentityKeys identity,
                              ShareVisibilityPolicy visibility) {
        this(index, identity, new RateLimiter(
                RelayConstants.DEFAULT_MAX_QPS,
                RelayConstants.DEFAULT_MAX_QPS), visibility);
    }

    public RelaySearchService(LocalIndex index, IdentityKeys identity,
                              RateLimiter rateLimiter,
                              ShareVisibilityPolicy visibility) {
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        if (identity == null) {
            throw new IllegalArgumentException("identity is null");
        }
        if (rateLimiter == null) {
            throw new IllegalArgumentException("rateLimiter is null");
        }
        this.index = index;
        this.identity = identity;
        this.rateLimiter = rateLimiter;
        this.visibility = visibility;
    }

    /**
     * Advertise this node's BT listen endpoints inside signed response rows
     * (v3 {@code bt} field) so requesters can fetch the torrent directly.
     * Default {@link SeederEndpointProvider#NONE} advertises nothing.
     */
    public void setSeederEndpointProvider(SeederEndpointProvider provider) {
        this.seederEndpointProvider = provider != null ? provider : SeederEndpointProvider.NONE;
    }

    /**
     * Handle an incoming request. Returns empty if the request
     * is rejected for any reason (bad signature, stale timestamp,
     * rate limit, internal error).
     */
    public Optional<RemoteSearchResponse> handle(RemoteSearchRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        try {
            if (!verifySignature(request)) {
                LOG.warn("RelaySearchService: rejected request (bad signature) keywords="
                        + request.keywords());
                return Optional.empty();
            }
            long nowMs = System.currentTimeMillis();
            long skew = Math.abs(nowMs - (request.timestamp() * 1000L));
            if (skew > MAX_TIMESTAMP_SKEW_MS) {
                LOG.warn("RelaySearchService: rejected request (timestamp skew "
                        + skew + "ms) keywords=" + request.keywords());
                return Optional.empty();
            }
            if (!rateLimiter.tryAcquire(request.requesterPub())) {
                LOG.warn("RelaySearchService: rejected request (rate limit) keywords="
                        + request.keywords());
                return Optional.empty();
            }
            int limit = Math.min(request.limit(), RESULT_LIMIT_CAP);
            int fetch = visibility == null || visibility == ShareVisibilityPolicy.INCLUDE_ALL
                    ? limit
                    : Math.min(limit * 4, Math.max(limit, 200));
            List<LocalSharedTorrent> rows = index.search(request.keywords(), fetch);
            if (rows == null) {
                rows = new ArrayList<>();
            }
            rows = ShareVisibility.filter(rows, visibility);
            if (rows.size() > limit) {
                rows = new ArrayList<>(rows.subList(0, limit));
            }
            LOG.info("RelaySearchService: answered keywords=\"" + request.keywords()
                    + "\" rows=" + rows.size());
            return Optional.of(buildResponse(request, rows));
        } catch (Throwable t) {
            LOG.warn("RelaySearchService.handle failed", t);
            return Optional.empty();
        }
    }

    private RemoteSearchResponse buildResponse(RemoteSearchRequest request,
                                               List<LocalSharedTorrent> rows) {
        RemoteSearchResponse.Builder b = RemoteSearchResponse.builder()
                .nonce(request.nonce())
                .timestamp(System.currentTimeMillis() / 1000L);
        List<String> endpoints = seederEndpointProvider.seederEndpoints();
        for (LocalSharedTorrent t : rows) {
            byte[] nodeId = t.publisherNodeId();
            b.addRow(t.infoHash(), t.name(), t.sizeBytes(), t.fileCount(),
                    t.publisherEd25519Pub(), nodeId, t.matchedFile(), endpoints);
        }
        RemoteSearchResponse unsigned = b.signature(new byte[64]).build();
        byte[] sig = sign(unsigned.canonicalBytes());
        return b.signature(sig).build();
    }

    private static boolean verifySignature(RemoteSearchRequest request) {
        try {
            byte[] raw = request.requesterPub();
            if (raw == null || raw.length != 32) {
                return false;
            }
            // X.509 prefix for Ed25519 (12 bytes)
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
            LOG.debug("Signature verification threw", e);
            return false;
        }
    }

    private byte[] sign(byte[] data) {
        try {
            PrivateKey priv = identity.ed25519().getPrivate();
            Signature signer = IdentityKeys.softwareSignature("Ed25519");
            signer.initSign(priv);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign relay response", e);
        }
    }

    public RateLimiter rateLimiter() {
        return rateLimiter;
    }
}
