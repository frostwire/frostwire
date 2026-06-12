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
        return torrent(name, size, fileCount, new byte[32]);
    }

    private static LocalSharedTorrent torrent(String name, long size, int fileCount, byte[] publisherPub) {
        byte[] hash = new byte[20];
        int n = HASH_COUNTER.incrementAndGet();
        hash[0] = (byte) (n >>> 24);
        hash[1] = (byte) (n >>> 16);
        hash[2] = (byte) (n >>> 8);
        hash[3] = (byte) n;
        byte[] nodeId = new byte[20];
        long now = System.currentTimeMillis() / 1000L;
        return new LocalSharedTorrent.Builder()
                .infoHash(hash)
                .name(name)
                .sizeBytes(size)
                .fileCount(fileCount)
                .filesJson("[]")
                .publisherNodeId(nodeId)
                .publisherEd25519Pub(publisherPub)
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    // --- karma-weighted sorting tests ---

    private static java.security.KeyPair testKarmaKeyPair() throws Exception {
        return java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static byte[] testKarmaOwnerPub(java.security.KeyPair kp) {
        return IdentityRecord.extractRawEd25519(kp.getPublic());
    }

    @Test
    void constructorAcceptsNullKarmaCache() {
        // Backward compat: no karma cache still works.
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                0L, "ubuntu", new InMemoryLocalIndex(), (PeerKarmaCache) null);
        assertNotNull(p);
    }

    @Test
    void performSortsByKarmaDescendingWhenCacheProvided() throws Exception {
        java.security.KeyPair kp = testKarmaKeyPair();
        byte[] ownerPub = testKarmaOwnerPub(kp);

        byte[] lowKarmaPub = new byte[32]; lowKarmaPub[31] = 0x01;
        byte[] midKarmaPub = new byte[32]; midKarmaPub[31] = 0x02;
        byte[] highKarmaPub = new byte[32]; highKarmaPub[31] = 0x03;

        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu 22.04", 1L, 1, lowKarmaPub));
        index.upsert(torrent("ubuntu 24.04", 1L, 1, midKarmaPub));
        index.upsert(torrent("ubuntu 23.04", 1L, 1, highKarmaPub));

        FakeKarmaChainSource source = new FakeKarmaChainSource(ownerPub, kp);
        source.endorseCount(lowKarmaPub, 1);
        source.endorseCount(midKarmaPub, 5);
        source.endorseCount(highKarmaPub, 10);

        PeerKarmaCache karma = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));

        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                1L, "ubuntu", index, karma);
        p.setListener(listener);

        p.perform();

        List<SearchResult> out = listener.results.get(0);
        assertEquals(3, out.size());
        assertEquals("ubuntu 23.04", out.get(0).getDisplayName(), "high karma first");
        assertEquals("ubuntu 24.04", out.get(1).getDisplayName(), "mid karma second");
        assertEquals("ubuntu 22.04", out.get(2).getDisplayName(), "low karma last");
    }

    @Test
    void performStableSortForEqualKarma() throws Exception {
        java.security.KeyPair kp = testKarmaKeyPair();
        byte[] ownerPub = testKarmaOwnerPub(kp);

        byte[] pubA = new byte[32]; pubA[31] = 0x01;
        byte[] pubB = new byte[32]; pubB[31] = 0x02;
        byte[] pubC = new byte[32]; pubC[31] = 0x03;

        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu A", 1L, 1, pubA));
        index.upsert(torrent("ubuntu B", 1L, 1, pubB));
        index.upsert(torrent("ubuntu C", 1L, 1, pubC));

        FakeKarmaChainSource source = new FakeKarmaChainSource(ownerPub, kp);
        source.endorseCount(pubA, 3);
        source.endorseCount(pubB, 3);
        source.endorseCount(pubC, 3);

        PeerKarmaCache karma = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));

        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                1L, "ubuntu", index, karma);
        p.setListener(listener);

        p.perform();

        List<SearchResult> out = listener.results.get(0);
        // Stable: ties keep insertion order from FTS5 search
        assertEquals("ubuntu A", out.get(0).getDisplayName());
        assertEquals("ubuntu B", out.get(1).getDisplayName());
        assertEquals("ubuntu C", out.get(2).getDisplayName());
    }

    @Test
    void performHandlesZeroKarmaPublishers() throws Exception {
        java.security.KeyPair kp = testKarmaKeyPair();
        byte[] ownerPub = testKarmaOwnerPub(kp);

        byte[] pub = new byte[32];
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu 22.04", 1L, 1, pub));
        // No entry in FakeKarmaChainSource -> getKarma returns 0
        FakeKarmaChainSource source = new FakeKarmaChainSource(ownerPub, kp);
        PeerKarmaCache karma = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));

        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                1L, "ubuntu", index, karma);
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.get(0).size());
    }

    @Test
    void performHandlesPlaceholderPublisherPubkey() {
        // All-zero 32-byte pubkey is the placeholder for "publisher unknown".
        // The performer's karmaFor must not throw, must return 0, and the
        // result must still surface.
        byte[] placeholder = new byte[32];
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu", 1L, 1, placeholder));

        FakeKarmaChainSource source;
        PeerKarmaCache karma;
        try {
            java.security.KeyPair kp = testKarmaKeyPair();
            source = new FakeKarmaChainSource(testKarmaOwnerPub(kp), kp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        karma = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));

        RecordingListener listener = new RecordingListener();
        LocalSharedTorrentSearchPerformer p = new LocalSharedTorrentSearchPerformer(
                1L, "ubuntu", index, karma);
        p.setListener(listener);

        p.perform();

        // Must not throw; result surfaces with 0 karma
        assertEquals(1, listener.results.get(0).size());
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

    /**
     * Hand-rolled karma source: for each peer pub key that has been
     * configured with {@link #endorseCount}, returns a manifest with
     * a 2-entry chain (1 EC + N EN) signed by that peer.
     */
    private static final class FakeKarmaChainSource implements KarmaChainSource {
        private final java.util.Map<String, Integer> endorseCounts = new java.util.HashMap<>();
        private final byte[] pubRaw;
        private final java.security.KeyPair keyPair;
        private final BitcoinBlockReference block;

        FakeKarmaChainSource(byte[] pubRaw, java.security.KeyPair keyPair) {
            this.pubRaw = pubRaw;
            this.keyPair = keyPair;
            byte[] hash = new byte[32];
            for (int i = 0; i < 32; i++) hash[i] = (byte) (i + 1);
            this.block = new BitcoinBlockReference(850000L, hash);
        }

        void endorseCount(byte[] peerPub, int count) {
            endorseCounts.put(com.frostwire.util.Hex.encode(peerPub), count);
        }

        @Override
        public com.frostwire.jlibtorrent.Entry fetchManifest(byte[] peerPub) {
            String key = com.frostwire.util.Hex.encode(peerPub);
            Integer count = endorseCounts.get(key);
            if (count == null) {
                return null; // peer has no chain
            }
            return buildVerifiedManifest(pubRaw, keyPair, block, count);
        }
    }

    private static com.frostwire.jlibtorrent.Entry buildVerifiedManifest(
            byte[] ownerPub, java.security.KeyPair keyPair,
            BitcoinBlockReference block, int endorsementCount) {
        try {
            java.util.List<com.frostwire.jlibtorrent.Entry> entries = new java.util.ArrayList<>();
            // Use MAX_ENERGY in the EC so the verifier permits up to
            // MAX_ENERGY endorsements in this epoch.
            KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                    KarmaChainEntry.GENESIS_PREV_HASH, 0, ownerPub, block,
                    KarmaConstants.MAX_ENERGY, keyPair.getPrivate());
            entries.add(entryDict(ec));
            byte[] prev = ec.entryHash();
            for (int i = 0; i < endorsementCount; i++) {
                byte[] fakePeer = new byte[32];
                fakePeer[31] = (byte) (i + 1);
                KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                        prev, i + 1, ownerPub, block, fakePeer, new byte[20], 1,
                        keyPair.getPrivate());
                entries.add(entryDict(en));
                prev = en.entryHash();
            }
            java.util.Map<String, Object> manifest = new java.util.HashMap<>();
            manifest.put("v", new com.frostwire.jlibtorrent.Entry(1L));
            manifest.put("len", new com.frostwire.jlibtorrent.Entry(
                    (long) (endorsementCount + 1)));
            manifest.put("head", new com.frostwire.jlibtorrent.Entry(
                    com.frostwire.util.Hex.encode(prev)));
            manifest.put("ts", new com.frostwire.jlibtorrent.Entry(0L));
            manifest.put("entries", com.frostwire.jlibtorrent.Entry.fromList(entries));
            return com.frostwire.jlibtorrent.Entry.fromMap(manifest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static com.frostwire.jlibtorrent.Entry entryDict(KarmaChainEntry e) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("k", new com.frostwire.jlibtorrent.Entry(e.kind().code()));
        m.put("seq", new com.frostwire.jlibtorrent.Entry(e.seq()));
        m.put("bh", new com.frostwire.jlibtorrent.Entry(e.blockHeight()));
        m.put("bkh", new com.frostwire.jlibtorrent.Entry(
                com.frostwire.util.Hex.encode(e.blockHash())));
        m.put("ph", new com.frostwire.jlibtorrent.Entry(
                com.frostwire.util.Hex.encode(e.prevHash())));
        m.put("pub", new com.frostwire.jlibtorrent.Entry(
                java.util.Base64.getEncoder().withoutPadding()
                        .encodeToString(e.endorserPub())));
        m.put("s", new com.frostwire.jlibtorrent.Entry(
                java.util.Base64.getEncoder().withoutPadding()
                        .encodeToString(e.signature())));
        if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
            m.put("ep", new com.frostwire.jlibtorrent.Entry(e.epoch()));
            m.put("en", new com.frostwire.jlibtorrent.Entry(String.format(
                    java.util.Locale.ROOT, "%.3f", e.energy())));
        } else {
            m.put("pp", new com.frostwire.jlibtorrent.Entry(
                    java.util.Base64.getEncoder().withoutPadding()
                            .encodeToString(e.peerPub())));
            m.put("ih", new com.frostwire.jlibtorrent.Entry(
                    com.frostwire.util.Hex.encode(e.infoHash())));
            m.put("sd", new com.frostwire.jlibtorrent.Entry(
                    e.scoreDelta().longValue()));
        }
        return com.frostwire.jlibtorrent.Entry.fromMap(m);
    }
}
