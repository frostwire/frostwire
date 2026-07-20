/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.util.Logger;


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

    /**
     * Maximum number of peers a single request is forwarded to (M).
     * Prefer live {@link IceBridgeTopology#searchPeerFanout()}; constant kept
     * for tests that reference the compiled default.
     */
    public static final int MAX_FORWARD_TARGETS =
            IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT;

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
     * Build dual-envelope hop requests for up to {@link #MAX_FORWARD_TARGETS}
     * trusted verified peers not already in the request's path.
     *
     * <p>Each hop uses {@link RemoteSearchRequest#withNextHop} (preserves the
     * original requester query signature; only ttl/path change).
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
            return selectForwardTargets(request);
        } catch (Throwable t) {
            LOG.warn("RelayRole.forward failed", t);
            return Collections.emptyList();
        }
    }

    private List<ForwardTarget> selectForwardTargets(RemoteSearchRequest request) {
        byte[] ownPub = identity.ed25519PubRaw();
        int hopsSoFar = request.path() != null ? request.path().length : 0;
        // forward() guarantees ttl > 0. Clamping may reduce the remaining
        // ttl to 0; this hop still forwards, and the next hop's ttl guard
        // stops further forwarding (soft-max horizon, LimeWire semantics).
        int newTtl = IceBridgeTopology.get().clampRemainingTtl(hopsSoFar, request.ttl() - 1);
        int m = IceBridgeTopology.get().searchPeerFanout();
        List<PeerDirectory.PeerInfo> candidates =
                directory.topByTrustVerified(m * 3);
        List<ForwardTarget> out = new ArrayList<>(m);
        for (PeerDirectory.PeerInfo peer : candidates) {
            if (out.size() >= m) {
                break;
            }
            byte[] peerPub = peer.peerPub();
            if (request.isLoop(peerPub)) {
                continue;
            }
            RemoteSearchRequest nextHop = request.withNextHop(ownPub, newTtl);
            out.add(new ForwardTarget(peerPub, nextHop));
        }
        return out;
    }

    /**
     * A dual-envelope hop request paired with the target peer's public key.
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
