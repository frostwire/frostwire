/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The folder chooser's "Look in" combo box rendered each ancestor folder's display name and icon on
 * the EDT on every layout pass; on Windows each is a File.exists (Icebase #6adf00ecb1c5,
 * frostwire/frostwire#1322). They must be looked up once, off the EDT.
 */
class BaseFileChooserUIDirectoryComboTest {

  private record Call(String method, File file, boolean onEdt) {}

  private static final Icon TEST_ICON = new ImageIcon(new byte[0]);

  /** Records which thread asks the file system for display names and icons. */
  private static final class RecordingFileSystemView extends FileSystemView {
    final Queue<Call> calls = new ConcurrentLinkedQueue<>();

    @Override
    public String getSystemDisplayName(File f) {
      calls.add(new Call("getSystemDisplayName", f, SwingUtilities.isEventDispatchThread()));
      return "Display " + f.getName();
    }

    @Override
    public Icon getSystemIcon(File f) {
      calls.add(new Call("getSystemIcon", f, SwingUtilities.isEventDispatchThread()));
      return TEST_ICON;
    }

    @Override
    public File createNewFolder(File containingDir) throws IOException {
      throw new IOException("not supported in tests");
    }
  }

  private static final class TestChooser extends JFileChooser {
    TestChooser(File dir, FileSystemView fsv) {
      super(dir, fsv);
    }

    @Override
    public void updateUI() {
      setUI(new BaseFileChooserUI(this));
    }
  }

  @Test
  void directoryComboRendersCachedNamesLookedUpOffTheEdt(@TempDir Path tmp) throws Exception {
    File dir = Files.createDirectories(tmp.resolve("parent").resolve("child")).toFile();
    RecordingFileSystemView fsv = new RecordingFileSystemView();
    AtomicReference<JComboBox<?>> comboRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          TestChooser chooser = new TestChooser(dir, fsv);
          comboRef.set(findDirectoryCombo(chooser));
          // Drop the one-shot setup lookups on the EDT (e.g. the home button tooltip); keep the
          // loader's, which may already have run.
          fsv.calls.removeIf(Call::onEdt);
        });
    JComboBox<?> combo = comboRef.get();
    assertNotNull(combo, "directory combo box not found");

    SwingUtilities.invokeAndWait(
        () -> {
          for (int pass = 0; pass < 20; pass++) {
            renderAllRows(combo);
          }
        });
    List<Call> edtCalls = fsv.calls.stream().filter(Call::onEdt).collect(Collectors.toList());
    assertTrue(
        edtCalls.isEmpty(),
        edtCalls.size()
            + " file system lookups on the EDT while rendering, first: "
            + edtCalls.stream().findFirst().orElse(null));

    // The lookups finish off the EDT and the rows then show the system display name and icon.
    String expected = "Display " + dir.getName();
    long deadline = System.currentTimeMillis() + 10_000;
    AtomicReference<JLabel> rendered = new AtomicReference<>();
    do {
      SwingUtilities.invokeAndWait(() -> rendered.set(renderSelected(combo)));
      if (expected.equals(rendered.get().getText())) {
        break;
      }
      Thread.sleep(20);
    } while (System.currentTimeMillis() < deadline);
    assertEquals(expected, rendered.get().getText());
    assertTrue(
        fsv.calls.stream().anyMatch(c -> c.method().equals("getSystemIcon") && !c.onEdt()),
        "icons were not looked up off the EDT");
    assertFalse(fsv.calls.stream().anyMatch(Call::onEdt), "file system asked on the EDT");

    // Rendering again uses the cache: no further lookups at all.
    int lookups = fsv.calls.size();
    SwingUtilities.invokeAndWait(() -> renderAllRows(combo));
    assertEquals(lookups, fsv.calls.size(), "rendering looked directories up again");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void renderAllRows(JComboBox<?> combo) {
    ListCellRenderer renderer = combo.getRenderer();
    JList list = new JList();
    renderer.getListCellRendererComponent(list, combo.getSelectedItem(), -1, false, false);
    for (int i = 0; i < combo.getItemCount(); i++) {
      renderer.getListCellRendererComponent(list, combo.getItemAt(i), i, false, false);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static JLabel renderSelected(JComboBox<?> combo) {
    ListCellRenderer renderer = combo.getRenderer();
    return (JLabel)
        renderer.getListCellRendererComponent(
            new JList(), combo.getSelectedItem(), -1, false, false);
  }

  private static JComboBox<?> findDirectoryCombo(Container container) {
    for (Component c : container.getComponents()) {
      if (c instanceof JComboBox<?> combo
          && combo.getModel() instanceof BaseFileChooserUI.DirectoryComboBoxModel) {
        return combo;
      }
      if (c instanceof Container child) {
        JComboBox<?> found = findDirectoryCombo(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
