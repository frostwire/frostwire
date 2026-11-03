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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Choosing a torrent's content (Select File, Select Folder, a drop, the Library) reads it on disk,
 * including every file under a folder for the piece size, off the EDT (frostwire/frostwire#1474).
 */
class CreateTorrentDialogContentTest {

  @TempDir Path dir;

  @Test
  void aFileIsSingleFileContent() throws Exception {
    File file = Files.writeString(dir.resolve("song.mp3"), "abc").toFile();

    CreateTorrentDialog.ChosenContent content =
        CreateTorrentDialog.readChosenContent(file, JFileChooser.FILES_ONLY);

    assertTrue(content.readable());
    assertFalse(content.directory());
    assertEquals(file, content.file());
    assertEquals(CreateTorrentDialog.PieceSize._16KB, content.recommendedPieceSize());
  }

  @Test
  void selectFolderTurnsAFileIntoItsFolder() throws Exception {
    File file = Files.writeString(dir.resolve("song.mp3"), "abc").toFile();

    CreateTorrentDialog.ChosenContent content =
        CreateTorrentDialog.readChosenContent(file, JFileChooser.DIRECTORIES_ONLY);

    assertTrue(content.directory());
    assertEquals(dir.toFile(), content.file());
    assertFalse(content.emptyFolder());
  }

  @Test
  void aDroppedFolderIsFolderContentAndAnEmptyOneIsMarked() throws Exception {
    File empty = Files.createDirectory(dir.resolve("empty")).toFile();

    CreateTorrentDialog.ChosenContent content =
        CreateTorrentDialog.readChosenContent(empty, JFileChooser.FILES_AND_DIRECTORIES);

    assertTrue(content.readable());
    assertTrue(content.directory());
    assertTrue(content.emptyFolder());
  }

  @Test
  void aMissingFileIsNotReadable() {
    CreateTorrentDialog.ChosenContent content =
        CreateTorrentDialog.readChosenContent(
            dir.resolve("gone.bin").toFile(), JFileChooser.FILES_AND_DIRECTORIES);

    assertFalse(content.readable());
  }

  @Test
  void choosingContentOnlySchedulesTheRead() throws Exception {
    String source =
        Files.readString(
                Path.of("src/main/java/com/frostwire/gui/bittorrent/CreateTorrentDialog.java"))
            .replaceAll("\\s+", "");
    String choose = source.substring(source.indexOf("publicvoidsetChosenContent("));
    choose = choose.substring(0, choose.indexOf("staticChosenContentreadChosenContent("));
    assertTrue(
        choose.contains("readThenShow(()->readChosenContent(chosenFile,fileChoosingMode),"),
        "the content is read on the background queue");
    assertFalse(choose.contains("canRead()"), "setChosenContent reads the disk on the EDT");
    String saveAs = source.substring(source.indexOf("privatevoidonButtonSaveAs("));
    saveAs = saveAs.substring(0, saveAs.indexOf("privatebooleanshowSaveAsDialog("));
    assertFalse(saveAs.contains("listFiles()"), "Save lists the folder on the EDT");
  }
}
