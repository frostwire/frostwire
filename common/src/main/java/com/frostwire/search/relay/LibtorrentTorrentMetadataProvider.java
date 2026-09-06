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
    private final ShareVisibilityPolicy visibility;

    public LibtorrentTorrentMetadataProvider() {
        this(null);
    }

    public LibtorrentTorrentMetadataProvider(ShareVisibilityPolicy visibility) {
        this.visibility = visibility;
    }

    @Override
    public boolean isPubliclyShared(byte[] infoHashV1) {
        if (infoHashV1 == null || infoHashV1.length != 20
                || !ShareVisibility.isPubliclyShared(com.frostwire.util.Hex.encode(infoHashV1), visibility)) {
            return false;
        }
        try {
            BTEngine engine = BTEngine.getInstance();
            TorrentInfo info = engine == null ? null : torrentInfoOf(engine.find(new Sha1Hash(infoHashV1)));
            return info != null && !info.isPrivate();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public byte[] torrentBytes(byte[] infoHashV1) {
        if (!isPubliclyShared(infoHashV1)) {
            return null;
        }
        try {
            BTEngine engine = BTEngine.getInstance();
            if (engine == null) {
                return null;
            }
            TorrentHandle handle = engine.find(new Sha1Hash(infoHashV1));
            TorrentInfo info = torrentInfoOf(handle);
            if (info == null || info.isPrivate()) {
                return null;
            }
            byte[] bytes = info.bencode();
            return isPubliclyShared(infoHashV1) ? bytes : null;
        } catch (Throwable t) {
            LOG.warn("LibtorrentTorrentMetadataProvider failed for "
                    + com.frostwire.util.Hex.encode(infoHashV1), t);
            return null;
        }
    }

    private static TorrentInfo torrentInfoOf(TorrentHandle handle) {
        if (handle == null || !handle.isValid()) {
            return null;
        }
        return handle.torrentFile();
    }
}
