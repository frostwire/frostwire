/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks whether this node has received unsolicited inbound connections,
 * which is the simplest empirical NAT traversal indicator.
 *
 * <p>A node is "connectable" if it has received at least one incoming
 * rUDP packet from a peer that it did not recently initiate a connection
 * to. This is set by the rUDP session manager when it receives a HELLO
 * from an address that has no existing outbound session.
 *
 * <p>The flag is sticky — once connectable, always connectable for the
 * session. NAT state rarely changes mid-session without the user
 * switching networks, in which case the app restarts anyway.
 */
public final class ConnectivityDetector {

    private static final ConnectivityDetector INSTANCE = new ConnectivityDetector();

    private final AtomicBoolean connectable = new AtomicBoolean(false);

    public static ConnectivityDetector instance() {
        return INSTANCE;
    }

    private ConnectivityDetector() {
    }

    public boolean isConnectable() {
        return connectable.get();
    }

    public void markConnectable() {
        connectable.set(true);
    }

    public void reset() {
        connectable.set(false);
    }
}
