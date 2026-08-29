/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Structure regression for the desktop TORRENT_FETCH requester path: magnet downloads try mesh
 * metadata (IceBridge, Protocol #3 METADATA) before the direct magnet fetch, so desktops can pull
 * metadata from phone-seeded torrents behind CGNAT.
 */
class TorrentFetcherMeshMetadataStructureTest {

  @Test
  void magnetDownloadsFetchMeshMetadataBeforeDirectFetch() throws Exception {
    String fetcher = read("src/main/java/com/frostwire/gui/bittorrent/TorrentFetcherDownload.java");
    String compact = fetcher.replaceAll("\\s+", "");

    assertTrue(
        compact.indexOf("fetchMeshTorrentMetadata()") >= 0
            && compact.indexOf("fetchMeshTorrentMetadata()")
                < compact.indexOf("magnetDownloader.download(uri,90);"),
        "mesh fetch must run before the direct magnet fetch");
    assertTrue(
        compact.contains("LibTorrentMagnetDownloader.parseHolderPub(uri)"),
        "holder pub comes from the magnet x.hp param");
    assertTrue(
        compact.contains("MeshRequestContext.isReady()"),
        "mesh request rides MeshRequestContext wiring");

    String initializer = read("src/main/java/com/limegroup/gnutella/gui/Initializer.java");
    assertTrue(
        initializer.contains("LibtorrentTorrentMetadataProvider"),
        "desktop must answer TORRENT_FETCH via the libtorrent provider");
    assertTrue(
        initializer.contains("MeshRequestContext.init(transport, identity)"),
        "bootstrap must expose the wiring to download paths");
  }

  private static String read(String relativePath) throws IOException {
    return Files.readString(
        Path.of(System.getProperty("user.dir")).resolve(relativePath), StandardCharsets.UTF_8);
  }
}
