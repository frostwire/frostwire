/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.mcp.desktop.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferAdapterIdentityTest {
  private static final String NAME = "hybrid-lookup.bin";
  @TempDir Path tempDir;

  @Test
  void genuineHybridCanonicalV1ResolvesWrapperWhoseLegacyIdIsTruncatedV2() throws Exception {
    Fixture fixture = fixture();
    String canonical = fixture.metadata.infoHashV1().toHex();
    String legacy = fixture.metadata.infoHashV2().toHex().substring(0, 40);
    assertNotEquals(canonical, legacy);
    // These are the real public methods, not mocked IDs or an identity-only helper.
    assertEquals(legacy, fixture.download.getInfoHash());
    assertEquals(canonical, fixture.download.getV1InfoHash());
    List<BTDownload> liveDownloads = new ArrayList<>(List.of(fixture.download));

    assertSame(fixture.download, TransferAdapter.findDownload(liveDownloads, canonical));
    assertSame(
        fixture.download,
        TransferAdapter.findDownload(liveDownloads, canonical.toUpperCase(Locale.ROOT)));
    assertSame(fixture.download, TransferAdapter.findDownload(liveDownloads, legacy));
    assertNull(
        TransferAdapter.findDownload(liveDownloads, "0000000000000000000000000000000000000000"));

    // Each lookup uses the current snapshot; removing a transfer cannot leave a cached alias.
    liveDownloads.remove(fixture.download);
    assertNull(TransferAdapter.findDownload(liveDownloads, canonical));
    assertNull(TransferAdapter.findDownload(liveDownloads, legacy));
  }

  @Test
  void metadataGapRetainsLegacyLookupWithoutInventingCanonicalIdentity() throws Exception {
    Fixture fixture = fixture();
    String canonical = fixture.metadata.infoHashV1().toHex();
    String legacy = fixture.download.getInfoHash();
    fixture.handle.metadataAvailable = false;
    assertNull(fixture.download.getV1InfoHash());
    assertSame(fixture.download, TransferAdapter.findDownload(List.of(fixture.download), legacy));
    assertNull(TransferAdapter.findDownload(List.of(fixture.download), canonical));
  }

  @Test
  void emptyPublicLookupDoesNotInitializeSwingTransferModel() {
    assertNull(TransferAdapter.findDownload((String) null));
    assertNull(TransferAdapter.findDownload(""));
    assertNull(TransferAdapter.findDownload(List.of(), null));
    assertNull(TransferAdapter.findDownload(List.of(), ""));
  }

  private Fixture fixture() throws Exception {
    Files.write(tempDir.resolve(NAME), new byte[] {42});
    FileStorage files = new FileStorage(new file_storage());
    files.addFile(NAME, 1);
    create_torrent creator = new create_torrent(files.swig(), 16384, new create_flags_t());
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
    TorrentInfo metadata =
        TorrentInfo.bdecode(Vectors.byte_vector2bytes(creator.generate().bencode()));
    assertTrue(metadata.isValid());
    assertTrue(metadata.infoHashType().has_v1());
    assertTrue(metadata.infoHashType().has_v2());
    Constructor<BTEngine> constructor = BTEngine.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    BTEngine engine = constructor.newInstance();
    MetadataHandle handle =
        new MetadataHandle(
            metadata, metadata.infoHashV2().toHex().substring(0, 40), tempDir.toString());
    BTDownload download = new BTDownload(engine, new TorrentHandle(handle));
    handle.valid = true;
    handle.metadataAvailable = true;
    return new Fixture(metadata, handle, download);
  }

  private record Fixture(TorrentInfo metadata, MetadataHandle handle, BTDownload download) {}

  /** Real torrent metadata with deterministic handle I/O; no native session or Swing model. */
  private static final class MetadataHandle extends torrent_handle {
    private final TorrentInfo metadata;
    private final String legacy;
    private final String path;
    private boolean valid;
    private boolean metadataAvailable;

    MetadataHandle(TorrentInfo metadata, String legacy, String path) {
      super(0, false);
      this.metadata = metadata;
      this.legacy = legacy;
      this.path = path;
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
}
