/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.Optional;

/**
 * Authenticates a discovered peer endpoint and returns its verified
 * {@link IdentityRecord}. Implementations are responsible for any
 * network handshake and signature verification.
 *
 * <p>Return {@link Optional#empty()} if the endpoint cannot be
 * authenticated. The caller (typically {@link PeerDiscovery}) must
 * not treat such an endpoint as a trusted queryable peer.
 */
public interface PeerAuthenticator {

    /**
     * Authenticate {@code host:port} and return the verified
     * identity record.
     */
    Optional<IdentityRecord> authenticate(String host, int port);
}
