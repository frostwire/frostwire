/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShareVisibilityTest {

    @Test
    void filterNullPolicyKeepsAll() {
        LocalSharedTorrent a = row("aa");
        LocalSharedTorrent b = row("bb");
        List<LocalSharedTorrent> out = ShareVisibility.filter(Arrays.asList(a, b), null);
        assertEquals(2, out.size());
    }

    @Test
    void filterIncludeAllKeepsAll() {
        LocalSharedTorrent a = row("aa");
        List<LocalSharedTorrent> out =
                ShareVisibility.filter(List.of(a), ShareVisibilityPolicy.INCLUDE_ALL);
        assertEquals(1, out.size());
    }

    @Test
    void filterDropsInvisible() {
        LocalSharedTorrent keep = row("aabbcc");
        LocalSharedTorrent drop = row("ddeeff");
        ShareVisibilityPolicy onlyKeep =
                hex -> hex != null && hex.toLowerCase().startsWith("aa");
        List<LocalSharedTorrent> out =
                ShareVisibility.filter(Arrays.asList(keep, drop), onlyKeep);
        assertEquals(1, out.size());
        assertTrue(out.get(0).infoHashHex().toLowerCase().startsWith("aa"));
    }

    @Test
    void localPerformerHonorsVisibility() {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(fullTorrent("00112233445566778899aabbccddeeff00112233", "active-seed"));
        index.upsert(fullTorrent("ffeeddccbbaa99887766554433221100ffeeddcc", "historical"));

        ShareVisibilityPolicy activeOnly =
                hex -> hex != null && hex.startsWith("001122");

        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                1L, "seed", index, null, activeOnly, 50);
        RecordingListener l = new RecordingListener();
        p.setListener(l);
        p.perform();
        assertEquals(1, l.results.size());
        assertEquals(1, l.results.get(0).size());
        assertEquals("active-seed", l.results.get(0).get(0).getDisplayName());
    }

    private static LocalSharedTorrent row(String hashPrefix) {
        return fullTorrent(
                (hashPrefix + "00112233445566778899aabbccddeeff00112233").substring(0, 40),
                "n-" + hashPrefix);
    }

    private static LocalSharedTorrent fullTorrent(String hex40, String name) {
        byte[] hash = com.frostwire.util.Hex.decode(hex40);
        long now = System.currentTimeMillis() / 1000L;
        return new LocalSharedTorrent.Builder()
                .infoHash(hash)
                .name(name)
                .sizeBytes(100)
                .fileCount(1)
                .filesJson("[]")
                .publisherNodeId(new byte[20])
                .publisherEd25519Pub(new byte[32])
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    private static final class RecordingListener implements com.frostwire.search.SearchListener {
        final List<List<com.frostwire.search.SearchResult>> results =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void onResults(long token, List<? extends com.frostwire.search.SearchResult> list) {
            results.add(new java.util.ArrayList<>(list));
        }

        @Override
        public void onError(long token, com.frostwire.search.SearchError error) {
        }

        @Override
        public void onStopped(long token) {
        }
    }

    private static final class InMemoryLocalIndex implements LocalIndex {
        private final List<LocalSharedTorrent> rows =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Override
        public void upsert(LocalSharedTorrent torrent) {
            rows.removeIf(r -> r.infoHashHex().equalsIgnoreCase(torrent.infoHashHex()));
            rows.add(torrent);
        }

        @Override
        public void delete(String infoHashHex) {
            rows.removeIf(r -> r.infoHashHex().equalsIgnoreCase(infoHashHex));
        }

        @Override
        public java.util.Optional<LocalSharedTorrent> get(String infoHashHex) {
            for (LocalSharedTorrent r : rows) {
                if (r.infoHashHex().equalsIgnoreCase(infoHashHex)) {
                    return java.util.Optional.of(r);
                }
            }
            return java.util.Optional.empty();
        }

        @Override
        public List<LocalSharedTorrent> search(String query, int limit) {
            List<LocalSharedTorrent> out = new java.util.ArrayList<>();
            String q = query == null ? "" : query.toLowerCase();
            for (LocalSharedTorrent r : rows) {
                if (r.name().toLowerCase().contains(q)) {
                    out.add(r);
                    if (out.size() >= limit) {
                        break;
                    }
                }
            }
            return out;
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
            return rows.size();
        }
    }
}
