/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RemoteKarmaChainFetcherTest {

    private static KeyPair keyPair;
    private static byte[] pubRaw;
    private static byte[] peerPub;
    private static BitcoinBlockReference block;

    @BeforeAll
    static void setUpClass() throws Exception {
        keyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        pubRaw = IdentityRecord.extractRawEd25519(keyPair.getPublic());
        peerPub = new byte[32];
        for (int i = 0; i < 32; i++) peerPub[i] = (byte) (i + 1);
        byte[] hash = new byte[32];
        for (int i = 0; i < 32; i++) hash[i] = (byte) (i + 1);
        block = new BitcoinBlockReference(850000L, hash);
    }

    @Test
    void constructorRejectsNullSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemoteKarmaChainFetcher(null));
    }

    @Test
    void fetchChainRejectsBadPubkey() {
        FakeSource source = new FakeSource();
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
        assertNull(fetcher.fetchChain(null));
        assertNull(fetcher.fetchChain(new byte[31]));
        assertEquals(0, source.callCount.get(), "bad pubkey must not hit source");
    }

    @Test
    void fetchChainReturnsNullWhenSourceReturnsNull() {
        FakeSource source = new FakeSource();
        source.nextManifest = null;
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
        assertNull(fetcher.fetchChain(peerPub));
        assertEquals(0, fetcher.cacheSize());
    }

    @Test
    void fetchChainReturnsNullWhenManifestIsNotDict() {
        FakeSource source = new FakeSource();
        source.nextManifest = new Entry("not-a-dict");
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
        assertNull(fetcher.fetchChain(peerPub));
    }

    @Test
    void fetchChainReturnsNullWhenManifestHasNoEntries() {
        FakeSource source = new FakeSource();
        source.nextManifest = Entry.fromMap(new HashMap<>());
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
        assertNull(fetcher.fetchChain(peerPub));
    }

    @Test
    void fetchChainReturnsNullWhenChainFailsVerification() {
        // All-zero signature: verifySignature will return false, so
        // KarmaChain.verify will reject the chain.
        Map<String, Object> manifest = buildManifestWithBadSignature();
        FakeSource source = new FakeSource();
        source.nextManifest = Entry.fromMap(manifest);
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
        assertNull(fetcher.fetchChain(peerPub));
    }

    @Test
    void fetchChainReturnsChainWhenVerified() {
        // Build a real 2-entry chain (1 commitment + 1 endorsement),
        // sign both, build the manifest, and verify the fetcher returns it.
        KarmaChainEntry ec = KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw, block, 5.0,
                keyPair.getPrivate());
        KarmaChainEntry en = KarmaChainEntry.createEndorsement(
                ec.entryHash(), 1, pubRaw, block, peerPub, new byte[20], 1,
                keyPair.getPrivate());

        Map<String, Object> manifest = new HashMap<>();
        manifest.put("v", new Entry(1L));
        manifest.put("len", new Entry(2L));
        manifest.put("head", new Entry(Hex.encode(en.entryHash())));
        manifest.put("ts", new Entry(0L));
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.fromMap(publishDictOf(ec)));
        entries.add(Entry.fromMap(publishDictOf(en)));
        manifest.put("entries", Entry.fromList(entries));

        FakeSource source = new FakeSource();
        source.nextManifest = Entry.fromMap(manifest);
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);

        List<KarmaChainEntry> fetched = fetcher.fetchChain(peerPub);
        assertNotNull(fetched);
        assertEquals(2, fetched.size());
        assertEquals(KarmaChainEntry.Kind.EPOCH_COMMITMENT, fetched.get(0).kind());
        assertEquals(KarmaChainEntry.Kind.ENDORSEMENT, fetched.get(1).kind());
        assertEquals(1, fetcher.cacheSize());
    }

    @Test
    void fetchChainCachesAbsence() {
        FakeSource source = new FakeSource();
        source.nextManifest = null;
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
        assertNull(fetcher.fetchChain(peerPub));
        fetcher.fetchChain(peerPub);
        assertEquals(1, source.callCount.get(),
                "cached absence should skip subsequent source calls");
    }

    @Test
    void evictForcesRefetch() {
        FakeSource source = new FakeSource();
        source.nextManifest = null;
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
        fetcher.fetchChain(peerPub);
        fetcher.evict(peerPub);
        fetcher.fetchChain(peerPub);
        assertEquals(2, source.callCount.get(),
                "evict() should allow a new source call");
    }

    @Test
    void clearResetsCache() {
        FakeSource source = new FakeSource();
        source.nextManifest = null;
        RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
        fetcher.fetchChain(peerPub);
        fetcher.clear();
        fetcher.fetchChain(peerPub);
        assertEquals(2, source.callCount.get(),
                "clear() should reset both verified and absent caches");
    }

    // --- PeerKarmaCache tests ---

    @Test
    void peerKarmaCacheRejectsNullFetcher() {
        assertThrows(IllegalArgumentException.class, () -> new PeerKarmaCache(null));
    }

    @Test
    void peerKarmaCacheReturnsZeroForBadInputs() {
        PeerKarmaCache cache = new PeerKarmaCache(
                new RemoteKarmaChainFetcher(new FakeSource()));
        assertEquals(0, cache.getKarma(null));
        assertEquals(0, cache.getKarma(new byte[31]));
    }

    @Test
    void peerKarmaCacheReturnsZeroForEmptyChain() {
        PeerKarmaCache cache = new PeerKarmaCache(
                new RemoteKarmaChainFetcher(new FixedSource(null)));
        assertEquals(0, cache.getKarma(peerPub));
    }

    @Test
    void peerKarmaCacheCountsEndorsements() {
        List<KarmaChainEntry> chain = new ArrayList<>();
        chain.add(KarmaChainEntry.createEpochCommitment(
                KarmaChainEntry.GENESIS_PREV_HASH, 0, pubRaw, block, 5.0,
                keyPair.getPrivate()));
        chain.add(KarmaChainEntry.createEndorsement(
                chain.get(0).entryHash(), 1, pubRaw, block, peerPub, new byte[20], 1,
                keyPair.getPrivate()));
        chain.add(KarmaChainEntry.createEndorsement(
                chain.get(1).entryHash(), 2, pubRaw, block, peerPub, new byte[20], 1,
                keyPair.getPrivate()));

        // Build a verified manifest so the fetcher can reconstruct + verify.
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("v", new Entry(1L));
        manifest.put("len", new Entry(3L));
        manifest.put("head", new Entry(Hex.encode(chain.get(2).entryHash())));
        manifest.put("ts", new Entry(0L));
        List<Entry> entries = new ArrayList<>();
        for (KarmaChainEntry e : chain) {
            entries.add(Entry.fromMap(publishDictOf(e)));
        }
        manifest.put("entries", Entry.fromList(entries));

        FakeSource source = new FakeSource();
        source.nextManifest = Entry.fromMap(manifest);
        PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));

        assertEquals(2, cache.getKarma(peerPub),
                "score counts ENDORSEMENT entries only");
    }

    @Test
    void peerKarmaCacheMemoizesScore() {
        FakeSource source = new FakeSource();
        source.nextManifest = null;
        PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));
        cache.getKarma(peerPub);
        cache.getKarma(peerPub);
        cache.getKarma(peerPub);
        assertEquals(1, source.callCount.get(),
                "score should be memoized after first lookup");
        assertTrue(cache.cacheHitCount() >= 2);
    }

    @Test
    void peerKarmaCacheEvictClearsBoth() {
        FakeSource source = new FakeSource();
        source.nextManifest = null;
        PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));
        cache.getKarma(peerPub);
        cache.evict(peerPub);
        cache.getKarma(peerPub);
        assertEquals(2, source.callCount.get());
    }

    // --- helpers ---

    private static Map<String, Object> buildManifestWithBadSignature() {
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("v", new Entry(1L));
        manifest.put("len", new Entry(1L));
        manifest.put("head", new Entry(Hex.encode(new byte[32])));
        manifest.put("ts", new Entry(0L));

        Map<String, Object> entry = new HashMap<>();
        entry.put("k", new Entry("EC"));
        entry.put("seq", new Entry(0L));
        entry.put("bh", new Entry(850000L));
        entry.put("bkh", new Entry(Hex.encode(block.hash())));
        entry.put("ph", new Entry(Hex.encode(KarmaChainEntry.GENESIS_PREV_HASH)));
        entry.put("pub", new Entry(Base64.getEncoder()
                .withoutPadding().encodeToString(pubRaw)));
        entry.put("s", new Entry(Base64.getEncoder()
                .withoutPadding().encodeToString(new byte[64])));
        entry.put("ep", new Entry(1L));
        entry.put("en", new Entry("5.00"));
        manifest.put("entries", Entry.fromList(List.of(Entry.fromMap(entry))));
        return manifest;
    }

    private static Map<String, Object> publishDictOf(KarmaChainEntry e) {
        Map<String, Object> m = new HashMap<>();
        m.put("k", new Entry(e.kind().code()));
        m.put("seq", new Entry(e.seq()));
        m.put("bh", new Entry(e.blockHeight()));
        m.put("bkh", new Entry(Hex.encode(e.blockHash())));
        m.put("ph", new Entry(Hex.encode(e.prevHash())));
        m.put("pub", new Entry(Base64.getEncoder()
                .withoutPadding().encodeToString(e.endorserPub())));
        m.put("s", new Entry(Base64.getEncoder()
                .withoutPadding().encodeToString(e.signature())));
        if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
            m.put("ep", new Entry(e.epoch()));
            m.put("en", new Entry(String.format(java.util.Locale.ROOT,
                    "%.3f", e.energy())));
        } else {
            m.put("pp", new Entry(Base64.getEncoder()
                    .withoutPadding().encodeToString(e.peerPub())));
            m.put("ih", new Entry(Hex.encode(e.infoHash())));
            m.put("sd", new Entry(e.scoreDelta().longValue()));
        }
        return m;
    }

    private static final class FakeSource implements KarmaChainSource {
        Entry nextManifest;
        final AtomicInteger callCount = new AtomicInteger();
        final AtomicReference<byte[]> lastPeerPub = new AtomicReference<>();

        @Override
        public Entry fetchManifest(byte[] peerPub) {
            callCount.incrementAndGet();
            lastPeerPub.set(peerPub.clone());
            return nextManifest;
        }
    }

    private static final class FixedSource implements KarmaChainSource {
        private final Entry manifest;
        FixedSource(Entry manifest) {
            this.manifest = manifest;
        }
        @Override
        public Entry fetchManifest(byte[] peerPub) {
            return manifest;
        }
    }
}
