/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

/**
 * Callback invoked by {@link RudpSessionManager} when an application payload
 * has been received from a remote peer.
 */
public interface RudpMessageListener {

    /**
     * Called with the verified source peer public key and the payload bytes.
     *
     * @param sourcePub raw 32-byte Ed25519 public key of the sender
     * @param payload   opaque application payload
     */
    void onMessage(byte[] sourcePub, byte[] payload);
}