/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.MediaType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

/**
 * A download that finished before the Library view was first shown made the background scan throw a
 * NullPointerException in LibraryExplorer.getMediaTypeSavedFilesDirectoryHolders: the view is built
 * lazily, so the explorer's tree root was still null (Icebase #711e86158de8, dev build 338). The
 * explorer now publishes its media-type holders when the tree is built, and the scan neither walks
 * the Swing tree nor builds the explorer.
 */
class LibraryScanBeforeExplorerTest {

  @Test
  void aTreeThatIsNotBuiltYetHasNoHolders() {
    assertEquals(List.of(), LibraryExplorer.mediaTypeHoldersIn(null));
  }

  @Test
  void onlyTheMediaTypeHoldersOfTheTreeAreCollected() {
    MediaTypeSavedFilesDirectoryHolder audio =
        new MediaTypeSavedFilesDirectoryHolder(MediaType.getAudioMediaType());
    MediaTypeSavedFilesDirectoryHolder video =
        new MediaTypeSavedFilesDirectoryHolder(MediaType.getVideoMediaType());
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    root.add(new DirectoryHolderNode(audio));
    root.add(new DirectoryHolderNode(new TorrentDirectoryHolder()));
    DefaultMutableTreeNode group = new DefaultMutableTreeNode("group");
    group.add(new DirectoryHolderNode(video));
    root.add(group);

    List<MediaTypeSavedFilesDirectoryHolder> holders = LibraryExplorer.mediaTypeHoldersIn(root);

    assertEquals(2, holders.size());
    assertSame(audio, holders.get(0));
    assertSame(video, holders.get(1));
    assertTrue(holders.stream().noneMatch(java.util.Objects::isNull));
  }

  @Test
  void theCollectedListCannotBeChangedByTheScan() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    root.add(
        new DirectoryHolderNode(
            new MediaTypeSavedFilesDirectoryHolder(MediaType.getAudioMediaType())));
    List<MediaTypeSavedFilesDirectoryHolder> holders = LibraryExplorer.mediaTypeHoldersIn(root);
    try {
      holders.clear();
      throw new AssertionError("the list is shared with other threads and must be immutable");
    } catch (UnsupportedOperationException expected) {
      // fine
    }
  }

  @Test
  void theExplorerPublishesItsHoldersWhenTheTreeIsBuiltAndTheGetterNeverTouchesTheTree()
      throws Exception {
    String source = source("LibraryExplorer.java");
    assertTrue(
        source.contains("privatevolatileList<MediaTypeSavedFilesDirectoryHolder>mediaTypeHolders"),
        "the holders are published through a volatile field");
    assertTrue(
        source.contains(
            "model=newDefaultTreeModel(root);mediaTypeHolders=mediaTypeHoldersIn(root);"),
        "the holders are published as soon as the tree is built");
    int getter = source.indexOf("getMediaTypeSavedFilesDirectoryHolders(){");
    assertTrue(getter >= 0, "getter not found");
    String body = source.substring(getter, source.indexOf('}', getter));
    assertEquals("getMediaTypeSavedFilesDirectoryHolders(){returnmediaTypeHolders;", body);
  }

  @Test
  void theScanDoesNotBuildTheLibraryExplorer() throws Exception {
    String source = source("LibraryMediator.java");
    int scan = source.indexOf("privatevoidscanInternal(");
    assertTrue(scan >= 0, "scanInternal not found");
    String body = source.substring(scan, source.indexOf("/**", scan));
    assertFalse(body.contains("getLibraryExplorer()"), "the scan builds the Library explorer");
    assertTrue(body.contains("libraryExplorerIfCreated()"));
    assertTrue(
        source.contains("privatevolatileLibraryExplorerlibraryExplorer;"),
        "the explorer is read from background threads");
  }

  private static String source(String file) throws Exception {
    return Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/frostwire/gui/library/" + file),
            StandardCharsets.UTF_8)
        .replaceAll("\\s+", "");
  }
}
