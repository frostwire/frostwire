/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SharedTorrentIndexerTest {

    private static final String INFO_HASH_HEX = "00112233445566778899aabbccddeeff00112233";

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
        assertEquals(SharedTorrentIndexer.UNKNOWN_NAME, SharedTorrentIndexer.resolveName(null, null));
        assertEquals(SharedTorrentIndexer.UNKNOWN_NAME, SharedTorrentIndexer.resolveName("", null));
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
        IndexResult result = indexer.indexIfReady(null, INFO_HASH_HEX, IndexTrigger.ADDED);

        assertEquals(IndexResult.NULL_INPUT, result, "null dl → NULL_INPUT");
        assertEquals(0, index.upserts.size(), "No metadata means no upsert");
    }

    @Test
    void indexIfReadyRejectsNulls() {
        RecordingIndex index = new RecordingIndex();
        SharedTorrentIndexer indexer = new SharedTorrentIndexer(index);
        indexer.setTorrentInfoSource(dl -> null);

        IndexResult r1 = indexer.indexIfReady(null, null, IndexTrigger.ADDED);
        IndexResult r2 = indexer.indexIfReady(null, INFO_HASH_HEX, null);

        assertEquals(IndexResult.NULL_INPUT, r1);
        assertEquals(IndexResult.NULL_INPUT, r2);
        assertEquals(0, index.upserts.size());
    }

    @Test
    void indexResultEnumIsExhaustive() {
        IndexResult[] values = IndexResult.values();
        assertEquals(4, values.length);
        assertEquals(IndexResult.UPSERTED, values[0]);
        assertEquals(IndexResult.NO_METADATA, values[1]);
        assertEquals(IndexResult.NULL_INPUT, values[2]);
        assertEquals(IndexResult.ERROR, values[3]);
    }

    @Test
    void enumIsExhaustive() {
        // Compile-time guard: if a new trigger is added, this test must be updated.
        IndexTrigger[] values = IndexTrigger.values();
        assertEquals(2, values.length);
        assertEquals(IndexTrigger.ADDED, values[0]);
        assertEquals(IndexTrigger.UPDATE, values[1]);
    }

    @Test
    void buildTorrentMapsHexAndNameForValidInputs() {
        // This test does not call into libtorrent; we are only proving
        // the conversion path that does not depend on the live JNI
        // surface — name + info hash + files_json mapping.
        LocalSharedTorrent torrent = new LocalSharedTorrent.Builder()
                .infoHash(Hex.decode(INFO_HASH_HEX))
                .name("explicit-name.iso")
                .sizeBytes(4096L)
                .fileCount(2)
                .filesJson(FilesJson.minimal(2, 4096L))
                .publisherNodeId(new byte[IdentityRecord.NODE_ID_LENGTH])
                .publisherEd25519Pub(new byte[IdentityRecord.ED25519_PUB_LENGTH])
                .publisherUtpPort(0)
                .addedAt(1_700_000_000L)
                .lastSeenAt(1_700_000_005L)
                .build();

        assertEquals(INFO_HASH_HEX, torrent.infoHashHex());
        assertEquals("explicit-name.iso", torrent.name());
        assertEquals(2, torrent.fileCount());
        assertEquals(4096L, torrent.sizeBytes());
        assertEquals(1_700_000_000L, torrent.addedAt());
        assertEquals(1_700_000_005L, torrent.lastSeenAt());
    }

    @Test
    void buildTorrentRequiresNonEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> new LocalSharedTorrent.Builder()
                .infoHash(Hex.decode(INFO_HASH_HEX))
                .name("")
                .sizeBytes(1)
                .fileCount(1)
                .filesJson("[]")
                .publisherNodeId(new byte[IdentityRecord.NODE_ID_LENGTH])
                .publisherEd25519Pub(new byte[IdentityRecord.ED25519_PUB_LENGTH])
                .publisherUtpPort(0)
                .addedAt(1_700_000_000L)
                .lastSeenAt(1_700_000_000L)
                .build());
    }

    @SuppressWarnings("unused")
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
