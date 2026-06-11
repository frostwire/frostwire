/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Hex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IndexAnnouncementPublisherTest {

    @Test
    void constructorRejectsNulls() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        assertThrows(IllegalArgumentException.class,
                () -> new IndexAnnouncementPublisher(null, keys));
        assertThrows(IllegalArgumentException.class,
                () -> new IndexAnnouncementPublisher(index, null));
    }

    @Test
    void buildManifestReturnsNullForEmptyRows() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        IndexAnnouncementPublisher pub = new IndexAnnouncementPublisher(index, keys);

        assertNull(pub.buildManifest(null));
        assertNull(pub.buildManifest(Collections.emptyList()));
    }

    @Test
    void buildManifestProducesValidBencode() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        IndexAnnouncementPublisher pub = new IndexAnnouncementPublisher(index, keys);

        List<LocalSharedTorrent> rows = new ArrayList<>();
        rows.add(torrent("ubuntu-24.04.iso", 5_000_000_000L, 1));
        rows.add(torrent("debian-12.iso", 3_000_000_000L, 1));

        Entry manifest = pub.buildManifest(rows);
        assertNotNull(manifest, "Manifest must not be null for non-empty rows");

        byte[] bencoded = manifest.bencode();
        assertTrue(bencoded.length > 0, "Bencoded manifest must be non-empty");
        assertTrue(bencoded.length <= IndexAnnouncementPublisher.MAX_MANIFEST_BYTES,
                "Manifest must fit within size limit, was " + bencoded.length);

        // Parse the bencoded manifest
        Map<String, Entry> dict = manifest.dictionary();
        assertEquals(IndexAnnouncementPublisher.MANIFEST_VERSION,
                (int) dict.get("v").integer());
        assertNotNull(dict.get("pub"), "Publisher key must be present");
        assertNotNull(dict.get("ts"), "Timestamp must be present");

        List<Entry> rowEntries = dict.get("rows").list();
        assertEquals(2, rowEntries.size(), "Both rows must be present");

        // Check first row
        Map<String, Entry> row0 = rowEntries.get(0).dictionary();
        assertEquals("ubuntu-24.04.iso", row0.get("n").string());
        assertEquals(5_000_000_000L, row0.get("s").integer());
        assertEquals(1, (int) row0.get("fc").integer());
        assertNotNull(row0.get("ih"), "Info hash must be present");
    }

    @Test
    void buildManifestTruncatesRowsToFitSizeLimit() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        IndexAnnouncementPublisher pub = new IndexAnnouncementPublisher(index, keys);

        // Add many rows with long names to exceed the size limit
        List<LocalSharedTorrent> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            rows.add(torrent("very-long-torrent-name-that-takes-space-" + i
                    + "-extra-padding-to-fill-bytes", 1_000_000L * i, 1 + i));
        }

        Entry manifest = pub.buildManifest(rows);
        assertNotNull(manifest, "Manifest must not be null even with truncation");

        byte[] bencoded = manifest.bencode();
        assertTrue(bencoded.length <= IndexAnnouncementPublisher.MAX_MANIFEST_BYTES,
                "Truncated manifest must fit within limit, was " + bencoded.length);

        List<Entry> rowEntries = manifest.dictionary().get("rows").list();
        assertTrue(rowEntries.size() < 50,
                "Must have fewer rows than input due to truncation, got " + rowEntries.size());
        assertTrue(rowEntries.size() > 0, "Must include at least one row");
    }

    @Test
    void publishIfNeededReturnsZeroWithNullSession() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu", 100L, 1));
        IndexAnnouncementPublisher pub = new IndexAnnouncementPublisher(index, keys);

        assertEquals(0, pub.publishIfNeeded(null));
    }

    @Test
    void publishIfNeededReturnsZeroWhenNothingNeedsRepublish() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        // Empty index
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        IndexAnnouncementPublisher pub = new IndexAnnouncementPublisher(index, keys);

        assertEquals(0, pub.publishIfNeeded(null));
    }

    // --- helpers ---

    private static final AtomicInteger HASH_COUNTER = new AtomicInteger();

    private static LocalSharedTorrent torrent(String name, long size, int fileCount) {
        byte[] hash = new byte[20];
        int n = HASH_COUNTER.incrementAndGet();
        hash[0] = (byte) (n >>> 24);
        hash[1] = (byte) (n >>> 16);
        hash[2] = (byte) (n >>> 8);
        hash[3] = (byte) n;
        byte[] nodeId = new byte[20];
        byte[] ed = new byte[32];
        long now = System.currentTimeMillis() / 1000L;
        return new LocalSharedTorrent.Builder()
                .infoHash(hash)
                .name(name)
                .sizeBytes(size)
                .fileCount(fileCount)
                .filesJson("[]")
                .publisherNodeId(nodeId)
                .publisherEd25519Pub(ed)
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    private static final class InMemoryLocalIndex implements LocalIndex {
        private final List<LocalSharedTorrent> rows = new ArrayList<>();

        @Override
        public void upsert(LocalSharedTorrent torrent) {
            rows.removeIf(r -> r.infoHashHex().equals(torrent.infoHashHex()));
            rows.add(torrent);
        }

        @Override
        public void delete(String infoHashHex) {
            rows.removeIf(r -> r.infoHashHex().equalsIgnoreCase(infoHashHex));
        }

        @Override
        public Optional<LocalSharedTorrent> get(String infoHashHex) {
            for (LocalSharedTorrent r : rows) {
                if (r.infoHashHex().equalsIgnoreCase(infoHashHex)) {
                    return Optional.of(r);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<LocalSharedTorrent> search(String query, int limit) {
            return Collections.emptyList();
        }

        @Override
        public void markPublished(String infoHashHex, long timestamp) {
        }

        @Override
        public List<String> needsRepublish(long nowSec, long thresholdSec) {
            List<String> out = new ArrayList<>();
            for (LocalSharedTorrent r : rows) {
                out.add(r.infoHashHex());
            }
            return out;
        }

        @Override
        public void updateLastSeen(String infoHashHex, long ts) {
        }

        @Override
        public int size() {
            return rows.size();
        }
    }
}
