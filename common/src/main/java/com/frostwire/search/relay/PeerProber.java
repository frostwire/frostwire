/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.Optional;

/**
 * Finds out who answers at a discovered {@code ip:udpPort}. The production implementation is
 * {@link RudpPeerProber}: a UDP handshake that pins no expected identity and returns the key the
 * endpoint proves it holds. Nothing here needs an inbound TCP port, so a peer that can be
 * hole-punched can be discovered.
 *
 * <p>Return {@link Optional#empty()} when the endpoint does not answer or does not authenticate.
 * The caller ({@link PeerDiscovery}) must not treat such an endpoint as a queryable peer.
 */
@FunctionalInterface
public interface PeerProber {

    /**
     * @param deadlineNanos {@link System#nanoTime()} value the probe must not outlive
     */
    Optional<ProbedPeer> probe(String host, int port, long deadlineNanos);
}
