/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

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

    private final RelaySearchService service;
    private final PeerDirectory directory;
    private final int defaultTrustFloor;

    public RelayRole(RelaySearchService service, PeerDirectory directory) {
        this(service, directory, 0);
    }

    public RelayRole(RelaySearchService service, PeerDirectory directory,
                     int defaultTrustFloor) {
        if (service == null) {
            throw new IllegalArgumentException("service is null");
        }
        if (directory == null) {
            throw new IllegalArgumentException("directory is null");
        }
        this.service = service;
        this.directory = directory;
        this.defaultTrustFloor = defaultTrustFloor;
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
     * Forward a request to a trusted peer in the directory. The
     * caller is responsible for actually dialing the peer and
     * sending the bytes; this method just transforms the request
     * (appends the next hop to the path, decrements ttl, returns
     * the un-signed request for the caller to sign with the
     * next-hop peer's key if needed).
     *
     * <p>Currently this only mutates the request; future work
     * could include signature re-signing if the transport
     * requires it.
     */
    public RemoteSearchRequest forward(RemoteSearchRequest request, byte[] nextHopPub) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        if (nextHopPub == null || nextHopPub.length != 32) {
            throw new IllegalArgumentException("nextHopPub must be 32 bytes");
        }
        if (request.isLoop(nextHopPub)) {
            throw new IllegalStateException("next hop already in path (loop)");
        }
        if (request.ttl() <= 0) {
            throw new IllegalStateException("ttl exhausted, cannot forward");
        }
        return request.withNextHop(nextHopPub, request.ttl() - 1);
    }

    public RelaySearchService service() {
        return service;
    }

    public PeerDirectory directory() {
        return directory;
    }
}
