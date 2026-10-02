/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.create_flags_t;
import com.frostwire.jlibtorrent.swig.create_torrent;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.file_storage;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.set_piece_hashes_listener;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.jlibtorrent.swig.status_flags_t;
import com.frostwire.jlibtorrent.swig.torrent_handle;
import com.frostwire.jlibtorrent.swig.torrent_info;
import com.frostwire.jlibtorrent.swig.torrent_status;
import com.frostwire.util.Hex;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedTorrentIndexerIdentityTest {

  private static final String NAME = "El General - Te Ves Buena (audio).webm";
  private static final String UNRELATED = "00112233445566778899aabbccddeeff00112233";

  @TempDir Path tempDir;

  @Test
  void realHybridCreatedAndDownloadCallbackConvergeOnOneCanonicalRow() throws Exception {
    TorrentInfo ti = torrentInfo(new create_flags_t());
    assertTrue(ti.infoHashType().has_v1());
    assertTrue(ti.infoHashType().has_v2());
    String canonical = ti.infoHashV1().toHex();
    String legacy = ti.infoHashV2().toHex().substring(0, 40);
    assertNotEquals(canonical, legacy);
    NativeDownload fixture = new NativeDownload(ti, legacy, tempDir);
    // Exercise the existing public APIs used by desktop visibility/transfer lookup.
    assertEquals(legacy, fixture.download.getInfoHash());
    assertEquals(canonical, fixture.download.getV1InfoHash());
    try (LocalIndexTable index = LocalIndexTable.open(tempDir.resolve("index.db").toFile())) {
      List<List<String>> notifications = new CopyOnWriteArrayList<>();
      SharedTorrentIndexer producer =
          new SharedTorrentIndexer(
              index,
              null,
              () ->
                  notifications.add(
                      index.listAll().stream().map(LocalSharedTorrent::infoHashHex).toList()));
      assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(ti, NAME));
      producer.downloadAdded(fixture.engine, fixture.download);
      await(() -> notifications.size() == 2);
      assertEquals(1, index.size(), "created and callback paths must not create hybrid aliases");
      assertEquals(canonical, index.search("general", 10).get(0).infoHashHex());
      assertTrue(index.get(legacy).isEmpty());

      // Simulate a stale row written by the old callback producer, and an unrelated UI ID.
      index.upsert(row(legacy));
      index.upsert(row(UNRELATED));
      assertEquals(
          IndexResult.UPSERTED,
          producer.indexIfReady(fixture.download, UNRELATED, IndexTrigger.UPDATE));
      assertEquals(2, index.size());
      assertTrue(index.get(legacy).isEmpty(), "metadata proves the v2-prefix alias belongs to v1");
      assertTrue(index.get(UNRELATED).isPresent(), "the supplied legacy string is not alias proof");
      assertEquals(4, notifications.size(), "canonical commit and successful deletion both notify");
      assertTrue(
          notifications.get(2).contains(legacy), "canonical row commits before alias removal");
      assertFalse(notifications.get(3).contains(legacy));

      producer.indexExisting(List.of(fixture.download));
      await(() -> notifications.size() >= 6);
      assertEquals(1, index.size());
      assertTrue(index.get(canonical).isPresent(), "live reconciliation must retain canonical v1");
      assertEquals(
          fixture.download.getV1InfoHash(), index.search("general", 10).get(0).infoHashHex());
    }
  }

  @Test
  void createdHybridRemovesOnlyItsMetadataProvenAlias() throws Exception {
    TorrentInfo ti = torrentInfo(new create_flags_t());
    String canonical = ti.infoHashV1().toHex();
    String legacy = ti.infoHashV2().toHex().substring(0, 40);
    RecordingIndex index = new RecordingIndex();
    index.upsert(row(legacy));
    index.upsert(row(UNRELATED));
    SharedTorrentIndexer producer = new SharedTorrentIndexer(index);
    assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(ti, NAME));
    assertEquals(2, index.size());
    assertTrue(index.get(canonical).isPresent());
    assertTrue(index.get(legacy).isEmpty());
    assertTrue(index.get(UNRELATED).isPresent());
  }

  @Test
  void v1OnlyMetadataOverridesSyntheticUiIdWithoutAuthorizingDeletion() throws Exception {
    TorrentInfo ti = torrentInfo(create_torrent.v1_only);
    String canonical = ti.infoHashV1().toHex();
    assertFalse(ti.infoHashType().has_v2());
    NativeDownload fixture = new NativeDownload(ti, UNRELATED, tempDir);
    RecordingIndex index = new RecordingIndex();
    index.upsert(row(UNRELATED));
    SharedTorrentIndexer producer = new SharedTorrentIndexer(index);
    assertEquals(
        IndexResult.UPSERTED,
        producer.indexIfReady(fixture.download, UNRELATED, IndexTrigger.UPDATE));
    assertTrue(index.get(canonical).isPresent());
    assertTrue(index.get(UNRELATED).isPresent());
    assertEquals(0, index.deletes.get());
  }

  @Test
  void v2OnlyRetainsCurrentTwentyBytePrefixWithoutInventingV1OrDeletingUnrelatedId()
      throws Exception {
    TorrentInfo ti = torrentInfo(create_torrent.v2_only);
    assertFalse(ti.infoHashType().has_v1());
    assertTrue(ti.infoHashType().has_v2());
    String prefix = ti.infoHashV2().toHex().substring(0, 40);
    NativeDownload fixture = new NativeDownload(ti, prefix, tempDir);
    assertNull(fixture.download.getV1InfoHash());
    RecordingIndex index = new RecordingIndex();
    SharedTorrentIndexer producer = new SharedTorrentIndexer(index);
    index.upsert(row(UNRELATED));
    assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(ti, NAME));
    assertEquals(
        IndexResult.UPSERTED,
        producer.indexIfReady(fixture.download, UNRELATED, IndexTrigger.UPDATE));
    assertEquals(2, index.size());
    assertTrue(index.get(prefix).isPresent());
    assertTrue(index.get(UNRELATED).isPresent());
    assertEquals(0, index.deletes.get());
  }

  @Test
  void failedCanonicalWriteKeepsAliasAndFailedAliasCleanupKeepsCommittedCanonicalSearchable()
      throws Exception {
    TorrentInfo ti = torrentInfo(new create_flags_t());
    String canonical = ti.infoHashV1().toHex();
    String legacy = ti.infoHashV2().toHex().substring(0, 40);
    NativeDownload fixture = new NativeDownload(ti, legacy, tempDir);
    RecordingIndex index = new RecordingIndex();
    index.upsert(row(legacy));
    AtomicInteger notifications = new AtomicInteger();
    SharedTorrentIndexer producer =
        new SharedTorrentIndexer(index, null, notifications::incrementAndGet);
    index.failUpsert = true;
    assertEquals(
        IndexResult.ERROR, producer.indexIfReady(fixture.download, legacy, IndexTrigger.ADDED));
    assertTrue(index.get(legacy).isPresent());
    assertTrue(index.get(canonical).isEmpty());
    assertEquals(0, notifications.get());
    // Even reconciliation must not delete the legacy row ahead of a failed canonical write.
    producer.indexExisting(List.of(fixture.download));
    assertTrue(index.get(legacy).isPresent());
    // Drain that asynchronous attempt before changing failure mode.
    await(() -> index.upsertAttempts.get() == 3);
    index.failUpsert = false;
    index.failDelete = true;
    assertEquals(
        IndexResult.UPSERTED, producer.indexIfReady(fixture.download, legacy, IndexTrigger.UPDATE));
    assertTrue(index.get(legacy).isPresent());
    assertEquals(canonical, index.get(canonical).orElseThrow().infoHashHex());
    assertEquals(1, notifications.get());
    index.failDelete = false;
    assertEquals(
        IndexResult.UPSERTED, producer.indexIfReady(fixture.download, legacy, IndexTrigger.UPDATE));
    assertTrue(index.get(legacy).isEmpty());
    assertEquals(3, notifications.get());
  }

  @Test
  void metadataGapDoesNotPruneAlreadySearchableCanonicalRow() throws Exception {
    TorrentInfo ti = torrentInfo(new create_flags_t());
    String canonical = ti.infoHashV1().toHex();
    String legacy = ti.infoHashV2().toHex().substring(0, 40);
    NativeDownload fixture = new NativeDownload(ti, legacy, tempDir);
    RecordingIndex index = new RecordingIndex();
    SharedTorrentIndexer producer = new SharedTorrentIndexer(index);
    assertEquals(IndexResult.UPSERTED, producer.indexTorrentInfo(ti, NAME));
    fixture.handle.metadataAvailable = false;
    assertNull(fixture.download.getV1InfoHash());
    assertEquals(
        IndexResult.NO_METADATA,
        producer.indexIfReady(fixture.download, legacy, IndexTrigger.UPDATE));
    producer.indexExisting(List.of(fixture.download));
    assertTrue(index.get(canonical).isPresent());
    assertEquals(0, index.deletes.get());
  }

  private TorrentInfo torrentInfo(create_flags_t flags) throws Exception {
    Files.write(tempDir.resolve(NAME), new byte[] {42});
    FileStorage files = new FileStorage(new file_storage());
    files.addFile(NAME, 1);
    create_torrent creator = new create_torrent(files.swig(), 16384, flags);
    error_code error = new error_code();
    libtorrent.set_piece_hashes_ex(
        creator,
        tempDir.toString(),
        new set_piece_hashes_listener() {
          @Override
          public void progress(int piece) {}
        },
        error);
    assertEquals(0, error.value(), error.message());
    TorrentInfo ti = TorrentInfo.bdecode(Vectors.byte_vector2bytes(creator.generate().bencode()));
    assertTrue(ti.isValid());
    return ti;
  }

  private static LocalSharedTorrent row(String hash) {
    return new LocalSharedTorrent.Builder()
        .infoHash(Hex.decode(hash))
        .name(NAME)
        .sizeBytes(1)
        .fileCount(1)
        .filesJson("[]")
        .publisherNodeId(new byte[20])
        .publisherEd25519Pub(new byte[32])
        .publisherUtpPort(0)
        .addedAt(1)
        .lastSeenAt(1)
        .build();
  }

  private static void await(BooleanSupplier condition) {
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          while (!condition.getAsBoolean()) Thread.sleep(10);
        });
  }

  /**
   * No session/network: real metadata and public BTDownload methods, with deterministic handle I/O.
   */
  private static final class NativeDownload {
    final BTEngine engine;
    final MetadataHandle handle;
    final BTDownload download;

    NativeDownload(TorrentInfo ti, String legacy, Path path) throws Exception {
      Constructor<BTEngine> constructor = BTEngine.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      engine = constructor.newInstance(); // Do not start or obtain the global application engine.
      handle = new MetadataHandle(ti, legacy, path);
      download = new BTDownload(engine, new TorrentHandle(handle));
      handle.valid = true;
      handle.metadataAvailable = true;
    }
  }

  private static final class MetadataHandle extends torrent_handle {
    final TorrentInfo metadata;
    final String legacy;
    final String path;
    volatile boolean valid;
    volatile boolean metadataAvailable;

    MetadataHandle(TorrentInfo metadata, String legacy, Path path) {
      super(0, false); // Every handle I/O used by this fixture is overridden; no native session.
      this.metadata = metadata;
      this.legacy = legacy;
      this.path = path.toString();
    }

    @Override
    public boolean is_valid() {
      return valid;
    }

    @Override
    public com.frostwire.jlibtorrent.swig.int_vector get_file_priorities2() {
      com.frostwire.jlibtorrent.swig.int_vector priorities =
          new com.frostwire.jlibtorrent.swig.int_vector();
      priorities.add(4);
      return priorities;
    }

    @Override
    public torrent_info torrent_file_ptr() {
      return metadataAvailable ? metadata.swig() : null;
    }

    @Override
    public sha1_hash info_hash() {
      return new Sha1Hash(legacy).swig();
    }

    @Override
    public torrent_status status(status_flags_t flags) {
      torrent_status status = new torrent_status();
      status.setSave_path(path);
      status.setName(NAME);
      return status;
    }
  }

  private static final class RecordingIndex implements LocalIndex {
    final List<LocalSharedTorrent> rows = new CopyOnWriteArrayList<>();
    final AtomicInteger deletes = new AtomicInteger();
    final AtomicInteger upsertAttempts = new AtomicInteger();
    volatile boolean failUpsert;
    volatile boolean failDelete;

    public void upsert(LocalSharedTorrent row) {
      boolean fail = failUpsert;
      upsertAttempts.incrementAndGet();
      if (fail) throw new IllegalStateException("write failed");
      rows.removeIf(old -> old.infoHashHex().equals(row.infoHashHex()));
      rows.add(row);
    }

    public void delete(String hash) {
      if (failDelete) throw new IllegalStateException("delete failed");
      rows.removeIf(row -> row.infoHashHex().equals(hash));
      deletes.incrementAndGet();
    }

    public Optional<LocalSharedTorrent> get(String hash) {
      return rows.stream().filter(row -> row.infoHashHex().equals(hash)).findFirst();
    }

    public List<LocalSharedTorrent> listAll() {
      return List.copyOf(rows);
    }

    public List<LocalSharedTorrent> search(String query, int limit) {
      return rows.stream()
          .filter(row -> row.name().toLowerCase().contains(query))
          .limit(limit)
          .toList();
    }

    public int size() {
      return rows.size();
    }

    public void markPublished(String hash, long timestamp) {}

    public List<String> needsRepublish(long now, long threshold) {
      return List.of();
    }

    public void updateLastSeen(String hash, long timestamp) {}
  }
}
