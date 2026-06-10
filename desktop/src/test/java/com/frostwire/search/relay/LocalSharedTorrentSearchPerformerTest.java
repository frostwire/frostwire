/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.util.Hex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LocalSharedTorrentSearchPerformerTest {

    @Test
    void constructorRejectsNullIndex() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalSharedTorrentSearchPerformer(0L, "ubuntu", null));
    }

    @Test
    void constructorRejectsNullKeywords() {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        assertThrows(IllegalArgumentException.class,
                () -> new LocalSharedTorrentSearchPerformer(0L, null, index));
    }

    @Test
    void constructorRejectsNegativeToken() {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        assertThrows(IllegalArgumentException.class,
                () -> new LocalSharedTorrentSearchPerformer(-1L, "ubuntu", index));
    }

    @Test
    void constructorRejectsNonPositiveLimit() {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        assertThrows(IllegalArgumentException.class,
                () -> new LocalSharedTorrentSearchPerformer(0L, "ubuntu", index, 0));
    }

    @Test
    void crawlerAndDdosFlagsAreFalse() {
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                0L, "ubuntu", new InMemoryLocalIndex());
        assertFalse(p.isCrawler(), "Local search does not crawl");
        assertFalse(p.isDDOSProtectionActive(), "Local search never talks to a remote server");
    }

    @Test
    void getKeywordsReturnsConstructorValue() {
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                0L, "ubuntu", new InMemoryLocalIndex());
        assertEquals("ubuntu", p.getKeywords());
    }

    @Test
    void performReturnsEmptyListWhenIndexReturnsEmpty() {
        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                42L, "ubuntu", new InMemoryLocalIndex());
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.size(), "Single empty results callback");
        assertTrue(listener.results.get(0).isEmpty(), "No matches yields empty list");
        assertEquals(42L, listener.resultsTokens.get(0).longValue());
    }

    @Test
    void performConvertsEachRowToCompositeFileSearchResult() {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu 22.04 desktop amd64", 1_000_000L, 1));
        index.upsert(torrent("ubuntu 24.04 server arm64", 800_000L, 1));
        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                7L, "ubuntu", index);
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.size());
        List<SearchResult> out = listener.results.get(0);
        assertEquals(2, out.size(), "Both rows surfaced");
        for (SearchResult sr : out) {
            assertTrue(sr instanceof CompositeFileSearchResult, "Compose from local row");
            CompositeFileSearchResult c = (CompositeFileSearchResult) sr;
            assertEquals(LocalSharedTorrentSearchPerformer.SOURCE_NAME, c.getSource());
            assertTrue(c.isTorrent(), "Magnet-bearing result");
            assertTrue(c.getTorrentUrl().orElse("").startsWith("magnet:?xt=urn:btih:"),
                    "Magnet URL uses standard prefix");
            assertTrue(c.getDisplayName().toLowerCase().contains("ubuntu"));
            assertTrue(c.getSize() > 0L, "Size copied from index");
        }
    }

    @Test
    void performRespectsLimit() {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        for (int i = 0; i < 5; i++) {
            index.upsert(torrent("ubuntu " + i, 100L, 1));
        }
        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                1L, "ubuntu", index, 2);
        p.setListener(listener);

        p.perform();

        assertEquals(2, listener.results.get(0).size(), "Performer caps at limit");
    }

    @Test
    void toResultEmbedsInfoHashInMagnet() {
        LocalSharedTorrent t = torrent("ubuntu", 100L, 1);
        CompositeFileSearchResult c = LocalSharedTorrentSearchPerformer.toResult(t);
        String hash = t.infoHashHex();
        assertEquals(hash, c.getTorrentHash().orElseThrow(IllegalStateException::new));
        assertTrue(c.getTorrentUrl().orElse("").contains("urn:btih:" + hash),
                "Magnet URL carries the v1 hex hash");
    }

    @Test
    void performCallsOnErrorWhenIndexThrows() {
        ThrowingIndex index = new ThrowingIndex();
        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                9L, "ubuntu", index);
        p.setListener(listener);

        p.perform();

        assertEquals(0, listener.results.size(), "No result callback on error");
        assertEquals(1, listener.errors.size(), "Error callback fired once");
        assertEquals(9L, listener.errorTokens.get(0).longValue());
    }

    @Test
    void stopBeforePerformSkipsSearch() {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu", 100L, 1));
        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                1L, "ubuntu", index);
        p.setListener(listener);
        p.stop();

        p.perform();

        assertTrue(p.isStopped());
        assertTrue(listener.results.isEmpty(), "Stopped performer reports nothing");
        assertEquals(1, listener.stoppedTokens.size(), "Listener notified on stop()");
        assertEquals(1L, listener.stoppedTokens.get(0).longValue());
    }

    @Test
    void crawlIsNoOp() {
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                0L, "ubuntu", new InMemoryLocalIndex());
        p.crawl((CrawlableSearchResult) null);
    }

    @Test
    void toResultSetsCreationTimeFromAddedAt() {
        LocalSharedTorrent t = torrent("ubuntu", 100L, 1);
        CompositeFileSearchResult c = LocalSharedTorrentSearchPerformer.toResult(t);
        assertEquals(t.addedAt() * 1000L, c.getCreationTime());
    }

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

    private static final class RecordingListener implements SearchListener {
        final List<List<SearchResult>> results = new CopyOnWriteArrayList<>();
        final List<Long> resultsTokens = new CopyOnWriteArrayList<>();
        final List<Long> stoppedTokens = new CopyOnWriteArrayList<>();
        final List<SearchError> errors = new CopyOnWriteArrayList<>();
        final List<Long> errorTokens = new CopyOnWriteArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> rs) {
            results.add(new ArrayList<>(rs));
            resultsTokens.add(token);
        }

        @Override
        public void onStopped(long token) {
            stoppedTokens.add(token);
        }

        @Override
        public void onError(long token, SearchError error) {
            errors.add(error);
            errorTokens.add(token);
        }
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
            if (query == null || query.isEmpty()) {
                return Collections.emptyList();
            }
            String q = query.toLowerCase();
            List<LocalSharedTorrent> out = new ArrayList<>();
            for (LocalSharedTorrent r : rows) {
                if (r.name().toLowerCase().contains(q)) {
                    out.add(r);
                    if (out.size() >= limit) break;
                }
            }
            return out;
        }

        @Override
        public void markPublished(String infoHashHex, long timestamp) {
        }

        @Override
        public List<String> needsRepublish(long nowSec, long thresholdSec) {
            return Collections.emptyList();
        }

        @Override
        public void updateLastSeen(String infoHashHex, long ts) {
        }

        @Override
        public int size() {
            return rows.size();
        }
    }

    private static final class ThrowingIndex implements LocalIndex {
        @Override
        public void upsert(LocalSharedTorrent torrent) {
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
            throw new IllegalStateException("simulated index failure");
        }

        @Override
        public void markPublished(String infoHashHex, long timestamp) {
        }

        @Override
        public List<String> needsRepublish(long nowSec, long thresholdSec) {
            return Collections.emptyList();
        }

        @Override
        public void updateLastSeen(String infoHashHex, long ts) {
        }

        @Override
        public int size() {
            return 0;
        }
    }
}
