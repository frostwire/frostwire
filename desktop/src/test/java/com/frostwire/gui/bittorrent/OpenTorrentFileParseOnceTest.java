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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Icebase #08f3fa6109d9 (frostwire/frostwire#1468): opening a .torrent file parsed it on the EDT
 * twice, once for the file selection dialog and once more for the info-hash used to select the
 * transfer. The open path now parses it once, in {@code readTorrentFile} on the background queue,
 * and hands the parsed torrent and its hash to the EDT. Source-level because the mediator cannot be
 * built without the whole GUI; whitespace-stripped so reformatting cannot hide a call.
 */
class OpenTorrentFileParseOnceTest {

  private static String source(String path) throws IOException {
    return Files.readString(Path.of(path)).replaceAll("\\s+", "");
  }

  private static String between(String source, String from, String to) {
    int start = source.indexOf(from);
    int end = source.indexOf(to, start);
    assertTrue(start >= 0 && end > start, from + " ... " + to + " not found");
    return source.substring(start, end);
  }

  private static int count(String text, String needle) {
    int n = 0;
    for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
      n++;
    }
    return n;
  }

  @Test
  void theOpenPathParsesTheFileOnceInTheBackgroundRead() throws IOException {
    String mediator = source("src/main/java/com/frostwire/gui/bittorrent/BTDownloadMediator.java");
    String openPath =
        between(mediator, "publicvoidopenTorrentFile(", "privatevoidaddOpenedTorrentFile(");
    assertEquals(1, count(openPath, "newTorrentInfo("), "the open path parses the file once");
    String read =
        between(
            openPath,
            "privatestaticOpenedTorrentFilereadTorrentFile(",
            "static<T>voidreadThenShow(");
    assertTrue(read.contains("newTorrentInfo(torrentFile)"), "readTorrentFile parses the file");
    assertTrue(read.contains(".exists()"), "readTorrentFile looks for the seed data");
    assertTrue(
        openPath.contains("readThenShow(()->readTorrentFile(torrentFile),"),
        "openTorrentFile reads the file through readThenShow");
    assertTrue(
        openPath.contains("BackgroundQueuedExecutorService::schedule,GUIMediator::safeInvokeLater"),
        "the read runs on the background queue and the result is shown on the EDT");
  }

  @Test
  void theEdtHalfNeitherParsesNorTouchesTheDisk() throws IOException {
    String mediator = source("src/main/java/com/frostwire/gui/bittorrent/BTDownloadMediator.java");
    String edtHalf =
        between(mediator, "privatevoidshowOpenedTorrentFile(", "privatevoidaddOpenedTorrentFile(");
    assertFalse(edtHalf.contains("TorrentInfo("), "the EDT half parses the file");
    assertFalse(edtHalf.contains(".exists()"), "the EDT half checks the disk");
  }

  @Test
  void selectingTheOpenedTransferUsesTheHashItIsGiven() throws IOException {
    String gui = source("src/main/java/com/limegroup/gnutella/gui/GUIMediator.java");
    String open =
        between(gui, "publicvoidopenTorrentFile(FiletorrentFile,", "publicvoidopenTorrentForSeed(");
    assertFalse(open.contains("TorrentInfo"), "GUIMediator.openTorrentFile parses the file again");
  }

  @Test
  void theFileSelectionDialogIsGivenAParsedTorrent() throws IOException {
    String dialog = source("src/main/java/com/frostwire/gui/bittorrent/PartialFilesDialog.java");
    assertFalse(
        dialog.contains("PartialFilesDialog(JFrameframe,FiletorrentFile)"),
        "PartialFilesDialog parses a .torrent file in its constructor, on the EDT");
    assertFalse(
        dialog.contains("PartialFilesDialog(JFrameframe,byte[]bytes,"),
        "PartialFilesDialog bdecodes torrent bytes in its constructor, on the EDT");
  }

  /**
   * A torrent fetched from a magnet or a URL was bdecoded twice on the EDT too: by the dialog's
   * byte[] constructor and again before the download. The fetcher's thread decodes it once.
   */
  @Test
  void aFetchedTorrentIsDecodedOnceOnTheFetchersThread() throws IOException {
    String fetcher =
        source("src/main/java/com/frostwire/gui/bittorrent/TorrentFetcherDownload.java");
    String download =
        between(fetcher, "privatevoiddownloadTorrent(", "privateboolean[]calculateSelection(");
    assertEquals(1, count(download, "TorrentInfo.bdecode("), "the torrent is decoded once");
    String onEdt = download.substring(download.indexOf("GUIMediator.safeInvokeLater("));
    assertFalse(onEdt.contains("bdecode("), "the fetched torrent is decoded on the EDT");
    assertTrue(
        download.indexOf("TorrentInfo.bdecode(data)") < download.indexOf("if(relativePath!=null)"),
        "the torrent is decoded before either path uses it");
  }
}
