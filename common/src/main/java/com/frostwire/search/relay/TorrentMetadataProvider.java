/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Source of full .torrent bytes for incoming {@link TorrentMetadataRequest}s
 * (Protocol #3 METADATA) — the mesh equivalent of BEP 9 metadata exchange,
 * NAT-proof via IceBridge relaying.
 *
 * <p>Implementations MUST return the complete .torrent serialization
 * (including BEP 52 piece layers for hybrid torrents) or {@code null} when
 * this node does not publicly share the torrent. Implementations run on a
 * bounded request worker, never the response poller.
 */
public interface TorrentMetadataProvider {

    /**
     * Current authorization, checked before cache access and while sending.
     * Must deny private, removed, unshared and temporary metadata-only torrents.
     * Merely implementing the byte source does not opt into public sharing.
     */
    default boolean isPubliclyShared(byte[] infoHashV1) {
        return false;
    }

    /**
     * @param infoHashV1 20-byte v1 info hash of the wanted torrent
     * @return full .torrent bytes, or null when not held locally
     */
    byte[] torrentBytes(byte[] infoHashV1);
}
