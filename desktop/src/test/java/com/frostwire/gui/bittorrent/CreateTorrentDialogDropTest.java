/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A file or folder dropped on the Create Torrent dialog becomes the torrent's content, without
 * building it (frostwire/frostwire#1474). Source-level: the dialog needs a display to be built.
 * FileOrFolderTransferHandlerTest covers the drop itself.
 */
class CreateTorrentDialogDropTest {

  @Test
  void theDialogTakesAFileOrFolderDropAsItsContent() throws Exception {
    String source =
        Files.readString(
                Path.of("src/main/java/com/frostwire/gui/bittorrent/CreateTorrentDialog.java"))
            .replaceAll("\\s+", "");
    int strip = source.indexOf("DropTargets.removeAll(this);");
    int dropTarget =
        source.indexOf(
            "newDropTarget(this,newTransferHandlerDropTargetListener("
                + "newFileOrFolderTransferHandler(this::onContentDropped)));");
    assertTrue(dropTarget >= 0, "the dialog takes file and folder drops");
    assertTrue(
        strip >= 0 && strip < dropTarget,
        "its components' drop targets are removed first, which would otherwise clear the dialog's");

    String dropped = source.substring(source.indexOf("privatevoidonContentDropped("));
    dropped = dropped.substring(0, dropped.indexOf("privatevoidonContentSelectionButton("));
    assertTrue(
        dropped.contains("setChosenContent("), "the drop picks the content like the buttons");
    assertTrue(
        !dropped.contains("makeTorrent(") && !dropped.contains("onButtonSaveAs("),
        "a drop never builds the torrent");
  }
}
