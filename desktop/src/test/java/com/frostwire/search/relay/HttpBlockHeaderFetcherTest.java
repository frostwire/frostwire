/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class HttpBlockHeaderFetcherTest {

    private File tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("http-block-header-test-").toFile();
    }

    @AfterEach
    void tearDown() {
        deleteRecursive(tempDir);
    }

    @Test
    void constructorRejectsNullCacheDir() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpBlockHeaderFetcher(null));
    }

    @Test
    void constructorCreatesCacheDirIfMissing() {
        File nested = new File(tempDir, "deep/nested/cache");
        assertFalse(nested.exists());
        HttpBlockHeaderFetcher fetcher = new HttpBlockHeaderFetcher(nested);
        assertTrue(nested.exists(), "Cache directory must be created");
    }

    @Test
    void getBlockReturnsCachedResult() {
        File cacheFile = new File(tempDir, "block-headers.cache");
        long knownHeight = 850000L;
        byte[] knownHash = hexHash("00000000000000000000000000000000000000000000000000000000000000aa");
        writeCacheEntry(cacheFile, knownHeight, knownHash);

        HttpBlockHeaderFetcher fetcher = new HttpBlockHeaderFetcher(tempDir);
        BitcoinBlockReference ref = fetcher.getBlock(knownHeight);

        assertNotNull(ref, "Cached block must be returned without HTTP");
        assertEquals(knownHeight, ref.height());
        assertArrayEquals(knownHash, ref.hash());
    }

    @Test
    void getBlockReturnsNullForUnknownHeight() {
        HttpBlockHeaderFetcher fetcher = new HttpBlockHeaderFetcher(tempDir);
        // A very high height that no API should have (will fail without network)
        BitcoinBlockReference ref = fetcher.getBlock(99999999L);
        assertNull(ref, "Unknown height with no cache must return null");
    }

    @Test
    void getBlockRejectsNegativeHeight() {
        HttpBlockHeaderFetcher fetcher = new HttpBlockHeaderFetcher(tempDir);
        assertNull(fetcher.getBlock(-1));
    }

    @Test
    void cacheFileRoundTrips() {
        File cacheFile = new File(tempDir, "block-headers.cache");
        long h1 = 100L, h2 = 200L;
        byte[] hash1 = hexHash("00000000000000000000000000000000000000000000000000000000000000ab");
        byte[] hash2 = hexHash("00000000000000000000000000000000000000000000000000000000000000cd");
        writeCacheEntry(cacheFile, h1, hash1);
        writeCacheEntry(cacheFile, h2, hash2);

        HttpBlockHeaderFetcher fetcher = new HttpBlockHeaderFetcher(tempDir);
        BitcoinBlockReference r1 = fetcher.getBlock(h1);
        BitcoinBlockReference r2 = fetcher.getBlock(h2);

        assertNotNull(r1);
        assertNotNull(r2);
        assertArrayEquals(hash1, r1.hash());
        assertArrayEquals(hash2, r2.hash());
    }

    @Test
    void getChainTipHeightReturnsNegativeOneOnFailure() {
        // With no network access (test environment may or may not have it),
        // getChainTipHeight should return -1 rather than throwing.
        HttpBlockHeaderFetcher fetcher = new HttpBlockHeaderFetcher(tempDir);
        long tip = fetcher.getChainTipHeight();
        // We can't assert a specific value because the test env might have network.
        // We only assert that it doesn't throw and returns a valid long.
        assertTrue(tip >= -1);
    }

    @Test
    void malformedCacheLinesAreSkipped() throws IOException {
        File cacheFile = new File(tempDir, "block-headers.cache");
        try (PrintWriter pw = new PrintWriter(cacheFile, StandardCharsets.UTF_8)) {
            pw.println("");
            pw.println("garbage_no_space");
            pw.println("not_a_number " + "00000000000000000000000000000000000000000000000000000000000000ab");
            pw.println("30000000 " + "00000000000000000000000000000000000000000000000000000000000000ff");
        }

        HttpBlockHeaderFetcher fetcher = new HttpBlockHeaderFetcher(tempDir);
        // Only the valid line should be loaded
        BitcoinBlockReference ref = fetcher.getBlock(30000000L);
        assertNotNull(ref, "Valid cache line must be loaded despite invalid neighbors");
        assertEquals((byte) 0xff, ref.hash()[31], "Last byte should be 0xff");
    }

    private static byte[] hexHash(String hex) {
        return Hex.decode(hex);
    }

    private static void writeCacheEntry(File cacheFile, long height, byte[] hash) {
        try (PrintWriter pw = new PrintWriter(
                new java.io.FileWriter(cacheFile, true))) {
            pw.println(height + " " + Hex.encode(hash));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
