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

import com.frostwire.jlibtorrent.TorrentInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The file selection dialog read its torrent through jlibtorrent on the EDT: two JNI calls per file
 * when built, and the file count on every table row count (Icebase #d83c2b866deb,
 * frostwire/frostwire#1473). It now gets the title and file list read beforehand, off the EDT.
 */
class PartialFilesDialogContentsTest {

  /** A two-file torrent: "pack/a.txt" (3 bytes) and "pack/sub/b.mp3" (5 bytes). */
  private static final String TWO_FILES =
      "d4:infod5:filesld6:lengthi3e4:pathl5:a.txteed6:lengthi5e4:pathl3:sub5:b.mp3eee"
          + "4:name4:pack12:piece lengthi16384e6:pieces20:xxxxxxxxxxxxxxxxxxxxee";

  @Test
  void contentsHoldTheTitleAndEveryFile() {
    TorrentInfo torrent = TorrentInfo.bdecode(TWO_FILES.getBytes(StandardCharsets.US_ASCII));

    PartialFilesDialog.Contents contents = PartialFilesDialog.contentsOf(torrent);

    assertEquals("pack", contents.title());
    assertEquals(2, contents.files().length);
    assertTrue(contents.files()[0].path.endsWith("a.txt"), contents.files()[0].path);
    assertEquals(3, contents.files()[0].size);
    assertTrue(contents.files()[1].path.endsWith("b.mp3"), contents.files()[1].path);
    assertEquals(5, contents.files()[1].size);
    assertTrue(contents.files()[0].selected, "every file starts selected");
  }

  @Test
  void theDialogKeepsNoTorrentAndBothCallersReadItsContentsFirst() throws IOException {
    String dialog = source("PartialFilesDialog.java");
    assertFalse(dialog.contains("privatefinalTorrentInfo"), "the dialog keeps a TorrentInfo");
    assertTrue(
        dialog.contains("PartialFilesDialog(JFrameframe,Contentscontents,Stringname)"),
        "the dialog is built from Contents");

    String mediator = source("BTDownloadMediator.java");
    String read =
        mediator.substring(
            mediator.indexOf("privatestaticOpenedTorrentFilereadTorrentFile("),
            mediator.indexOf("static<T>voidreadThenShow("));
    assertTrue(read.contains("PartialFilesDialog.contentsOf("), "read on the background queue");

    String fetcher = source("TorrentFetcherDownload.java");
    String download = fetcher.substring(fetcher.indexOf("privatevoiddownloadTorrent("));
    int contents = download.indexOf("PartialFilesDialog.contentsOf(");
    int onEdt = download.indexOf("GUIMediator.safeInvokeLater(");
    assertTrue(contents >= 0 && contents < onEdt, "read on the fetcher's thread, before the EDT");
  }

  private static String source(String file) throws IOException {
    return Files.readString(Path.of("src/main/java/com/frostwire/gui/bittorrent/" + file))
        .replaceAll("\\s+", "");
  }
}
