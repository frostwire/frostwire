/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.dnd.DropTarget;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * MultiLineLabel and HTMLLabel are the read-only text that MessageService puts in nearly every
 * message, error and confirmation dialog. As text components they got a drop target from the look
 * and feel, so each of those dialogs registered with the OS for drag-and-drop when it opened
 * (frostwire/frostwire#1471). They drop it when their UI is installed: at construction and on a
 * theme change. The tests run headless, where Swing installs no drop targets, so they record the
 * clearing calls.
 */
class ReadOnlyLabelsTakeNoDropsTest {

  private static final List<Component> CLEARED = new CopyOnWriteArrayList<>();

  private static final class RecordingMultiLineLabel extends MultiLineLabel {
    RecordingMultiLineLabel() {
      super("A message", 400);
    }

    @Override
    public synchronized void setDropTarget(DropTarget target) {
      if (target == null) CLEARED.add(this);
      super.setDropTarget(target);
    }
  }

  private static final class RecordingHtmlLabel extends HTMLLabel {
    RecordingHtmlLabel() {
      super("<html>A <b>message</b></html>");
    }

    @Override
    public synchronized void setDropTarget(DropTarget target) {
      if (target == null) CLEARED.add(this);
      super.setDropTarget(target);
    }
  }

  @Test
  void aMultiLineLabelDropsItsDropTargetWhenBuiltAndOnAThemeChange() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          CLEARED.clear();
          RecordingMultiLineLabel label = new RecordingMultiLineLabel();
          assertTrue(CLEARED.contains(label), "when built");
          CLEARED.clear();
          label.updateUI();
          assertTrue(CLEARED.contains(label), "when the theme changes");
        });
  }

  @Test
  void anHtmlLabelDropsItsDropTargetWhenBuiltAndOnAThemeChange() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          CLEARED.clear();
          RecordingHtmlLabel label = new RecordingHtmlLabel();
          assertTrue(CLEARED.contains(label), "when built");
          CLEARED.clear();
          label.updateUI();
          assertTrue(CLEARED.contains(label), "when the theme changes");
        });
  }
}
