/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.tables.SizeHolder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The library table's cells and the directory holders' file filtering. */
class LibraryCellsAndHoldersTest {

  @TempDir Path tmp;

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  @Test
  void aCellShowsItsTextAndOrdersByTheValueItWraps() {
    Object line = new Object();
    PlayableCell small = new PlayableCell(line, new SizeHolder(10), "10 B", false, 3);
    PlayableCell big = new PlayableCell(line, new SizeHolder(5000), "4.9 KB", false, 3);
    assertEquals("10 B", small.toString());
    assertTrue(small.compareTo(big) < 0);
    assertTrue(big.compareTo(small) > 0);
    assertSame(line, small.getDataLine());
    assertNotNull(small.getLocaleString());
  }

  @Test
  void cellsOfDifferentWrappedTypesFallBackToTheirText() {
    PlayableCell date = new PlayableCell(null, new Date(0), "b", false, 0);
    PlayableCell size = new PlayableCell(null, new SizeHolder(1), "a", false, 0);
    assertTrue(date.compareTo(size) > 0, "\"b\" sorts after \"a\"");
    PlayableCell text1 = new PlayableCell(null, "apple", false, 0);
    PlayableCell text2 = new PlayableCell(null, "banana", false, 0);
    assertTrue(text1.compareTo(text2) < 0);
  }

  @Test
  void aCellRemembersWhetherItIsPlayingAndItsColumn() {
    PlayableCell cell = new PlayableCell(null, "x", false, 0);
    assertFalse(cell.isPlaying());
    cell.setPlaying(true);
    assertTrue(cell.isPlaying());
    cell.setColumn(4);
    assertEquals(4, cell.getColumn());
  }

  /** A holder over a folder, with the files to hide supplied by the test. */
  private static final class FolderHolder extends AbstractDirectoryHolder {
    private final File directory;

    FolderHolder(File directory, Set<File> hidden) {
      this.directory = directory;
      this._hideFiles = hidden;
    }

    @Override
    public File getDirectory() {
      return directory;
    }
  }

  @Test
  void aHolderAcceptsOnlyDirectChildrenThatAreNotHidden() throws Exception {
    File dir = tmp.toFile();
    File song = tmp.resolve("song.mp3").toFile();
    File hidden = tmp.resolve("incomplete.mp3").toFile();
    File dsStore = tmp.resolve(".DS_Store").toFile();
    File parts = tmp.resolve(".torrent.parts").toFile();
    Files.createDirectories(tmp.resolve("sub"));
    File nested = tmp.resolve("sub/inner.mp3").toFile();
    FolderHolder holder = new FolderHolder(dir, Set.of(hidden));
    assertTrue(holder.accept(song));
    assertFalse(holder.accept(hidden));
    assertFalse(holder.accept(dsStore));
    assertFalse(holder.accept(parts));
    assertFalse(holder.accept(nested), "only the folder's own files");
    assertFalse(holder.accept(new File("/")), "no parent");
  }

  @Test
  void aHolderWithNothingToHideYetAcceptsEverythingThatIsNotFilteredByFolder() {
    FolderHolder holder = new FolderHolder(tmp.toFile(), null);
    assertTrue(holder.accept(tmp.resolve("a.mp3").toFile()));
    assertTrue(holder.accept(tmp.resolve(".DS_Store").toFile()));
  }

  @Test
  void aHolderIsNamedAndDescribedByItsFolder() {
    FolderHolder holder = new FolderHolder(tmp.toFile(), Set.of());
    assertEquals(tmp.toFile().getName(), holder.getName());
    assertEquals(tmp.toFile().getAbsolutePath(), holder.getDescription());
    assertNull(holder.getIcon());
  }

  @Test
  void listingTheFilesOnTheEdtIsRefusedWithAnEmptyList() throws Exception {
    FolderHolder holder = new FolderHolder(tmp.toFile(), Set.of());
    File[][] listed = new File[1][];
    javax.swing.SwingUtilities.invokeAndWait(() -> listed[0] = holder.getFiles());
    assertEquals(0, listed[0].length);
  }
}
