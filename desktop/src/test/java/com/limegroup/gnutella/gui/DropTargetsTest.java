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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The first drop target realized in a window makes Windows register the window for drag-and-drop
 * (frostwire/frostwire#1464). {@link DropTargets#removeAll} clears every drop target in a tree. The
 * tests run headless, where Swing installs no drop targets, so they record the clearing calls.
 */
class DropTargetsTest {

  private static final List<Component> CLEARED = new CopyOnWriteArrayList<>();

  private static final class Field extends JTextField {
    @Override
    public synchronized void setDropTarget(DropTarget target) {
      if (target == null) CLEARED.add(this);
      super.setDropTarget(target);
    }
  }

  private static final class Area extends JTextArea {
    @Override
    public synchronized void setDropTarget(DropTarget target) {
      if (target == null) CLEARED.add(this);
      super.setDropTarget(target);
    }
  }

  @Test
  void everyComponentInTheTreeDropsItsDropTarget() throws Exception {
    CLEARED.clear();
    SwingUtilities.invokeAndWait(
        () -> {
          Field outer = new Field();
          Area scrolled = new Area();
          JPanel inner = new JPanel();
          inner.add(new JScrollPane(scrolled));
          JPanel root = new JPanel();
          root.add(outer);
          root.add(inner);

          DropTargets.removeAll(root);

          assertTrue(CLEARED.contains(outer));
          assertTrue(CLEARED.contains(scrolled), "nested components, inside scroll panes too");
        });
  }
}
