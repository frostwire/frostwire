/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/**
 * Structure regression for the mesh-first torrent metadata path: x.pe magnets try TORRENT_FETCH
 * over IceBridge (Protocol #3 METADATA) before falling back to the direct magnet add, so cellular
 * peers get metadata even when the seeder sits behind an unreachable NAT.
 */
public class TorrentFetcherMeshMetadataStructureTest {

  @Test
  public void magnetDownloadsFetchMeshMetadataBeforeDirectAdd() throws Exception {
    String source =
        readProjectFile(
            "src/main/java/com/frostwire/android/gui/transfers/TorrentFetcherDownload.java");
    String compact = source.replaceAll("\\s+", "");

    assertTrue(
        "mesh fetch must run before the direct magnet add",
        compact.indexOf("fetchMeshTorrentMetadata(uri)") >= 0
            && compact.indexOf("fetchMeshTorrentMetadata(uri)")
                < compact.indexOf(
                    "BTEngine.getInstance().download(uri,null,newtorrent_flags_t());"));
    assertTrue(
        "mesh metadata success must start the transfer with x.pe peers",
        compact.contains(
            "downloadTorrent(meshMetadata,LibTorrentMagnetDownloader.parsePeers(uri));"));
    assertTrue(
        "holder pub comes from the magnet x.hp param",
        compact.contains("LibTorrentMagnetDownloader.parseHolderPub(magnetUri)"));
    assertTrue("fetch rides the distributed relay wiring", compact.contains("DISTRIBUTED_WIRING"));
    assertTrue(
        "fetch failure falls back to the direct magnet add",
        compact.contains("Startingx.pemagnetdirectlyinBTEngine"));
  }

  private static String readProjectFile(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
