/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * frostwire/frostwire#1402: pressing Play checked every file in the folder on disk on the EDT
 * (PlaybackUtil.isPlayableFile: exists + isDirectory), and selection and launch checked the
 * selected file too. Rows now carry those flags, read when the folder is resolved off the EDT.
 */
class LibraryRowFlagsTest {

  @TempDir Path tmp;

  /** Records the threads that asked the file system whether the file exists or what it is. */
  private static final class RecordingFile extends File {
    final Set<String> threads;

    RecordingFile(File file, Set<String> threads) {
      super(file.getPath());
      this.threads = threads;
    }

    @Override
    public boolean exists() {
      threads.add(Thread.currentThread().getName());
      return super.exists();
    }

    @Override
    public boolean isFile() {
      threads.add(Thread.currentThread().getName());
      return super.isFile();
    }

    @Override
    public boolean isDirectory() {
      threads.add(Thread.currentThread().getName());
      return super.isDirectory();
    }
  }

  @Test
  void rowsKnowWhetherTheyArePlayableFilesWithoutAskingTheDiskOnTheEdt() throws Exception {
    Set<String> threads = ConcurrentHashMap.newKeySet();
    File song = new RecordingFile(Files.createFile(tmp.resolve("song.mp3")).toFile(), threads);
    File notes = new RecordingFile(Files.createFile(tmp.resolve("notes.txt")).toFile(), threads);
    File folder =
        new RecordingFile(Files.createDirectories(tmp.resolve("album.mp3")).toFile(), threads);

    List<LibraryFilesTableDataLine.ResolvedFile> resolved =
        LibraryFilesTableDataLine.resolveAll(List.of(song, notes, folder));
    threads.clear();

    LibraryFilesTableModel model = new LibraryFilesTableModel();
    boolean[][] flags = new boolean[3][];
    String[] edt = {null};
    SwingUtilities.invokeAndWait(
        () -> {
          edt[0] = Thread.currentThread().getName();
          model.addResolved(resolved);
          for (int row = 0; row < 3; row++) {
            LibraryFilesTableDataLine line = model.get(row);
            flags[row] = new boolean[] {line.isRegularFile(), line.isPlayableFile()};
          }
        });

    assertFalse(threads.contains(edt[0]), "the file system was read on the EDT");
    assertEquals(List.of(true, true), List.of(flags[0][0], flags[0][1]), "song.mp3");
    assertEquals(List.of(true, false), List.of(flags[1][0], flags[1][1]), "notes.txt");
    assertEquals(List.of(false, false), List.of(flags[2][0], flags[2][1]), "album.mp3/ folder");
  }

  @Test
  void playSelectionAndLaunchUseTheRowFlagsNotTheDisk() throws IOException {
    String mediator =
        Files.readString(
            Path.of("src/main/java/com/frostwire/gui/library/LibraryFilesTableMediator.java"));
    for (String method :
        new String[] {
          "public List<MediaSource> getFilesView()",
          "public void handleSelection(int row)",
          "private void launch(boolean playAudio)"
        }) {
      String body = body(mediator, method);
      for (String diskCall :
          new String[] {
            ".exists()", ".isFile()", ".isDirectory()", "PlaybackUtil.isPlayableFile("
          }) {
        assertFalse(body.contains(diskCall), method + " calls " + diskCall + " on the EDT");
      }
    }
  }

  /** The text of the method whose signature is {@code signature}, braces matched. */
  private static String body(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(start >= 0, "method not found: " + signature);
    int open = source.indexOf('{', start);
    int depth = 0;
    for (int i = open; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(open, i + 1);
      }
    }
    throw new AssertionError("unbalanced braces in " + signature);
  }
}
