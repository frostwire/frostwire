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
 * <p>When constructed with an {@link IdentityKeys}, the publisher
 * fields carry the node's real Ed25519 public key and derived node
 * ID. When constructed without keys (e.g. unit tests), placeholder
 * zero bytes are used.
 */
public final class SharedTorrentIndexer implements BTEngineListener {

    static final String UNKNOWN_NAME = "unknown";
    private static final String THREAD_NAME_PREFIX = "SharedTorrentIndexer-";

    private static final Logger LOG = Logger.getLogger(SharedTorrentIndexer.class);

    private final LocalIndex index;
    private final byte[] publisherNodeId;
    private final byte[] publisherEd25519Pub;
    private final AtomicReference<TorrentInfoSource> torrentInfoSource = new AtomicReference<>(
            new DefaultTorrentInfoSource());

    /** Construct with placeholder identity (for tests). */
    public SharedTorrentIndexer(LocalIndex index) {
        this(index, null);
    }

    /** Construct with a real node identity. */
    public SharedTorrentIndexer(LocalIndex index, IdentityKeys identity) {
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        this.index = index;
        if (identity != null) {
            this.publisherNodeId = identity.nodeId();
            this.publisherEd25519Pub = identity.ed25519PubRaw();
        } else {
            this.publisherNodeId = new byte[IdentityRecord.NODE_ID_LENGTH];
            this.publisherEd25519Pub = new byte[IdentityRecord.ED25519_PUB_LENGTH];
        }
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
        scheduleIndex(dl, IndexTrigger.ADDED);
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
        scheduleIndex(dl, IndexTrigger.UPDATE);
    }

    private void scheduleIndex(BTDownload dl, IndexTrigger trigger) {
        if (dl == null) {
            return;
        }
        String infoHashHex = safeInfoHash(dl);
        if (infoHashHex == null) {
            return;
        }
        ThreadExecutor.startThread(
                () -> indexIfReady(dl, infoHashHex, trigger),
                THREAD_NAME_PREFIX + infoHashHex);
    }

    IndexResult indexIfReady(BTDownload dl, String infoHashHex, IndexTrigger trigger) {
        if (dl == null || infoHashHex == null) {
            return IndexResult.NULL_INPUT;
        }
        try {
            TorrentInfo ti = torrentInfoSource.get().torrentInfo(dl);
            if (ti == null) {
                return IndexResult.NO_METADATA;
            }
            LocalSharedTorrent torrent = buildTorrent(dl, ti, infoHashHex);
            index.upsert(torrent);
            LOG.info("Indexed torrent " + infoHashHex + " from " + trigger.name());
            return IndexResult.UPSERTED;
        } catch (Throwable t) {
            LOG.warn("Failed to index torrent " + infoHashHex + " from " + trigger.name(), t);
            return IndexResult.ERROR;
        }
    }

    LocalSharedTorrent buildTorrent(BTDownload dl, TorrentInfo ti, String infoHashHex) {
        long now = Instant.now().getEpochSecond();
        long size = safeSize(ti);
        int fileCount = safeFileCount(ti);
        String name = safeName(dl, ti);
        String filesJson = buildFilesJson(ti, fileCount, size);

        return new LocalSharedTorrent.Builder()
                .infoHash(Hex.decode(infoHashHex))
                .name(name)
                .sizeBytes(size)
                .fileCount(fileCount)
                .filesJson(filesJson)
                .publisherNodeId(publisherNodeId)
                .publisherEd25519Pub(publisherEd25519Pub)
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    /**
     * Build a JSON array of real file paths extracted from the torrent's
     * {@link TorrentInfo}. Each entry is {@code {"path":"...","size":N}}.
     *
     * <p>Skips pad files and hidden files (same filters as
     * {@link com.frostwire.search.PerformersHelper#crawlTorrentInfo}).
     * Falls back to {@link FilesJson#minimal} if extraction fails.
     */
    private static String buildFilesJson(TorrentInfo ti, int fileCount, long totalSize) {
        try {
            var fs = ti.files();
            int n = fs.numFiles();
            if (n <= 0) {
                return FilesJson.minimal(fileCount, totalSize);
            }
            StringBuilder sb = new StringBuilder(256);
            sb.append('[');
            boolean first = true;
            for (int i = 0; i < n; i++) {
                // Skip pad files and hidden files.
                if (fs.padFileAt(i)) {
                    continue;
                }
                try {
                    var flags = fs.fileFlags(i);
                    if (flags != null && flags.and_(com.frostwire.jlibtorrent.FileStorage.FLAG_HIDDEN).nonZero()) {
                        continue;
                    }
                } catch (Throwable ignored) {
                    // fileFlags may not be available on all platforms; proceed.
                }
                String path;
                try {
                    path = fs.filePath(i);
                } catch (Throwable t) {
                    path = null;
                }
                if (path == null || path.isEmpty()) {
                    continue;
                }
                long fileSize;
                try {
                    fileSize = fs.fileSize(i);
                } catch (Throwable t) {
                    fileSize = 0;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{\"path\":");
                // Escape JSON string content.
                appendJsonString(sb, path);
                sb.append(",\"size\":").append(fileSize).append('}');
            }
            sb.append(']');
            if (first) {
                // No real files extracted — fall back to minimal.
                return FilesJson.minimal(fileCount, totalSize);
            }
            return sb.toString();
        } catch (Throwable t) {
            LOG.debug("buildFilesJson: extraction failed, using minimal", t);
            return FilesJson.minimal(fileCount, totalSize);
        }
    }

    /**
     * Append a JSON-escaped string (with surrounding double quotes) to
     * the builder. Escapes backslash, double-quote, newline, carriage
     * return, and tab.
     */
    private static void appendJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
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
            } catch (Throwable t) {
                LOG.debug("TorrentInfo.infoHashV1() lookup failed during name fallback", t);
            }
        }
        return UNKNOWN_NAME;
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
            LOG.debug("TorrentInfo.totalSize() lookup failed", t);
            return 0;
        }
    }

    private static int safeFileCount(TorrentInfo ti) {
        try {
            int n = ti.files().numFiles();
            return n <= 0 ? 1 : n;
        } catch (Throwable t) {
            LOG.debug("TorrentInfo.files().numFiles() lookup failed", t);
            return 1;
        }
    }

    private static String safeName(BTDownload dl, TorrentInfo ti) {
        String downloadName = null;
        try {
            downloadName = dl.getName();
        } catch (Throwable t) {
            LOG.debug("BTDownload.getName() lookup failed", t);
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
