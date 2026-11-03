/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.swig.create_flags_t;
import com.frostwire.jlibtorrent.swig.create_torrent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Create Torrent's Advanced tab: libtorrent 2's creation flags, with the torrent type's v1/v2 flag
 * (frostwire/frostwire#1474).
 */
class CreateTorrentDialogFlagsTest {

  private static boolean has(create_flags_t flags, create_flags_t flag) {
    return flags.and_(flag).nonZero();
  }

  @Test
  void aHybridTorrentWithNoAdvancedOptionsHasNoFlags() {
    assertEquals(
        0, CreateTorrentDialog.creationFlags(TorrentType.HYBRID, false, false, false).to_int());
  }

  @Test
  void theTypeAndEachOptionSetTheirFlag() {
    create_flags_t v1 = CreateTorrentDialog.creationFlags(TorrentType.V1_ONLY, true, false, true);
    assertTrue(has(v1, create_torrent.v1_only));
    assertFalse(has(v1, create_torrent.v2_only));
    assertTrue(has(v1, create_torrent.modification_time));
    assertFalse(has(v1, create_torrent.symlinks));
    assertTrue(has(v1, create_torrent.no_attributes));

    create_flags_t v2 = CreateTorrentDialog.creationFlags(TorrentType.V2_ONLY, false, true, false);
    assertTrue(has(v2, create_torrent.v2_only));
    assertFalse(has(v2, create_torrent.v1_only));
    assertTrue(has(v2, create_torrent.symlinks));
    assertFalse(has(v2, create_torrent.modification_time));
  }

  @Test
  void bothListingTheFilesAndCreatingTheTorrentGetTheFlags() throws Exception {
    String source =
        Files.readString(
                Path.of("src/main/java/com/frostwire/gui/bittorrent/CreateTorrentDialog.java"))
            .replaceAll("\\s+", "");
    assertTrue(source.contains("libtorrent.add_files(fs,f.getPath(),creationFlags);"));
    assertTrue(source.contains("newcreate_torrent(fs,pieceSize,creationFlags);"));
  }
}
