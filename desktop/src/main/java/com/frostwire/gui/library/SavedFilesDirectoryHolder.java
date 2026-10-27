/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.util.BoundedFileWalker;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.settings.LibrarySettings;
import com.limegroup.gnutella.settings.SharingSettings;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import org.limewire.setting.FileSetting;

/**
 * @author gubatron
 * @author aldenml
 */
public class SavedFilesDirectoryHolder extends FileSettingDirectoryHolder {
  private final MediaType type;

  /** Immutable snapshot, replaced wholesale so EDT clears never race worker reads. */
  private volatile Set<File> cache;

  public SavedFilesDirectoryHolder(FileSetting saveDir, String name) {
    super(saveDir, name);
    type = MediaType.getAnyTypeMediaType();
    cache = Collections.emptySet();
  }

  public Icon getIcon() {
    return GUIMediator.getThemeImage("save");
  }

  public boolean accept(File file) {
    return super.accept(file) && type.matches(file.getName()) && !file.isDirectory();
  }

  /**
   * Collects the files under {@code root} that the Library shows, skipping {@code excludeFolders},
   * {@code hideFiles}, .DS_Store and .parts files. Bounded by {@link BoundedFileWalker}: symlinked
   * folders are not entered and the walk stops after {@code maxEntries} entries, so a symlink loop
   * or a huge Torrent Data folder can't exhaust the heap.
   */
  static Set<File> collectFiles(
      File root, Set<File> excludeFolders, Set<File> hideFiles, int maxEntries) {
    Set<File> results = new HashSet<>();
    BoundedFileWalker.walk(
        root.getAbsoluteFile(),
        maxEntries,
        BoundedFileWalker.DEFAULT_MAX_DEPTH,
        new BoundedFileWalker.Visitor() {
          @Override
          public boolean enterDirectory(Path dir) {
            return !excludeFolders.contains(dir.toFile());
          }

          @Override
          public void file(Path path, BasicFileAttributes attrs) {
            File f = path.toFile();
            if (!hideFiles.contains(f)
                && !f.getName().toLowerCase().contains(".ds_store")
                && !isPartsFile(f)) {
              results.add(f);
            }
          }
        });
    return results;
  }

  private static boolean isPartsFile(File f) {
    return f.getName().startsWith(".") && f.getName().endsWith(".parts");
  }

  public void clearCache() {
    cache = Collections.emptySet();
  }

  @Override
  public File[] getFiles() {
    Set<File> snapshot = cache;
    if (EventQueue.isDispatchThread() || !snapshot.isEmpty()) {
      return snapshot.toArray(new File[0]);
    }
    _hideFiles = TorrentUtil.getIgnorableFiles();
    Set<File> directoriesToNotInclude = LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue();
    Set<File> directoriesToInclude =
        new HashSet<>(
            Collections.singletonList(
                SharingSettings.TORRENT_DATA_DIR_SETTING
                    .getValue())); // LibrarySettings.DIRECTORIES_TO_INCLUDE.getValue();
    Set<File> files = new HashSet<>();
    for (File directory : directoriesToInclude) {
      files.addAll(
          collectFiles(
              directory,
              directoriesToNotInclude,
              _hideFiles,
              BoundedFileWalker.DEFAULT_MAX_ENTRIES));
    }
    cache = Collections.unmodifiableSet(files);
    return files.toArray(new File[0]);
  }

  public Collection<File> getCache() {
    return cache;
  }
}
