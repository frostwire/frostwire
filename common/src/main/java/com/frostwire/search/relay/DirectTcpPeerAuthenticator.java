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
 * Authenticates a peer by connecting over TCP and fetching its
 * {@link IdentityRecord} via the relay identity handshake. The
 * record's self-signature is verified before the record is
 * considered valid.
 *
 * <p>This is the production authenticator for the direct TCP
 * peer-search protocol: a discovered endpoint is not registered
 * in the {@link PeerDirectory} until its identity record has been
 * fetched and verified.
 */
public final class DirectTcpPeerAuthenticator implements PeerAuthenticator {

    private static final Logger LOG = Logger.getLogger(DirectTcpPeerAuthenticator.class);

    private final OutgoingRelayClient client;

    public DirectTcpPeerAuthenticator() {
        this(new OutgoingRelayClient());
    }

    public DirectTcpPeerAuthenticator(OutgoingRelayClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client is null");
        }
        this.client = client;
    }

    @Override
    public Optional<IdentityRecord> authenticate(String host, int port) {
        try {
            Optional<IdentityRecord> maybeRecord = client.fetchIdentity(host, port);
            if (maybeRecord.isEmpty()) {
                LOG.debug("No identity record from " + host + ":" + port);
                return Optional.empty();
            }
            IdentityRecord record = maybeRecord.get();
            if (!record.verifySignature()) {
                LOG.debug("Identity record signature invalid from " + host + ":" + port);
                return Optional.empty();
            }
            return Optional.of(record);
        } catch (Throwable t) {
            if (t instanceof java.net.ConnectException || t.getCause() instanceof java.net.ConnectException) {
                LOG.debug("DirectTcp auth connection refused for " + host + ":" + port);
            } else {
                LOG.debug("Authentication failed for " + host + ":" + port, t);
            }
            return Optional.empty();
        }
    }
}
