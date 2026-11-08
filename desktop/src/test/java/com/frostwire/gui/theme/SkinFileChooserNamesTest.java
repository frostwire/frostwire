/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import org.junit.jupiter.api.Test;

/**
 * The chooser's list renderer asks for each row's display name, and on Windows the shell answers
 * over COM: asking it on the EDT stalled the save folder chooser (Icebase #0d182adae29c,
 * frostwire/frostwire#1428). The file view answers with the plain name and asks the shell on its
 * lookup worker.
 */
class SkinFileChooserNamesTest {

  @Test
  void displayNamesAreLookedUpOffTheEdt() throws Exception {
    List<Boolean> askedOnEdt = new CopyOnWriteArrayList<>();
    FileSystemView recording =
        new FileSystemView() {
          @Override
          public File createNewFolder(File containingDir) {
            throw new UnsupportedOperationException();
          }

          @Override
          public String getSystemDisplayName(File f) {
            if (f.getName().equals("movie.mkv")) {
              askedOnEdt.add(SwingUtilities.isEventDispatchThread());
            }
            return "Display " + f.getName();
          }
        };
    File file = new File("movie.mkv");
    AtomicReference<FileView> view = new AtomicReference<>();
    AtomicReference<String> first = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          JFileChooser chooser = new JFileChooser(recording);
          SkinFileChooserUI ui = new SkinFileChooserUI(chooser);
          ui.installUI(chooser);
          view.set(ui.getFileView(chooser));
          first.set(view.get().getName(file));
        });

    assertEquals("movie.mkv", first.get(), "the row shows the plain name at once");
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    AtomicReference<String> later = new AtomicReference<>();
    while (System.nanoTime() < deadline) {
      SwingUtilities.invokeAndWait(() -> later.set(view.get().getName(file)));
      if ("Display movie.mkv".equals(later.get())) {
        break;
      }
      Thread.sleep(20);
    }
    assertEquals("Display movie.mkv", later.get(), "the shell's name replaces it once loaded");
    assertFalse(askedOnEdt.isEmpty(), "the shell was never asked");
    assertTrue(askedOnEdt.stream().noneMatch(onEdt -> onEdt), "the shell was asked on the EDT");
  }
}
