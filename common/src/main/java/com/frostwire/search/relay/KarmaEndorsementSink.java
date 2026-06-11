/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Callback for the karma endorsement trigger. Fired when a download
 * completes and the torrent was published by a peer (non-zero
 * publisher Ed25519 key that differs from our own).
 *
 * <p>Implementations should be fast and non-blocking; the trigger
 * runs on the BTEngine listener thread.
 */
public interface KarmaEndorsementSink {
    void onDownloadCompletedFromPeer(byte[] peerEd25519Pub, byte[] infoHash);
}
