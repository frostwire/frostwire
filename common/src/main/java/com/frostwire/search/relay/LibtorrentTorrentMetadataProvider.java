/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.util.Logger;

/**
 * {@link TorrentMetadataProvider} backed by the live libtorrent session:
 * answers with the full .torrent serialization (including BEP 52 piece
 * layers) of any torrent this engine holds — desktop answerer for mesh
 * TORRENT_FETCH requests.
 */
public final class LibtorrentTorrentMetadataProvider implements TorrentMetadataProvider {

    private static final Logger LOG = Logger.getLogger(LibtorrentTorrentMetadataProvider.class);

    @Override
    public byte[] torrentBytes(byte[] infoHashV1) {
        if (infoHashV1 == null || infoHashV1.length != 20) {
            return null;
        }
        try {
            BTEngine engine = BTEngine.getInstance();
            if (engine == null) {
                return null;
            }
            TorrentHandle handle = engine.find(new Sha1Hash(infoHashV1));
            if (handle == null || !handle.isValid()) {
                return null;
            }
            TorrentInfo info = handle.torrentFile();
            if (info == null) {
                return null; // metadata not available (yet)
            }
            return info.bencode();
        } catch (Throwable t) {
            LOG.warn("LibtorrentTorrentMetadataProvider failed for "
                    + com.frostwire.util.Hex.encode(infoHashV1), t);
            return null;
        }
    }
}