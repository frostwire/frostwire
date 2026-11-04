/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.filechooser.FileSystemView;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1411: on Windows, the first JFileChooser built on the EDT waited on the shell
 * (COM) for the Desktop, the drives and the start folder's ancestors (Icebase #c12a377b40e1).
 * ShellFolderWarmUp makes the same calls in the background when a component with a Browse button is
 * created, so the one-time shell initialization isn't paid on the click.
 */
class ShellFolderWarmUpTest {

  /** Counts the FileSystemView calls the warm-up makes. */
  private static final class CountingView extends FileSystemView {
    final AtomicInteger comboBoxFiles = new AtomicInteger();
    final List<File> displayNames = new ArrayList<>();

    @Override
    public File createNewFolder(File containingDir) {
      throw new UnsupportedOperationException();
    }

    @Override
    public File[] getChooserComboBoxFiles() {
      comboBoxFiles.incrementAndGet();
      return new File[0];
    }

    @Override
    public String getSystemDisplayName(File f) {
      displayNames.add(f);
      return f.getName();
    }

    @Override
    public File getParentDirectory(File dir) {
      return dir.getParentFile();
    }
  }

  @Test
  void theShellIsAskedInTheBackgroundNotOnTheCallersThread() {
    CountingView view = new CountingView();
    List<Runnable> background = new ArrayList<>();
    ShellFolderWarmUp warmUp = new ShellFolderWarmUp(background::add, () -> view);

    AtomicInteger startFolderLookups = new AtomicInteger();
    assertTrue(
        warmUp.warmUp(
            () -> {
              startFolderLookups.incrementAndGet();
              return new File("/a/b/c");
            },
            1_000L));
    // Looking up the start folder checks the disk, so it waits for the background too.
    assertEquals(0, startFolderLookups.get(), "the start folder was looked up on the caller");
    assertEquals(0, view.comboBoxFiles.get(), "the shell was asked on the caller's thread");

    background.forEach(Runnable::run);
    assertEquals(1, view.comboBoxFiles.get());
    // The start folder and each ancestor, as JFileChooser's folder combo box resolves them.
    assertEquals(
        List.of(new File("/a/b/c"), new File("/a/b"), new File("/a"), new File("/")),
        view.displayNames);
  }

  @Test
  void itRunsAtMostOncePerRefreshInterval() {
    CountingView view = new CountingView();
    List<Runnable> background = new ArrayList<>();
    ShellFolderWarmUp warmUp = new ShellFolderWarmUp(background::add, () -> view);

    assertTrue(warmUp.warmUp(() -> null, 10_000L));
    assertFalse(warmUp.warmUp(() -> null, 10_000L + ShellFolderWarmUp.REFRESH_MILLIS - 1));
    assertTrue(warmUp.warmUp(() -> null, 10_000L + ShellFolderWarmUp.REFRESH_MILLIS));
    assertEquals(2, background.size());
  }

  @Test
  void aFailingShellIsHarmless() {
    ShellFolderWarmUp warmUp =
        new ShellFolderWarmUp(
            Runnable::run,
            () -> {
              throw new IllegalStateException("COM failed");
            });
    assertTrue(warmUp.warmUp(() -> new File("/a"), 1L));
  }

  /**
   * Icebase #2a52f998a1ec: the warm-up only ran for the save folder's Browse button, and the
   * torrent files folder's Browse froze on the same shell call. Every class that opens a
   * FileChooserHandler chooser starts the warm-up when it is built, and the startup thread starts
   * the first one, so its worker isn't started on the EDT either.
   */
  @Test
  void everyClassThatOpensAChooserWarmsUpTheShell() throws IOException {
    // Through FileChooserHandler, or by building a chooser directly (Icebase #ef39066da729).
    Pattern opensChooser =
        Pattern.compile(
            "FileChooserHandler\\.(getInput\\w*|getSaveAsFile)\\(|new (NoDrop|J)FileChooser\\(");
    Set<String> missing = new TreeSet<>();
    int choosers = 0;
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path file :
          (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        if (file.endsWith("FileChooserHandler.java")) {
          continue;
        }
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (opensChooser.matcher(source).find()) {
          choosers++;
          if (!source.contains("ShellFolderWarmUp.warmUpForDirectoryChooser();")) {
            missing.add(file.getFileName().toString());
          }
        }
      }
    }
    assertTrue(choosers >= 10, "the scan found only " + choosers + " classes opening a chooser");
    assertTrue(missing.isEmpty(), "open a chooser without warming up the shell: " + missing);
    String initializer =
        Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/Initializer.java"))
            .replaceAll("\\s+", "");
    assertTrue(initializer.contains("ShellFolderWarmUp.warmUpForDirectoryChooser();"));
  }
}
