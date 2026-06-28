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
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalIndexTableTest {

    private File tempDir;
    private File dbFile;
    private LocalIndexTable table;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("local-index-test-").toFile();
        dbFile = new File(tempDir, "test.db");
        table = LocalIndexTable.open(dbFile);
    }

    @AfterEach
    void tearDown() {
        if (table != null && table.isOpen()) {
            table.close();
        }
        deleteRecursive(tempDir);
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private LocalSharedTorrent sampleTorrent(String name, long now) {
        return new LocalSharedTorrent.Builder()
                .infoHash(randomBytes(20))
                .name(name)
                .sizeBytes(1024L * 1024L)
                .fileCount(3)
                .filesJson("[{\"path\":\"a.iso\",\"size\":1048576}]")
                .publisherNodeId(randomBytes(20))
                .publisherEd25519Pub(randomBytes(32))
                .publisherUtpPort(49152)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    private LocalSharedTorrent sampleTorrentWithTags(String name, String tags, long now) {
        return new LocalSharedTorrent.Builder()
                .infoHash(randomBytes(20))
                .name(name)
                .sizeBytes(1024L * 1024L)
                .fileCount(3)
                .filesJson("[{\"path\":\"a.iso\",\"size\":1048576}]")
                .tags(tags)
                .publisherNodeId(randomBytes(20))
                .publisherEd25519Pub(randomBytes(32))
                .publisherUtpPort(49152)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    private LocalSharedTorrent torrentWithFiles(String name, String filesJson, long now) {
        return new LocalSharedTorrent.Builder()
                .infoHash(randomBytes(20))
                .name(name)
                .sizeBytes(1024L * 1024L)
                .fileCount(3)
                .filesJson(filesJson)
                .publisherNodeId(randomBytes(20))
                .publisherEd25519Pub(randomBytes(32))
                .publisherUtpPort(49152)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static String hexOf(byte[] b) {
        return Hex.encode(b);
    }

    @Test
    void openCreatesSchemaAndIsOpen() {
        assertTrue(table.isOpen());
        assertEquals(0, table.size());
        assertTrue(dbFile.exists());
    }

    @Test
    void schemaHasRequiredIndexes() {
        // DESIGN_RELAY_REGISTRY.md §4.2 explicitly specifies the first two.
        // The third (last_published_at) is added so §4.3's needsRepublish
        // query stays O(log n) once the table grows past a few hundred rows.
        java.util.Set<String> expected = new java.util.HashSet<>();
        expected.add("idx_shared_torrents_added");
        expected.add("idx_shared_torrents_name");
        expected.add("idx_shared_torrents_last_published_at");
        java.util.Set<String> actual = new java.util.HashSet<>();
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             java.sql.Statement s = c.createStatement();
             java.sql.ResultSet rs = s.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shared_torrents'")) {
            while (rs.next()) {
                actual.add(rs.getString(1));
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        assertTrue(actual.containsAll(expected),
                "Missing required indexes. Expected " + expected + " but table has " + actual);
    }

    @Test
    void upsertInsertsNewRow() {
        LocalSharedTorrent t = sampleTorrentWithTags("Ubuntu 24.04 LTS Desktop", "linux,iso,ubuntu", 1_000_000L);
        table.upsert(t);
        assertEquals(1, table.size());
        Optional<LocalSharedTorrent> got = table.get(hexOf(t.infoHash()));
        assertTrue(got.isPresent());
        assertEquals("Ubuntu 24.04 LTS Desktop", got.get().name());
        assertEquals(3, got.get().fileCount());
        assertEquals("linux,iso,ubuntu", got.get().tags());
    }

    @Test
    void upsertReplacesOnDuplicateInfoHash() {
        LocalSharedTorrent t = sampleTorrent("First name", 1_000_000L);
        table.upsert(t);
        assertEquals(1, table.size());

        LocalSharedTorrent updated = t.toBuilder()
                .name("Updated name")
                .lastSeenAt(1_000_500L)
                .build();
        table.upsert(updated);

        assertEquals(1, table.size(), "Same info_hash should replace, not insert");
        Optional<LocalSharedTorrent> got = table.get(hexOf(t.infoHash()));
        assertTrue(got.isPresent());
        assertEquals("Updated name", got.get().name());
        assertEquals(1_000_500L, got.get().lastSeenAt());
    }

    @Test
    void searchFindsMatchingTorrents() {
        long now = 1_000_000L;
        table.upsert(sampleTorrentWithTags("Ubuntu 24.04 LTS Desktop", "linux iso", now));
        table.upsert(sampleTorrentWithTags("Debian 12 netinst", "linux debian", now));
        table.upsert(sampleTorrentWithTags("Windows 11 Pro", "windows os", now));

        List<LocalSharedTorrent> ubuntu = table.search("ubuntu", 10);
        assertEquals(1, ubuntu.size());
        assertTrue(ubuntu.get(0).name().contains("Ubuntu"));
    }

    @Test
    void searchIsCaseInsensitive() {
        long now = 1_000_000L;
        table.upsert(sampleTorrent("Ubuntu 24.04 LTS Desktop", now));
        table.upsert(sampleTorrent("Windows 11 Pro", now));

        List<LocalSharedTorrent> ubuntuLower = table.search("ubuntu", 10);
        List<LocalSharedTorrent> ubuntuUpper = table.search("UBUNTU", 10);
        assertEquals(1, ubuntuLower.size());
        assertEquals(1, ubuntuUpper.size());
    }

    @Test
    void searchStripsFts5Operators() {
        long now = 1_000_000L;
        table.upsert(sampleTorrent("Ubuntu 24.04 LTS Desktop", now));
        table.upsert(sampleTorrent("Windows 11 Pro", now));

        // 'OR' is an FTS5 operator. Sanitization must quote it as a literal
        // token, so the query becomes '"ubuntu" "or" "windows"' which FTS5
        // treats as implicit-AND. No document contains the word "or", so
        // the result set is empty. The crucial property is that this does
        // NOT raise a syntax error and does NOT silently fall back to OR.
        assertEquals(0, table.search("ubuntu OR windows", 10).size());
        // Without the "or" token, the implicit-AND of "ubuntu" and "windows"
        // also yields 0 (the Ubuntu doc has "ubuntu" but not "windows", and
        // vice versa). Use a single word to confirm a positive result.
        assertEquals(1, table.search("ubuntu", 10).size());
        assertEquals(1, table.search("windows", 10).size());
    }

    @Test
    void searchWithNoUsableTokensReturnsEmpty() {
        long now = 1_000_000L;
        table.upsert(sampleTorrent("Ubuntu 24.04", now));

        // All chars are stripped by sanitization
        assertTrue(table.search("!@#$%^&*()", 10).isEmpty());
        assertTrue(table.search("", 10).isEmpty());
        assertTrue(table.search(null, 10).isEmpty());
    }

    @Test
    void searchLimitRespected() {
        long now = 1_000_000L;
        for (int i = 0; i < 5; i++) {
            table.upsert(sampleTorrent("Ubuntu " + i, now));
        }
        List<LocalSharedTorrent> r = table.search("ubuntu", 2);
        assertEquals(2, r.size());
    }

    @Test
    void deleteRemovesRow() {
        LocalSharedTorrent t = sampleTorrent("Ubuntu 24.04", 1_000_000L);
        table.upsert(t);
        assertEquals(1, table.size());

        table.delete(hexOf(t.infoHash()));
        assertEquals(0, table.size());
        assertTrue(table.get(hexOf(t.infoHash())).isEmpty());
    }

    @Test
    void markPublishedSetsTimestamp() {
        LocalSharedTorrent t = sampleTorrent("Ubuntu 24.04", 1_000_000L);
        table.upsert(t);
        assertNull(t.lastPublishedAt());
        assertNull(table.get(hexOf(t.infoHash())).get().lastPublishedAt());

        table.markPublished(hexOf(t.infoHash()), 1_000_500L);
        assertEquals(1_000_500L,
                table.get(hexOf(t.infoHash())).get().lastPublishedAt());
    }

    @Test
    void needsRepublishReturnsRowsOlderThanThreshold() {
        long now = 1_000_000L;
        LocalSharedTorrent t1 = sampleTorrent("Ubuntu 1", now);
        LocalSharedTorrent t2 = sampleTorrent("Ubuntu 2", now);
        LocalSharedTorrent t3 = sampleTorrent("Ubuntu 3", now);
        table.upsert(t1);
        table.upsert(t2);
        table.upsert(t3);

        // Mark t2 as recently published (10 sec ago)
        table.markPublished(hexOf(t2.infoHash()), now - 10);
        // Mark t3 as published long ago (1 hour ago)
        table.markPublished(hexOf(t3.infoHash()), now - 3600);

        // Threshold 60 sec: t1 (never), t3 (3600 ago) qualify; t2 does not
        List<String> republish = table.needsRepublish(now, 60);
        assertEquals(2, republish.size());
        assertTrue(republish.contains(hexOf(t1.infoHash())));
        assertTrue(republish.contains(hexOf(t3.infoHash())));
        assertFalse(republish.contains(hexOf(t2.infoHash())));
    }

    @Test
    void updateLastSeenChangesTimestampOnly() {
        LocalSharedTorrent t = sampleTorrent("Ubuntu 24.04", 1_000_000L);
        table.upsert(t);

        table.updateLastSeen(hexOf(t.infoHash()), 1_000_999L);
        LocalSharedTorrent got = table.get(hexOf(t.infoHash())).get();
        assertEquals(1_000_999L, got.lastSeenAt());
        assertEquals(1_000_000L, got.addedAt(), "addedAt should not change");
    }

    @Test
    void sizeInBytesIsNonNegative() {
        table.upsert(sampleTorrent("X", 1_000_000L));
        assertTrue(table.sizeInBytes() > 0);
    }

    @Test
    void operationsAfterCloseThrow() {
        table.close();
        assertFalse(table.isOpen());
        assertThrows(IllegalStateException.class, () -> table.size());
    }

    @Test
    void tryWithResourcesClosesTable() throws Exception {
        File f = new File(tempDir, "try-with-resources.db");
        try (LocalIndexTable t = LocalIndexTable.open(f)) {
            assertTrue(t.isOpen());
            t.upsert(sampleTorrent("inside-try", 1_000_000L));
        }
        // After try-with-resources, the table is closed.
        LocalIndexTable reopened = LocalIndexTable.open(f);
        try {
            assertEquals(1, reopened.size());
        } finally {
            reopened.close();
        }
    }

    @Test
    void reopenExistingFilePreservesData() {
        LocalSharedTorrent t = sampleTorrent("Persistent", 1_000_000L);
        table.upsert(t);
        table.close();
        assertFalse(table.isOpen());

        LocalIndexTable reopened = LocalIndexTable.open(dbFile);
        assertTrue(reopened.isOpen());
        assertEquals(1, reopened.size());
        assertTrue(reopened.get(hexOf(t.infoHash())).isPresent());
        reopened.close();
    }

    @Test
    void getMissingReturnsEmpty() {
        Optional<LocalSharedTorrent> got = table.get(hexOf(randomBytes(20)));
        assertTrue(got.isEmpty());
    }

    @Test
    void searchDropsInjectionAttempt() {
        long now = 1_000_000L;
        table.upsert(sampleTorrent("Ubuntu 24.04", now));
        // FTS5 syntax injection attempt: query contains " and = and OR, all of
        // which are stripped. The sanitized form is the implicit-AND of
        // "ubuntu", "or", "11". No document contains the literal word "or",
        // so the result is empty. Crucially, the query does NOT raise an
        // exception and does NOT silently fall back to "OR" semantics
        // (which would have returned 1 result).
        assertEquals(0, table.search("ubuntu\" OR \"1\"=\"1", 10).size());
        // And the plain "ubuntu" search still works:
        assertEquals(1, table.search("ubuntu", 10).size());
    }

    @Test
    void hexEncodeDecodeRoundTrips() {
        byte[] original = randomBytes(20);
        byte[] back = Hex.decode(Hex.encode(original));
        assertArrayEquals(original, back);
    }

    @Test
    void shortNodeIdReturnsFirst8Hex() {
        byte[] id = new byte[20];
        for (int i = 0; i < 20; i++) id[i] = (byte) i;
        String shortId = LocalSharedTorrent.shortNodeId(id);
        assertEquals(8, shortId.length());
        assertEquals("00010203", shortId);
    }

    @Test
    void manyInsertsAndSearchStaysConsistent() {
        long now = 1_000_000L;
        int n = 100;
        for (int i = 0; i < n; i++) {
            table.upsert(sampleTorrent("filler " + i, now));
        }
        assertEquals(n, table.size());
        // Insert one with a unique keyword, search for it
        table.upsert(sampleTorrent("uniqueKeywordMarkerIso torrent", now));
        assertEquals(1, table.search("uniqueKeywordMarkerIso", 1000).size());
    }

    // ===== File-level search tests =====

    @Test
    void searchByFilePathFindsTorrentAndPopulatesMatchedFile() {
        long now = 1_000_000L;
        // Torrent name has nothing to do with "readme"; only a file inside does.
        table.upsert(torrentWithFiles(
                "Some Random Project",
                "[{\"path\":\"docs/readme.txt\",\"size\":1024},{\"path\":\"src/main.java\",\"size\":2048}]",
                now));

        List<LocalSharedTorrent> results = table.search("readme", 10);
        assertEquals(1, results.size());
        assertEquals("Some Random Project", results.get(0).name());
        assertNotNull(results.get(0).matchedFile(), "matchedFile should be populated for file-path matches");
        assertTrue(results.get(0).matchedFile().contains("readme.txt"));
    }

    @Test
    void searchByTorrentNameLeavesMatchedFileNull() {
        long now = 1_000_000L;
        table.upsert(torrentWithFiles(
                "Ubuntu 24.04 Desktop",
                "[{\"path\":\"ubuntu-24.04.iso\",\"size\":4000000000}]",
                now));

        List<LocalSharedTorrent> results = table.search("ubuntu", 10);
        assertEquals(1, results.size());
        assertNull(results.get(0).matchedFile(),
                "matchedFile should be null when the match is on the torrent name, not a file path");
    }

    @Test
    void torrentNameMatchTakesPriorityOverFileMatch() {
        long now = 1_000_000L;
        // This torrent matches "keyword" in both name and file path.
        table.upsert(torrentWithFiles(
                "Keyword In Name",
                "[{\"path\":\"keyword_file.txt\",\"size\":100}]",
                now));
        // This torrent only matches "keyword" in a file path.
        table.upsert(torrentWithFiles(
                "Different Name",
                "[{\"path\":\"keyword_other.txt\",\"size\":100}]",
                now));

        List<LocalSharedTorrent> results = table.search("keyword", 10);
        assertEquals(2, results.size());
        // Torrent-name match should come first (Phase 1 runs before Phase 2).
        assertEquals("Keyword In Name", results.get(0).name());
        assertNull(results.get(0).matchedFile(), "Phase 1 match should have null matchedFile");
        // File-path match should come second.
        assertEquals("Different Name", results.get(1).name());
        assertNotNull(results.get(1).matchedFile(), "Phase 2 match should have populated matchedFile");
    }

    @Test
    void searchDoesNotDuplicateWhenBothNameAndFileMatch() {
        long now = 1_000_000L;
        // Torrent matches "ubuntu" in both name and a file path.
        table.upsert(torrentWithFiles(
                "Ubuntu LTS",
                "[{\"path\":\"ubuntu-desktop.iso\",\"size\":1000000000}]",
                now));

        List<LocalSharedTorrent> results = table.search("ubuntu", 10);
        assertEquals(1, results.size(), "Should not duplicate even if both name and file match");
        // Phase 1 (torrent-name) wins, so matchedFile is null.
        assertNull(results.get(0).matchedFile());
    }

    @Test
    void upsertReplacesSharedFilesOnUpdate() {
        long now = 1_000_000L;
        LocalSharedTorrent t = torrentWithFiles(
                "MyTorrent",
                "[{\"path\":\"old_file.txt\",\"size\":100}]",
                now);
        table.upsert(t);

        // Verify the old file is searchable.
        assertEquals(1, table.search("old_file", 10).size());

        // Update with new file paths.
        LocalSharedTorrent updated = t.toBuilder()
                .filesJson("[{\"path\":\"new_file.txt\",\"size\":200}]")
                .lastSeenAt(now + 100)
                .build();
        table.upsert(updated);

        // Old file should no longer be in shared_files.
        assertEquals(0, table.search("old_file", 10).size(),
                "Old file path should be removed after upsert with new files_json");
        // New file should be searchable.
        List<LocalSharedTorrent> results = table.search("new_file", 10);
        assertEquals(1, results.size());
        assertNotNull(results.get(0).matchedFile());
        assertTrue(results.get(0).matchedFile().contains("new_file.txt"));
    }

    @Test
    void deleteCascadesToSharedFiles() {
        long now = 1_000_000L;
        LocalSharedTorrent t = torrentWithFiles(
                "ToDelete",
                "[{\"path\":\"unique_cascade_file.txt\",\"size\":100}]",
                now);
        table.upsert(t);
        assertEquals(1, table.search("unique_cascade_file", 10).size());

        table.delete(hexOf(t.infoHash()));
        assertEquals(0, table.search("unique_cascade_file", 10).size(),
                "File-path FTS entries should be cascade-deleted with the torrent");
    }

    @Test
    void searchMultipleFileMatchesReturnsDistinctTorrents() {
        long now = 1_000_000L;
        table.upsert(torrentWithFiles(
                "Torrent A",
                "[{\"path\":\"shared/readme.md\",\"size\":100}]",
                now));
        table.upsert(torrentWithFiles(
                "Torrent B",
                "[{\"path\":\"docs/readme.md\",\"size\":200}]",
                now));
        table.upsert(torrentWithFiles(
                "Torrent C",
                "[{\"path\":\"other/guide.txt\",\"size\":300}]",
                now));

        List<LocalSharedTorrent> results = table.search("readme", 10);
        assertEquals(2, results.size(), "Only torrents with 'readme' in a file path should match");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (LocalSharedTorrent r : results) {
            names.add(r.name());
            assertNotNull(r.matchedFile());
        }
        assertTrue(names.contains("Torrent A"));
        assertTrue(names.contains("Torrent B"));
        assertFalse(names.contains("Torrent C"));
    }

    @Test
    void searchRespectsLimitAcrossBothPhases() {
        long now = 1_000_000L;
        // Insert 3 name-matching torrents and 3 file-matching torrents.
        for (int i = 0; i < 3; i++) {
            table.upsert(torrentWithFiles(
                    "CommonKeyword Name " + i,
                    "[{\"path\":\"file" + i + ".txt\",\"size\":100}]",
                    now));
        }
        for (int i = 0; i < 3; i++) {
            table.upsert(torrentWithFiles(
                    "Unrelated " + i,
                    "[{\"path\":\"commonkeyword_file.txt\",\"size\":100}]",
                    now));
        }

        // Limit 2: should return 2 name matches (Phase 1 fills the quota).
        List<LocalSharedTorrent> results = table.search("commonkeyword", 2);
        assertEquals(2, results.size());
        for (LocalSharedTorrent r : results) {
            assertTrue(r.name().startsWith("CommonKeyword Name"));
            assertNull(r.matchedFile());
        }
    }

    @Test
    void searchFileMatchFillsRemainingQuotaAfterNameMatches() {
        long now = 1_000_000L;
        // 1 name match
        table.upsert(torrentWithFiles(
                "AlphaRelease torrent",
                "[{\"path\":\"data.bin\",\"size\":100}]",
                now));
        // 2 file-only matches
        table.upsert(torrentWithFiles(
                "Unrelated 1",
                "[{\"path\":\"alpharelease_notes.txt\",\"size\":100}]",
                now));
        table.upsert(torrentWithFiles(
                "Unrelated 2",
                "[{\"path\":\"alpharelease_changelog.txt\",\"size\":100}]",
                now));

        List<LocalSharedTorrent> results = table.search("alpharelease", 10);
        assertEquals(3, results.size());
        // First result is the name match (matchedFile null).
        assertNull(results.get(0).matchedFile());
        // Remaining results are file matches (matchedFile populated).
        assertNotNull(results.get(1).matchedFile());
        assertNotNull(results.get(2).matchedFile());
    }

    @Test
    void emptyFilesJsonDoesNotBreakSearch() {
        long now = 1_000_000L;
        table.upsert(torrentWithFiles("ValidName", "[]", now));

        List<LocalSharedTorrent> nameResults = table.search("validname", 10);
        assertEquals(1, nameResults.size());
        assertNull(nameResults.get(0).matchedFile());
    }

    @Test
    void filesJsonWithMissingPathIsSkipped() {
        long now = 1_000_000L;
        // Entry without "path" should be silently skipped.
        table.upsert(torrentWithFiles(
                "TestTorrent",
                "[{\"size\":100},{\"path\":\"real_file.txt\",\"size\":200}]",
                now));

        List<LocalSharedTorrent> results = table.search("real_file", 10);
        assertEquals(1, results.size());
        assertNotNull(results.get(0).matchedFile());
    }

    @Test
    void sharedFilesSurviveReopen() {
        long now = 1_000_000L;
        LocalSharedTorrent t = torrentWithFiles(
                "PersistentFiles",
                "[{\"path\":\"persistent_readme.txt\",\"size\":100}]",
                now);
        table.upsert(t);
        table.close();

        LocalIndexTable reopened = LocalIndexTable.open(dbFile);
        try {
            List<LocalSharedTorrent> results = reopened.search("persistent_readme", 10);
            assertEquals(1, results.size());
            assertNotNull(results.get(0).matchedFile());
            assertTrue(results.get(0).matchedFile().contains("persistent_readme.txt"));
        } finally {
            reopened.close();
        }
    }

    @Test
    void upsertReplaceDoesNotOrphanSharedFilesRows() throws Exception {
        // Regression test: INSERT OR REPLACE assigns a new rowid on conflict.
        // Old shared_files rows referencing the old rowid must be cleaned up,
        // not orphaned. Verify by counting rows in shared_files directly.
        long now = 1_000_000L;
        LocalSharedTorrent t1 = torrentWithFiles(
                "MyTorrent",
                "[{\"path\":\"file_a.txt\",\"size\":100},{\"path\":\"file_b.txt\",\"size\":200}]",
                now);
        table.upsert(t1);

        // Upsert again with different files (same info_hash → REPLACE).
        LocalSharedTorrent t2 = t1.toBuilder()
                .filesJson("[{\"path\":\"file_c.txt\",\"size\":300}]")
                .lastSeenAt(now + 100)
                .build();
        table.upsert(t2);

        // Count shared_files rows for this torrent — should be 1, not 3.
        int fileCount = countSharedFilesRows();
        assertEquals(1, fileCount,
                "INSERT OR REPLACE must not orphan old shared_files rows; expected 1, got " + fileCount);

        // Old files should not be searchable.
        assertEquals(0, table.search("file_a", 10).size());
        assertEquals(0, table.search("file_b", 10).size());
        // New file should be searchable.
        assertEquals(1, table.search("file_c", 10).size());
    }

    @Test
    void repeatedUpsertsDoNotAccumulateSharedFilesRows() throws Exception {
        // Stress the orphan bug: many upserts of the same torrent with
        // different file lists. If orphans accumulate, the count grows
        // unboundedly.
        long now = 1_000_000L;
        LocalSharedTorrent t = torrentWithFiles("StressTest", "[{\"path\":\"v1.txt\",\"size\":1}]", now);
        byte[] ih = t.infoHash();

        for (int i = 0; i < 20; i++) {
            t = t.toBuilder()
                    .filesJson("[{\"path\":\"v" + i + ".txt\",\"size\":1}]")
                    .lastSeenAt(now + i)
                    .build();
            table.upsert(t);
        }

        int fileCount = countSharedFilesRows();
        assertEquals(1, fileCount,
                "20 upserts should leave exactly 1 shared_files row, not " + fileCount);
    }

    @Test
    void parseFilesJsonCapsAtMaxFilesPerTorrent() throws Exception {
        // Build a files_json with more than MAX_FILES_PER_TORRENT entries.
        int excess = LocalIndexTable.MAX_FILES_PER_TORRENT + 500;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < excess; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"path\":\"file").append(i).append(".txt\",\"size\":1}");
        }
        sb.append(']');
        long now = 1_000_000L;
        table.upsert(torrentWithFiles("HugeTorrent", sb.toString(), now));

        int fileCount = countSharedFilesRows();
        assertEquals(LocalIndexTable.MAX_FILES_PER_TORRENT, fileCount,
                "shared_files should be capped at MAX_FILES_PER_TORRENT");
    }

    @Test
    void migrationBackfillsSharedFilesFromFilesJson() throws Exception {
        File v2Db = new File(tempDir, "v2-migrate-test.db");
        deleteSqliteDatabase(v2Db);
        seedV2DatabaseWithTorrent(v2Db,
                "legacy-torrent",
                "[{\"path\":\"v2_unique_readme.txt\",\"size\":42}]");
        assertEquals("2", readSchemaVersion(v2Db), "precondition: seeded database must be schema v2");
        assertEquals(0, countSharedFilesRows(v2Db), "precondition: v2 db has no shared_files rows");

        LocalIndexTable reopened = LocalIndexTable.open(v2Db);
        try {
            assertEquals(String.valueOf(LocalIndexTable.SCHEMA_VERSION), readSchemaVersion(v2Db),
                    "schema must be bumped to v" + LocalIndexTable.SCHEMA_VERSION);
            assertEquals(1, countSharedFilesRows(v2Db),
                    "migration must backfill shared_files from files_json");

            List<LocalSharedTorrent> results = reopened.search("v2_unique_readme", 10);
            assertEquals(1, results.size(), "file-path search must work after v2->v3 migration");
            assertNotNull(results.get(0).matchedFile());
            assertTrue(results.get(0).matchedFile().contains("v2_unique_readme.txt"));
        } finally {
            reopened.close();
        }
    }

    /**
     * Simulates a v2 database: shared_torrents populated, schema version 2,
     * no shared_files index (pre-v3 file FTS).
     */
    private void seedV2DatabaseWithTorrent(File db, String name, String filesJson) throws Exception {
        deleteSqliteDatabase(db);
        File parent = db.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent);
        }
        byte[] infoHash = randomBytes(20);
        byte[] nodeId = randomBytes(20);
        byte[] pub = randomBytes(32);
        long now = 1_000_000L;
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + db.getAbsolutePath())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode = DELETE");
                s.execute("CREATE TABLE schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                s.execute("INSERT INTO schema_meta(key, value) VALUES ('version', '2')");
                s.execute("CREATE TABLE shared_torrents ("
                        + "info_hash TEXT PRIMARY KEY, "
                        + "name TEXT NOT NULL, "
                        + "size_bytes INTEGER NOT NULL, "
                        + "file_count INTEGER NOT NULL, "
                        + "files_json TEXT NOT NULL, "
                        + "tags TEXT, "
                        + "publisher_node_id TEXT NOT NULL, "
                        + "publisher_ed25519_pub BLOB NOT NULL, "
                        + "publisher_utp_port INTEGER, "
                        + "added_at INTEGER NOT NULL, "
                        + "last_seen_at INTEGER NOT NULL, "
                        + "last_published_at INTEGER)");
                s.execute("CREATE VIRTUAL TABLE shared_torrents_fts USING fts5("
                        + "name, tags, content='shared_torrents', content_rowid='rowid', "
                        + "tokenize='porter unicode61')");
            }
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO shared_torrents (info_hash, name, size_bytes, file_count, "
                            + "files_json, tags, publisher_node_id, publisher_ed25519_pub, "
                            + "publisher_utp_port, added_at, last_seen_at, last_published_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, Hex.encode(infoHash));
                ps.setString(2, name);
                ps.setLong(3, 1024L);
                ps.setInt(4, 1);
                ps.setString(5, filesJson);
                ps.setString(6, null);
                ps.setString(7, Hex.encode(nodeId));
                ps.setBytes(8, pub);
                ps.setInt(9, 49152);
                ps.setLong(10, now);
                ps.setLong(11, now);
                ps.setNull(12, java.sql.Types.INTEGER);
                ps.executeUpdate();
            }
        }
    }

    private static void deleteSqliteDatabase(File db) {
        if (db == null) {
            return;
        }
        String path = db.getAbsolutePath();
        for (String suffix : new String[]{"", "-wal", "-shm", "-journal"}) {
            File f = new File(path + suffix);
            if (f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static String readSchemaVersion(File db) throws Exception {
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + db.getAbsolutePath());
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "SELECT value FROM schema_meta WHERE key = 'version'")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "schema_meta version row missing in " + db);
                return rs.getString(1);
            }
        }
    }

    @Test
    void schemaVersionIsBumpedToCurrent() throws Exception {
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + dbFile.getAbsolutePath());
             java.sql.Statement s = c.createStatement();
             java.sql.ResultSet rs = s.executeQuery(
                     "SELECT value FROM schema_meta WHERE key = 'version'")) {
            assertTrue(rs.next());
            assertEquals(String.valueOf(LocalIndexTable.SCHEMA_VERSION), rs.getString(1),
                    "SCHEMA_VERSION must be " + LocalIndexTable.SCHEMA_VERSION + " after init");
        }
    }

    private int countSharedFilesRows() throws Exception {
        return countSharedFilesRows(dbFile);
    }

    private static int countSharedFilesRows(File db) throws Exception {
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + db.getAbsolutePath());
             java.sql.Statement s = c.createStatement()) {
            try (java.sql.ResultSet tables = s.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='shared_files'")) {
                if (!tables.next() || tables.getInt(1) == 0) {
                    return 0;
                }
            }
            try (java.sql.ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM shared_files")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
