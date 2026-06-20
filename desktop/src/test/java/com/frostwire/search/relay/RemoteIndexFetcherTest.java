/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RemoteIndexFetcherTest {

    private static byte[] peerPub;

    @BeforeAll
    static void setUpClass() {
        peerPub = new byte[32];
        for (int i = 0; i < 32; i++) {
            peerPub[i] = (byte) (i + 1);
        }
    }

    @Test
    void constructorRejectsNullSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemoteIndexFetcher(null));
    }

    @Test
    void fetchCatalogRejectsBadPubkey() {
        FakeSource source = new FakeSource();
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);
        assertTrue(fetcher.fetchCatalog(null).isEmpty());
        assertTrue(fetcher.fetchCatalog(new byte[31]).isEmpty());
        assertEquals(0, source.callCount.get(), "bad pubkey must not hit source");
    }

    @Test
    void fetchCatalogReturnsEmptyWhenSourceReturnsEmpty() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.empty();
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);
        assertTrue(fetcher.fetchCatalog(peerPub).isEmpty());
        assertEquals(0, fetcher.cacheSize());
    }

    @Test
    void fetchCatalogParsesValidManifest() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.of(validManifestJson());
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);

        List<RemoteIndexFetcher.RemoteTorrentEntry> entries = fetcher.fetchCatalog(peerPub);
        assertEquals(2, entries.size());
        assertEquals("a1b2c3d4e5f6", entries.get(0).infoHashHex());
        assertEquals("ubuntu-24.04.iso", entries.get(0).name());
        assertEquals(5_000_000_000L, entries.get(0).sizeBytes());
        assertEquals(1, entries.get(0).fileCount());
        assertEquals("f0e1d2c3b4a5", entries.get(1).infoHashHex());
        assertEquals("debian-12.iso", entries.get(1).name());
        assertEquals(3_000_000_000L, entries.get(1).sizeBytes());
        assertEquals(3, entries.get(1).fileCount());
        assertEquals(1, fetcher.cacheSize());
    }

    @Test
    void fetchCatalogParsesEmptyManifest() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.of(
                "{\"v\":1,\"pub\":\"\",\"rows\":[],\"ts\":1700000000}".getBytes(StandardCharsets.UTF_8));
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);

        List<RemoteIndexFetcher.RemoteTorrentEntry> entries = fetcher.fetchCatalog(peerPub);
        assertTrue(entries.isEmpty());
        assertEquals(1, fetcher.cacheSize(), "valid empty manifest should be cached");
    }

    @Test
    void fetchCatalogReturnsEmptyOnMalformedJson() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.of("not json at all {{{".getBytes(StandardCharsets.UTF_8));
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);

        assertTrue(fetcher.fetchCatalog(peerPub).isEmpty());
        assertEquals(0, fetcher.cacheSize(), "malformed manifest must not be cached as verified");
    }

    @Test
    void fetchCatalogReturnsEmptyWhenRowsMissing() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.of(
                "{\"v\":1,\"pub\":\"abc\",\"ts\":1700000000}".getBytes(StandardCharsets.UTF_8));
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);

        assertTrue(fetcher.fetchCatalog(peerPub).isEmpty());
    }

    @Test
    void fetchCatalogReturnsEmptyWhenRowMissingField() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.of(
                "{\"v\":1,\"pub\":\"abc\",\"rows\":[{\"ih\":\"aabb\",\"n\":\"test\"}],\"ts\":1700000000}"
                        .getBytes(StandardCharsets.UTF_8));
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);

        assertTrue(fetcher.fetchCatalog(peerPub).isEmpty(), "missing s and fc must fail parsing");
    }

    @Test
    void fetchCatalogCachesAbsence() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.empty();
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);
        fetcher.fetchCatalog(peerPub);
        fetcher.fetchCatalog(peerPub);
        assertEquals(1, source.callCount.get(),
                "cached absence should skip subsequent source calls");
    }

    @Test
    void fetchCatalogCachesParsedEntries() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.of(validManifestJson());
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);
        fetcher.fetchCatalog(peerPub);
        fetcher.fetchCatalog(peerPub);
        fetcher.fetchCatalog(peerPub);
        assertEquals(1, source.callCount.get(),
                "cached entries should skip subsequent source calls");
    }

    @Test
    void evictForcesRefetch() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.empty();
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);
        fetcher.fetchCatalog(peerPub);
        fetcher.evict(peerPub);
        fetcher.fetchCatalog(peerPub);
        assertEquals(2, source.callCount.get(),
                "evict() should allow a new source call");
    }

    @Test
    void clearResetsCache() {
        FakeSource source = new FakeSource();
        source.nextBytes = Optional.empty();
        RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);
        fetcher.fetchCatalog(peerPub);
        fetcher.clear();
        fetcher.fetchCatalog(peerPub);
        assertEquals(2, source.callCount.get(),
                "clear() should reset both cached and absent maps");
    }

    @Test
    void parseManifestReturnsEmptyForNullInput() {
        List<RemoteIndexFetcher.RemoteTorrentEntry> entries = RemoteIndexFetcher.parseManifest(null);
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void parseManifestReturnsEmptyForEmptyInput() {
        List<RemoteIndexFetcher.RemoteTorrentEntry> entries =
                RemoteIndexFetcher.parseManifest(new byte[0]);
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void parseManifestReturnsNullForMalformedJson() {
        List<RemoteIndexFetcher.RemoteTorrentEntry> entries =
                RemoteIndexFetcher.parseManifest("{{{bad".getBytes(StandardCharsets.UTF_8));
        assertNull(entries, "malformed JSON should return null (not empty list)");
    }

    @Test
    void parseManifestReturnsNullWhenRowsNotArray() {
        List<RemoteIndexFetcher.RemoteTorrentEntry> entries =
                RemoteIndexFetcher.parseManifest("{\"rows\":\"not-an-array\"}".getBytes(StandardCharsets.UTF_8));
        assertNull(entries);
    }

    @Test
    void manifestCanonicalBytesAreDeterministic() {
        List<RemoteIndexFetcher.RemoteTorrentEntry> rows = List.of(
                new RemoteIndexFetcher.RemoteTorrentEntry("aabb", "name1", 100L, 1),
                new RemoteIndexFetcher.RemoteTorrentEntry("ccdd", "name2", 200L, 2));
        byte[] a = RemoteIndexFetcher.manifestCanonicalBytes(1, "pub-b64", 1700000000L, rows);
        byte[] b = RemoteIndexFetcher.manifestCanonicalBytes(1, "pub-b64", 1700000000L, rows);
        assertArrayEquals(a, b, "canonical bytes must be deterministic");
    }

    @Test
    void manifestCanonicalBytesDifferOnTimestampChange() {
        List<RemoteIndexFetcher.RemoteTorrentEntry> rows = List.of(
                new RemoteIndexFetcher.RemoteTorrentEntry("aabb", "name1", 100L, 1));
        byte[] a = RemoteIndexFetcher.manifestCanonicalBytes(1, "pub-b64", 1700000000L, rows);
        byte[] b = RemoteIndexFetcher.manifestCanonicalBytes(1, "pub-b64", 1700000001L, rows);
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    void manifestCanonicalBytesDifferOnRowsChange() {
        List<RemoteIndexFetcher.RemoteTorrentEntry> rows1 = List.of(
                new RemoteIndexFetcher.RemoteTorrentEntry("aabb", "name1", 100L, 1));
        List<RemoteIndexFetcher.RemoteTorrentEntry> rows2 = List.of(
                new RemoteIndexFetcher.RemoteTorrentEntry("aabb", "name1-changed", 100L, 1));
        byte[] a = RemoteIndexFetcher.manifestCanonicalBytes(1, "pub-b64", 1700000000L, rows1);
        byte[] b = RemoteIndexFetcher.manifestCanonicalBytes(1, "pub-b64", 1700000000L, rows2);
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    void buildManifestJsonProducesValidJsonWithSignature() {
        List<RemoteIndexFetcher.RemoteTorrentEntry> rows = List.of(
                new RemoteIndexFetcher.RemoteTorrentEntry("aabb", "test", 100L, 1));
        byte[] sig = new byte[64];
        sig[0] = 0x42;
        byte[] json = RemoteIndexFetcher.buildManifestJson(1, "pub-b64", 1700000000L, rows, sig);
        assertNotNull(json);

        List<RemoteIndexFetcher.RemoteTorrentEntry> parsed = RemoteIndexFetcher.parseManifest(json);
        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        assertEquals("aabb", parsed.get(0).infoHashHex());
        assertEquals("test", parsed.get(0).name());
    }

    // --- helpers ---

    private static byte[] validManifestJson() {
        String pubB64 = Base64.getEncoder().withoutPadding().encodeToString(peerPub);
        String json = "{\"v\":1,\"pub\":\"" + pubB64 + "\"," +
                "\"rows\":[" +
                "{\"ih\":\"a1b2c3d4e5f6\",\"n\":\"ubuntu-24.04.iso\",\"s\":5000000000,\"fc\":1}," +
                "{\"ih\":\"f0e1d2c3b4a5\",\"n\":\"debian-12.iso\",\"s\":3000000000,\"fc\":3}" +
                "],\"ts\":1700000000}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static final class FakeSource implements RemoteIndexFetcher.IndexSource {
        Optional<byte[]> nextBytes = Optional.empty();
        final AtomicInteger callCount = new AtomicInteger();

        @Override
        public Optional<byte[]> fetch(byte[] peerPub) {
            callCount.incrementAndGet();
            return nextBytes;
        }
    }
}
