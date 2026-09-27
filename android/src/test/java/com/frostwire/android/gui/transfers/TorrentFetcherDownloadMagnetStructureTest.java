/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards direct magnet adds for hybrid/v2 torrents found through distributed search. */
public class TorrentFetcherDownloadMagnetStructureTest {

  @Test
  public void searchResultMagnetBypassesTorrentFileReconstruction() throws Exception {
    String source =
        readProjectFile(
            "src/main/java/com/frostwire/android/gui/transfers/TorrentFetcherDownload.java");
    assertTrue(source.contains("uri.startsWith(\"magnet:\") && fetcherListener == null"));
    String compactSource = source.replaceAll("\\s+", "");
    assertTrue(
        compactSource.contains(
            "BTEngine.getInstance().download(uri,null,newtorrent_flags_t(),info.getDistributedSourcePeerPub())"));

    String asyncStartDownload =
        readProjectFile("src/main/java/com/frostwire/android/gui/tasks/AsyncStartDownload.java");
    String compactAsync = asyncStartDownload.replaceAll("\\s+", "");
    assertTrue(compactAsync.contains("torrentUrl.contains(\"&x.pe=\")"));
    assertTrue(compactAsync.contains("TorrentFetcherListenerlistener=directMeshMagnet?null:"));
    assertTrue(
        compactAsync.contains(
            "downloadTorrent(torrentUrl,listener,sr.getDisplayName(),distributedSourcePeerPub)"));
  }

  @Test
  public void incomingPortReadsUseNonZeroDefaults() throws Exception {
    String mainApplication =
        readProjectFile("src/main/java/com/frostwire/android/gui/MainApplication.java");
    assertTrue(mainApplication.contains("Constants.DEFAULT_TORRENT_INCOMING_PORT_START"));
    assertTrue(mainApplication.contains("Constants.DEFAULT_TORRENT_INCOMING_PORT_END"));
    assertTrue(mainApplication.contains("configuredStartPort < 1"));
    assertTrue(mainApplication.contains("configuredEndPort > 65535"));
  }

  @Test
  public void transferUpdatesRefreshCachedState() throws Exception {
    String source =
        readProjectFile(
            "src/main/java/com/frostwire/android/gui/transfers/UIBittorrentDownload.java");
    int updateUi = source.indexOf("void updateUI(BTDownload dl)");
    int nextMethod = source.indexOf("public void clearCachedItems()", updateUi);
    assertTrue(updateUi >= 0 && nextMethod > updateUi);
    assertTrue(source.substring(updateUi, nextMethod).contains("updateCachedState()"));
  }

  private static String readProjectFile(String relativePath) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relativePath);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relativePath);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
