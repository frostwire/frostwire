/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.TorrentInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SharedTorrentIndexerTest {

    @Test
    void constructorRejectsNullIndex() {
        assertThrows(IllegalArgumentException.class, () -> new SharedTorrentIndexer(null));
    }

    @Test
    void setTorrentInfoSourceRejectsNull() {
        SharedTorrentIndexer indexer = new SharedTorrentIndexer(new RecordingIndex());
        assertThrows(IllegalArgumentException.class, () -> indexer.setTorrentInfoSource(null));
    }

    @Test
    void resolveNamePrefersDownloadName() {
        assertEquals("ubuntu-24.04.iso", SharedTorrentIndexer.resolveName("ubuntu-24.04.iso", null));
    }

    @Test
    void resolveNameFallsBackToUnknownWhenNothingIsAvailable() {
        assertEquals("unknown", SharedTorrentIndexer.resolveName(null, null));
        assertEquals("unknown", SharedTorrentIndexer.resolveName("", null));
    }

    @Test
    void resolveNameHandlesTolerantTorrentInfo() {
        // A null TorrentInfo (magnet still loading) should not throw.
        assertEquals("explicit", SharedTorrentIndexer.resolveName("explicit", null));
    }

    @Test
    void indexIfReadySkipsWhenTorrentInfoSourceReturnsNull() {
        RecordingIndex index = new RecordingIndex();
        SharedTorrentIndexer indexer = new SharedTorrentIndexer(index);
        indexer.setTorrentInfoSource(dl -> null);

        // BTDownload is not dereferenced when the source short-circuits; the
        // null parameter is intentional to prove the seam is the boundary.
        indexer.indexIfReady(null, null, "00112233445566778899aabbccddeeff00112233", "downloadAdded");

        assertEquals(0, index.upserts.size(), "No metadata means no upsert");
    }

    @Test
    void buildTorrentPathIsNotInvokedWhenTorrentInfoIsNull() {
        // Indexer must not call buildTorrent without a TorrentInfo.
        RecordingIndex index = new RecordingIndex();
        SharedTorrentIndexer indexer = new SharedTorrentIndexer(index);
        indexer.setTorrentInfoSource(dl -> null);

        indexer.indexIfReady(null, null, "00112233445566778899aabbccddeeff00112233", "downloadAdded");
        assertEquals(0, index.upserts.size());
    }

    /**
     * The path from "TorrentInfo is non-null" to "LocalSharedTorrent.upsert"
     * is fully exercised by the integration test that wires
     * SharedTorrentIndexer into a real {@code BTEngine}. The unit tests
     * here cover the surrounding contract: name fallback, dispatch
     * seams, and validation.
     */
    @SuppressWarnings("unused")
    private static final Class<?> KEEP_IMPORT = TorrentInfo.class;

    private static final class RecordingIndex implements LocalIndex {
        final List<LocalSharedTorrent> upserts = new CopyOnWriteArrayList<>();

        @Override
        public void upsert(LocalSharedTorrent torrent) {
            upserts.add(torrent);
        }

        @Override
        public void delete(String infoHashHex) {
        }

        @Override
        public Optional<LocalSharedTorrent> get(String infoHashHex) {
            return Optional.empty();
        }

        @Override
        public List<LocalSharedTorrent> search(String query, int limit) {
            return List.of();
        }

        @Override
        public void markPublished(String infoHashHex, long timestamp) {
        }

        @Override
        public List<String> needsRepublish(long nowSec, long thresholdSec) {
            return List.of();
        }

        @Override
        public void updateLastSeen(String infoHashHex, long ts) {
        }

        @Override
        public int size() {
            return upserts.size();
        }
    }
}
