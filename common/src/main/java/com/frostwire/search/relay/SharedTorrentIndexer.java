/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.concurrent.concurrent.ThreadExecutor;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Auto-magic populator for the local distributed-search index.
 *
 * <p>Registered as a {@link BTEngineListener}. On each
 * {@code downloadAdded} or {@code downloadUpdate} callback (the
 * magnet-to-metadata transition), a background task inspects the
 * torrent's {@link TorrentInfo} and, once it is available, upserts a
 * {@link LocalSharedTorrent} row into the configured
 * {@link LocalIndex}.
 *
 * <p>The publisher identity fields are filled with placeholder zero
 * bytes for now; the dedicated {@code IdentityKeys} generator will
 * replace them once it lands.
 */
public final class SharedTorrentIndexer implements BTEngineListener {

    private static final Logger LOG = Logger.getLogger(SharedTorrentIndexer.class);

    private final LocalIndex index;
    private final ConcurrentMap<String, Boolean> indexingByInfoHash = new ConcurrentHashMap<>();
    private final AtomicReference<TorrentInfoSource> torrentInfoSource = new AtomicReference<>(
            new DefaultTorrentInfoSource());

    public SharedTorrentIndexer(LocalIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        this.index = index;
    }

    void setTorrentInfoSource(TorrentInfoSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        torrentInfoSource.set(source);
    }

    @Override
    public void started(BTEngine engine) {
    }

    @Override
    public void stopped(BTEngine engine) {
    }

    @Override
    public void downloadAdded(BTEngine engine, BTDownload dl) {
        scheduleIndex(engine, dl, "downloadAdded");
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
        scheduleIndex(engine, dl, "downloadUpdate");
    }

    private void scheduleIndex(BTEngine engine, BTDownload dl, String trigger) {
        if (dl == null) {
            return;
        }
        String infoHashHex = safeInfoHash(dl);
        if (infoHashHex == null) {
            return;
        }
        if (indexingByInfoHash.putIfAbsent(infoHashHex, Boolean.TRUE) != null) {
            return;
        }
        ThreadExecutor.startThread(
                () -> indexIfReady(engine, dl, infoHashHex, trigger),
                "SharedTorrentIndexer-" + infoHashHex);
    }

    void indexIfReady(BTEngine engine, BTDownload dl, String infoHashHex, String trigger) {
        try {
            TorrentInfo ti = torrentInfoSource.get().torrentInfo(dl);
            if (ti == null) {
                return;
            }
            LocalSharedTorrent torrent = buildTorrent(dl, ti, infoHashHex);
            index.upsert(torrent);
            LOG.info("Indexed torrent " + infoHashHex + " from " + trigger);
        } catch (Throwable t) {
            LOG.warn("Failed to index torrent " + infoHashHex + " from " + trigger, t);
        } finally {
            indexingByInfoHash.remove(infoHashHex);
        }
    }

    static LocalSharedTorrent buildTorrent(BTDownload dl, TorrentInfo ti, String infoHashHex) {
        long now = Instant.now().getEpochSecond();
        long size = safeSize(ti);
        int fileCount = safeFileCount(ti);
        String name = safeName(dl, ti);
        String filesJson = FilesJson.minimal(fileCount, size);
        byte[] placeholderPub = new byte[IdentityRecord.ED25519_PUB_LENGTH];
        byte[] placeholderNodeId = new byte[IdentityRecord.NODE_ID_LENGTH];

        return new LocalSharedTorrent.Builder()
                .infoHash(Hex.decode(infoHashHex))
                .name(name)
                .sizeBytes(size)
                .fileCount(fileCount)
                .filesJson(filesJson)
                .publisherNodeId(placeholderNodeId)
                .publisherEd25519Pub(placeholderPub)
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    static String resolveName(String downloadName, TorrentInfo ti) {
        if (downloadName != null && !downloadName.isEmpty()) {
            return downloadName;
        }
        if (ti != null) {
            try {
                Sha1Hash v1 = ti.infoHashV1();
                if (v1 != null) {
                    return v1.toHex();
                }
            } catch (Throwable ignored) {
            }
        }
        return "unknown";
    }

    private static String safeInfoHash(BTDownload dl) {
        try {
            String hex = dl.getInfoHash();
            if (hex == null || hex.isEmpty()) {
                return null;
            }
            return hex.toLowerCase();
        } catch (Throwable t) {
            LOG.warn("Unable to read info hash from BTDownload", t);
            return null;
        }
    }

    private static long safeSize(TorrentInfo ti) {
        try {
            long size = ti.totalSize();
            return size < 0 ? 0 : size;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int safeFileCount(TorrentInfo ti) {
        try {
            int n = ti.files().numFiles();
            return n <= 0 ? 1 : n;
        } catch (Throwable t) {
            return 1;
        }
    }

    private static String safeName(BTDownload dl, TorrentInfo ti) {
        String downloadName = null;
        try {
            downloadName = dl.getName();
        } catch (Throwable ignored) {
        }
        return resolveName(downloadName, ti);
    }

    /**
     * Tiny seam so unit tests can drive the indexer without a real
     * libtorrent {@code BTDownload} and {@code TorrentInfo}. The default
     * implementation reads from the live handle.
     */
    interface TorrentInfoSource {
        TorrentInfo torrentInfo(BTDownload dl);
    }

    private static final class DefaultTorrentInfoSource implements TorrentInfoSource {
        @Override
        public TorrentInfo torrentInfo(BTDownload dl) {
            return dl.getTorrentHandle().torrentFile();
        }
    }
}
