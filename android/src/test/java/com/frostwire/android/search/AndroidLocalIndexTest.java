/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import android.content.Context;

import com.frostwire.search.relay.LocalSharedTorrent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AndroidLocalIndexTest {

    private AndroidLocalIndex index;

    @Before
    public void setUp() {
        Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        index = AndroidLocalIndex.open(ctx, "test-shared-torrents.db");
    }

    @After
    public void tearDown() {
        if (index != null) {
            index.close();
        }
    }

    @Test
    public void upsert_and_get_roundTrip() {
        LocalSharedTorrent t = makeTorrent("abc123", "Ubuntu ISO", 1, 1);
        index.upsert(t);

        Optional<LocalSharedTorrent> fetched = index.get(t.infoHashHex());
        assertTrue(fetched.isPresent());
        assertEquals(t.name(), fetched.get().name());
        assertEquals(t.sizeBytes(), fetched.get().sizeBytes());
        assertEquals(t.infoHashHex(), fetched.get().infoHashHex());
    }

    @Test
    public void upsert_replacesExisting() {
        LocalSharedTorrent t1 = makeTorrent("replace123", "Original Name", 100, 1);
        index.upsert(t1);

        LocalSharedTorrent t2 = makeTorrent("replace123", "Updated Name", 200, 2);
        index.upsert(t2);

        Optional<LocalSharedTorrent> fetched = index.get(t2.infoHashHex());
        assertTrue(fetched.isPresent());
        assertEquals("Updated Name", fetched.get().name());
        assertEquals(200, fetched.get().sizeBytes());
        assertEquals(2, fetched.get().fileCount());
    }

    @Test
    public void delete_removesTorrent() {
        LocalSharedTorrent t = makeTorrent("del123", "Deletable", 50, 1);
        index.upsert(t);
        assertEquals(1, index.size());

        index.delete(t.infoHashHex());
        assertEquals(0, index.size());
        assertFalse(index.get(t.infoHashHex()).isPresent());
    }

    @Test
    public void markPublished_and_needsRepublish() {
        LocalSharedTorrent t = makeTorrent("pub001", "Publishable", 100, 1);
        index.upsert(t);

        List<String> needs = index.needsRepublish(System.currentTimeMillis() / 1000, 3600);
        assertTrue(needs.contains(t.infoHashHex()));

        index.markPublished(t.infoHashHex(), System.currentTimeMillis() / 1000);

        List<String> needsAfter = index.needsRepublish(System.currentTimeMillis() / 1000, 3600);
        assertFalse(needsAfter.contains(t.infoHashHex()));
    }

    @Test
    public void updateLastSeen() {
        LocalSharedTorrent t = makeTorrent("seen001", "Seen Test", 100, 1);
        index.upsert(t);

        long newTs = System.currentTimeMillis() / 1000;
        index.updateLastSeen(t.infoHashHex(), newTs);

        Optional<LocalSharedTorrent> fetched = index.get(t.infoHashHex());
        assertTrue(fetched.isPresent());
        assertEquals(newTs, fetched.get().lastSeenAt());
    }

    @Test
    public void size_returnsCorrectCount() {
        assertEquals(0, index.size());
        index.upsert(makeTorrent("sz001", "Size 1", 100, 1));
        assertEquals(1, index.size());
        index.upsert(makeTorrent("sz002", "Size 2", 200, 1));
        assertEquals(2, index.size());
    }

    @Test
    public void listAll_returnsAllOrderedByLastSeenDesc() {
        index.upsert(makeTorrent("a100", "First", 100, 1, 1000, 1000));
        index.upsert(makeTorrent("b200", "Second", 200, 1, 2000, 2000));
        index.upsert(makeTorrent("c300", "Third", 300, 1, 3000, 3000));

        List<LocalSharedTorrent> all = index.listAll();
        assertEquals(3, all.size());
        assertEquals("Third", all.get(0).name());
        assertEquals("Second", all.get(1).name());
        assertEquals("First", all.get(2).name());
    }

    @Test
    public void get_nonExistent_returnsEmpty() {
        assertFalse(index.get("nonexistent").isPresent());
    }

    @Test
    public void upsert_preservesAllFields() {
        byte[] infoHash = new byte[20];
        byte[] nodeId = new byte[20];
        byte[] pubKey = new byte[32];
        Arrays.fill(infoHash, (byte) 0xAB);
        Arrays.fill(nodeId, (byte) 0xCD);
        Arrays.fill(pubKey, (byte) 0xEF);

        LocalSharedTorrent t = new LocalSharedTorrent.Builder()
                .infoHash(infoHash)
                .name("Full Field Test")
                .sizeBytes(9999)
                .fileCount(42)
                .filesJson("[{\"path\":\"a.txt\",\"size\":100}]")
                .tags("music;video")
                .publisherNodeId(nodeId)
                .publisherEd25519Pub(pubKey)
                .publisherUtpPort(6881)
                .addedAt(1000)
                .lastSeenAt(2000)
                .build();

        index.upsert(t);
        Optional<LocalSharedTorrent> fetched = index.get(t.infoHashHex());
        assertTrue(fetched.isPresent());
        LocalSharedTorrent r = fetched.get();
        assertEquals("Full Field Test", r.name());
        assertEquals(9999, r.sizeBytes());
        assertEquals(42, r.fileCount());
        assertEquals("[{\"path\":\"a.txt\",\"size\":100}]", r.filesJson());
        assertEquals("music;video", r.tags());
        assertEquals(6881, r.publisherUtpPort());
        assertEquals(1000, r.addedAt());
        assertEquals(2000, r.lastSeenAt());
        assertNotNull(r.publisherEd25519Pub());
        assertEquals(32, r.publisherEd25519Pub().length);
    }

    // Search tests use LIKE fallback in Robolectric (no FTS5 module).
    // On real Android devices (API 26+) FTS5 is used with BM25 ranking.
    // The LIKE fallback matches substrings case-insensitively.

    @Test
    public void search_byTorrentName_returnsMatch() {
        index.upsert(makeTorrent("srch001", "Ubuntu Studio ISO", 4000, 1));
        index.upsert(makeTorrent("srch002", "Debian Netinst", 500, 1));

        List<LocalSharedTorrent> results = index.search("ubuntu", 10);
        assertEquals(1, results.size());
        assertEquals("Ubuntu Studio ISO", results.get(0).name());
    }

    @Test
    public void search_byFilePath_returnsMatchedFile() {
        index.upsert(makeTorrentWithFiles("file001", "Linux Distro Pack", 1000,
                "[{\"path\":\"ubuntu-24.04.iso\",\"size\":1000}]"));

        List<LocalSharedTorrent> results = index.search("ubuntu", 10);
        assertEquals(1, results.size());
        assertNotNull(results.get(0).matchedFile());
        assertTrue(results.get(0).matchedFile().contains("ubuntu"));
    }

    @Test
    public void search_emptyQuery_returnsEmpty() {
        index.upsert(makeTorrent("empty001", "Some Torrent", 100, 1));
        List<LocalSharedTorrent> results = index.search("", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    public void search_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            index.upsert(makeTorrent("u" + i + "00", "Ubuntu Variant " + i, 100 + i, 1));
        }
        List<LocalSharedTorrent> results = index.search("ubuntu", 2);
        assertEquals(2, results.size());
    }

    @Test
    public void upsert_syncsSharedFiles_forReSearch() {
        index.upsert(makeTorrentWithFiles("sync001", "Sync Test", 500,
                "[{\"path\":\"readme.txt\",\"size\":10},{\"path\":\"manual.pdf\",\"size\":490}]"));

        List<LocalSharedTorrent> results = index.search("readme", 10);
        assertEquals(1, results.size());
        assertEquals("Sync Test", results.get(0).name());
        assertNotNull(results.get(0).matchedFile());
    }

    // Security tests — LIKE wildcard injection prevention

    @Test
    public void search_percentWildcard_doesNotMatchAll() {
        index.upsert(makeTorrent("pct001", "Alpha Torrent", 100, 1));
        index.upsert(makeTorrent("pct002", "Beta Torrent", 200, 1));
        index.upsert(makeTorrent("pct003", "Gamma Torrent", 300, 1));

        List<LocalSharedTorrent> results = index.search("%", 100);
        assertEquals("Percent wildcard must not match all torrents", 0, results.size());
    }

    @Test
    public void search_underscoreWildcard_doesNotMatchAll() {
        index.upsert(makeTorrent("und001", "Alpha Torrent", 100, 1));
        index.upsert(makeTorrent("und002", "Beta Torrent", 200, 1));

        List<LocalSharedTorrent> results = index.search("_", 100);
        assertEquals("Underscore wildcard must not match all torrents", 0, results.size());
    }

    @Test
    public void search_combinedWildcards_doesNotMatchAll() {
        index.upsert(makeTorrent("cw001", "Alpha Torrent", 100, 1));
        index.upsert(makeTorrent("cw002", "Beta Torrent", 200, 1));

        List<LocalSharedTorrent> results = index.search("%_%", 100);
        assertEquals(0, results.size());
    }

    @Test
    public void search_longQuery_isTruncated() {
        index.upsert(makeTorrent("lq001", "Alpha", 100, 1));
        String longQuery = "a".repeat(1000);
        List<LocalSharedTorrent> results = index.search(longQuery, 10);
        assertTrue(results.isEmpty());
    }

    private static LocalSharedTorrent makeTorrent(String hashSuffix, String name, long size, int fileCount) {
        return makeTorrent(hashSuffix, name, size, fileCount, System.currentTimeMillis() / 1000);
    }

    private static LocalSharedTorrent makeTorrent(String hashSuffix, String name, long size, int fileCount, long addedAt) {
        return makeTorrent(hashSuffix, name, size, fileCount, addedAt, addedAt);
    }

    private static LocalSharedTorrent makeTorrent(String hashSuffix, String name, long size, int fileCount, long addedAt, long lastSeenAt) {
        byte[] infoHash = new byte[20];
        byte[] nodeId = new byte[20];
        byte[] pubKey = new byte[32];
        Arrays.fill(nodeId, (byte) 'n');
        Arrays.fill(pubKey, (byte) 'p');
        for (int i = 0; i < infoHash.length && i < hashSuffix.length(); i++) {
            infoHash[i] = (byte) hashSuffix.charAt(i);
        }
        for (int i = hashSuffix.length(); i < infoHash.length; i++) {
            infoHash[i] = (byte) ('0' + (i % 10));
        }

        return new LocalSharedTorrent.Builder()
                .infoHash(infoHash)
                .name(name)
                .sizeBytes(size)
                .fileCount(fileCount)
                .filesJson("[]")
                .tags("")
                .publisherNodeId(nodeId)
                .publisherEd25519Pub(pubKey)
                .publisherUtpPort(6881)
                .addedAt(addedAt)
                .lastSeenAt(lastSeenAt)
                .build();
    }

    private static LocalSharedTorrent makeTorrentWithFiles(String hashSuffix, String name, long size, String filesJson) {
        LocalSharedTorrent t = makeTorrent(hashSuffix, name, size, 2);
        return t.toBuilder().filesJson(filesJson).build();
    }
}
