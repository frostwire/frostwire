/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Composable "relay" role for a node. Wires the {@link RelaySearchService}
 * with a {@link PeerDirectory} so that incoming requests can be
 * evaluated against the local trust graph.
 *
 * <p>This role is the in-process glue: it accepts a
 * {@link RemoteSearchRequest} and either:
 * <ul>
 *   <li>rejects it because the requester is in the directory and
 *       is marked as spam (negative trust), OR</li>
 *   <li>delegates to the underlying {@link RelaySearchService} for
 *       signature verification, rate-limiting, local query, and
 *       response signing.</li>
 * </ul>
 *
 * <p>The actual transport (uTP dial/accept, byte encoding) is a
 * separate layer above this role. The role itself is pure logic
 * — it doesn't touch the network — so it's easy to test and
 * cheap to compose with other roles (indexer, searcher, etc.).
 *
 * <p>Fail-closed: any error returns empty and is logged.
 */
public final class RelayRole {

    private static final Logger LOG = Logger.getLogger(RelayRole.class);

    /** Maximum number of peers a single request is forwarded to. */
    public static final int MAX_FORWARD_TARGETS = 3;

    private final RelaySearchService service;
    private final PeerDirectory directory;
    private final int defaultTrustFloor;
    private final IdentityKeys identity;

    public RelayRole(RelaySearchService service, PeerDirectory directory) {
        this(service, directory, 0, null);
    }

    public RelayRole(RelaySearchService service, PeerDirectory directory,
                     int defaultTrustFloor) {
        this(service, directory, defaultTrustFloor, null);
    }

    public RelayRole(RelaySearchService service, PeerDirectory directory,
                     IdentityKeys identity) {
        this(service, directory, 0, identity);
    }

    public RelayRole(RelaySearchService service, PeerDirectory directory,
                     int defaultTrustFloor, IdentityKeys identity) {
        if (service == null) {
            throw new IllegalArgumentException("service is null");
        }
        if (directory == null) {
            throw new IllegalArgumentException("directory is null");
        }
        this.service = service;
        this.directory = directory;
        this.defaultTrustFloor = defaultTrustFloor;
        this.identity = identity;
    }

    /**
     * Handle a {@link RemoteSearchRequest} from a peer. Returns
     * empty for any rejection (spam, bad signature, rate limit,
     * internal error).
     */
    public Optional<RemoteSearchResponse> handleRequest(RemoteSearchRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        try {
            // Spam check: a peer marked in the directory as spam
            // is rejected before we even rate-limit them.
            double trust = directory.trustScore(request.requesterPub());
            if (trust < defaultTrustFloor) {
                LOG.debug("Rejected: trust score " + trust + " below floor " + defaultTrustFloor);
                return Optional.empty();
            }
            return service.handle(request);
        } catch (Throwable t) {
            LOG.warn("RelayRole.handleRequest failed", t);
            return Optional.empty();
        }
    }

    /**
     * Build signed forwarded requests for up to {@link #MAX_FORWARD_TARGETS}
     * trusted verified peers not already in the request's path.
     *
     * <p>Each forwarded request has this node's Ed25519 pubkey appended to the
     * path (via {@link RemoteSearchRequest#withNextHop}), the ttl decremented
     * by 1, and a fresh signature over the canonical bytes. The returned
     * {@link ForwardTarget} pairs each signed request with the target peer's
     * public key so the caller can send it via a transport.
     *
     * <p>Returns an empty list when:
     * <ul>
     *   <li>{@code request.ttl()} is 0 or negative (no more hops allowed),</li>
     *   <li>no identity is configured (forwarding requires re-signing),</li>
     *   <li>no eligible peers are available (all are in the path or the
     *       directory is empty).</li>
     * </ul>
     *
     * @throws IllegalArgumentException if {@code request} is null
     * @throws IllegalStateException if no identity is configured
     */
    public List<ForwardTarget> forward(RemoteSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        if (identity == null) {
            throw new IllegalStateException(
                    "identity not configured for forwarding");
        }
        if (request.ttl() <= 0) {
            return Collections.emptyList();
        }
        try {
            return selectAndSignForwardTargets(request);
        } catch (Throwable t) {
            LOG.warn("RelayRole.forward failed", t);
            return Collections.emptyList();
        }
    }

    private List<ForwardTarget> selectAndSignForwardTargets(RemoteSearchRequest request)
            throws GeneralSecurityException {
        byte[] ownPub = identity.ed25519PubRaw();
        int newTtl = request.ttl() - 1;
        List<PeerDirectory.PeerInfo> candidates =
                directory.topByTrustVerified(MAX_FORWARD_TARGETS * 3);
        List<ForwardTarget> out = new ArrayList<>(MAX_FORWARD_TARGETS);
        for (PeerDirectory.PeerInfo peer : candidates) {
            if (out.size() >= MAX_FORWARD_TARGETS) {
                break;
            }
            byte[] peerPub = peer.peerPub();
            if (request.isLoop(peerPub)) {
                continue;
            }
            RemoteSearchRequest nextHop = request.withNextHop(ownPub, newTtl);
            RemoteSearchRequest signed = signForwardedRequest(nextHop);
            out.add(new ForwardTarget(peerPub, signed));
        }
        return out;
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

    /**
     * A signed forwarded request paired with the target peer's public key.
     */
    public static final class ForwardTarget {
        private final byte[] peerPub;
        private final RemoteSearchRequest request;

        public ForwardTarget(byte[] peerPub, RemoteSearchRequest request) {
            if (peerPub == null || peerPub.length != 32) {
                throw new IllegalArgumentException("peerPub must be 32 bytes");
            }
            if (request == null) {
                throw new IllegalArgumentException("request is null");
            }
            this.peerPub = peerPub.clone();
            this.request = request;
        }

        public byte[] peerPub() {
            return peerPub.clone();
        }

        public RemoteSearchRequest request() {
            return request;
        }
    }

    public RelaySearchService service() {
        return service;
    }

    public PeerDirectory directory() {
        return directory;
    }
}
