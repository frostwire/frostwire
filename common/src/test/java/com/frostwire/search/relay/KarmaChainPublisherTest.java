/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class KarmaChainPublisherTest {

    private static final int LOW_DIFFICULTY = 4;
    private static final byte[] FAKE_PEER_PUB = new byte[]{
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
    };
    private static final byte[] FAKE_INFO_HASH = new byte[20];

    @TempDir
    Path tempDir;

    private File dbFile;
    private KarmaChainTable table;
    private IdentityKeys identity;
    private FakeBlockSource blockSource;
    private KarmaChainWriter writer;
    private KarmaChainPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tempDir.resolve("karma-test.db").toFile();
        table = KarmaChainTable.open(dbFile);
        identity = IdentityKeys.generate(LOW_DIFFICULTY);
        blockSource = new FakeBlockSource()
                .withTip(144L)
                .withBlock(144L, hashForHeight(144L));
        writer = new KarmaChainWriter(identity, blockSource, table);
        publisher = new KarmaChainPublisher(writer, identity);
    }

    @AfterEach
    void tearDown() {
        if (table != null) {
            table.close();
        }
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainPublisher(null, identity));
        assertThrows(IllegalArgumentException.class,
                () -> new KarmaChainPublisher(writer, null));
    }

    @Test
    void buildManifestReturnsNullForEmptyChain() {
        assertNull(publisher.buildManifest(null));
        assertNull(publisher.buildManifest(java.util.Collections.emptyList()));
    }

    @Test
    void buildManifestForSingleEntryHasExpectedFields() {
        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        List<KarmaChainEntry> chain = writer.chain().entries();
        Entry manifest = publisher.buildManifest(chain);
        assertNotNull(manifest);

        byte[] bencoded = manifest.bencode();
        assertTrue(bencoded.length > 0);
        assertTrue(bencoded.length <= KarmaChainPublisher.MAX_MANIFEST_BYTES,
                "Manifest must fit, was " + bencoded.length);

        Map<String, Entry> dict = manifest.dictionary();
        assertEquals(KarmaChainPublisher.MANIFEST_VERSION, (int) dict.get("v").integer());
        assertNotNull(dict.get("pub"));
        assertEquals(2L, dict.get("len").integer(), "1 commitment + 1 endorsement");

        List<Entry> entries = dict.get("entries").list();
        assertEquals(2, entries.size());

        // First entry is EPOCH_COMMITMENT
        Map<String, Entry> ec = entries.get(0).dictionary();
        assertEquals("EC", ec.get("k").string());
        assertEquals(0L, ec.get("seq").integer());
        assertEquals(144L, ec.get("bh").integer());
        assertNotNull(ec.get("ep"), "epoch must be present for EC");
        assertNotNull(ec.get("en"), "energy must be present for EC");
        assertNotNull(ec.get("s"), "signature must be present");

        // Second entry is ENDORSEMENT
        Map<String, Entry> en = entries.get(1).dictionary();
        assertEquals("EN", en.get("k").string());
        assertEquals(1L, en.get("seq").integer());
        assertNotNull(en.get("pp"), "peerPub must be present for EN");
        assertNotNull(en.get("ih"), "infoHash must be present for EN");
        assertNotNull(en.get("sd"), "scoreDelta must be present for EN");
    }

    @Test
    void buildManifestTruncatesOldestEntriesWhenOversize() {
        // Build a chain long enough to require truncation. With ~250B
        // per entry, ~10 entries should push us past the 950B cap.
        blockSource.withTip(144L).withBlock(144L, hashForHeight(144L));
        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        // Advance epochs and add endorsements to grow the chain.
        for (int i = 1; i < 12; i++) {
            long tip = 144L * (i + 1);
            blockSource.withTip(tip).withBlock(tip, hashForHeight(tip));
            writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);
        }

        List<KarmaChainEntry> chain = writer.chain().entries();
        assertTrue(chain.size() > 5, "test requires a non-trivial chain, was " + chain.size());

        Entry manifest = publisher.buildManifest(chain);
        assertNotNull(manifest);
        byte[] bencoded = manifest.bencode();
        assertTrue(bencoded.length <= KarmaChainPublisher.MAX_MANIFEST_BYTES,
                "Truncated manifest must fit, was " + bencoded.length);

        List<Entry> included = manifest.dictionary().get("entries").list();
        assertTrue(included.size() < chain.size(),
                "Some entries must be truncated, got " + included.size() + " of " + chain.size());
        assertTrue(included.size() > 0, "At least one entry must be included");

        // The head (last entry) must be preserved when truncating.
        long headSeq = included.get(included.size() - 1).dictionary().get("seq").integer();
        long actualHeadSeq = chain.get(chain.size() - 1).seq();
        assertEquals(actualHeadSeq, headSeq,
                "Head entry must be the last entry of the full chain");

        // len field reports the full chain length, not the truncated count
        assertEquals((long) chain.size(), manifest.dictionary().get("len").integer());
    }

    @Test
    void publishIfNeededReturnsZeroForEmptyChain() {
        assertEquals(0, publisher.publishIfNeeded(null));
    }

    @Test
    void publishIfNeededReturnsZeroWithNullSession() {
        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);
        assertEquals(0, publisher.publishIfNeeded(null));
    }

    @Test
    void publishedManifestHeadHashMatchesChain() {
        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        // Build the manifest and decode its head field directly
        List<KarmaChainEntry> chain = writer.chain().entries();
        Entry manifest = publisher.buildManifest(chain);
        String manifestHeadHex = manifest.dictionary().get("head").string();
        String chainHeadHex = Hex.encode(writer.chain().headHash());
        assertEquals(chainHeadHex, manifestHeadHex);
    }

    @Test
    void publishedManifestLenMatchesChainSize() {
        blockSource.withTip(288L).withBlock(288L, hashForHeight(288L));
        writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);

        List<KarmaChainEntry> chain = writer.chain().entries();
        Entry manifest = publisher.buildManifest(chain);
        assertEquals((long) chain.size(), manifest.dictionary().get("len").integer());
    }

    // --- helpers ---

    private static byte[] hashForHeight(long height) {
        byte[] hash = new byte[32];
        for (int i = 0; i < 8; i++) {
            hash[i] = (byte) (height >>> (8 * (7 - i)));
        }
        return hash;
    }

    private static final class FakeBlockSource implements BlockHeaderSource {
        private final AtomicLong tip = new AtomicLong(-1);
        private final Map<Long, byte[]> blocks = new HashMap<>();

        FakeBlockSource withTip(long height) {
            tip.set(height);
            return this;
        }

        FakeBlockSource withBlock(long height, byte[] hash) {
            blocks.put(height, hash);
            return this;
        }

        @Override
        public BitcoinBlockReference getBlock(long height) {
            byte[] hash = blocks.get(height);
            if (hash == null) {
                return null;
            }
            return new BitcoinBlockReference(height, hash);
        }

        @Override
        public long getChainTipHeight() {
            return tip.get();
        }
    }
}
