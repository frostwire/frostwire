/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.dnd.DropTarget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The setup wizard has no use for drag-and-drop, but its text fields and lists carry Swing's drop
 * targets, and the first one in a window makes Windows register it for drag-and-drop
 * (WComponentPeer.addNativeDropTarget), which froze the wizard as it was packed (Icebase
 * #13f43e6bcb75, GitHub #1464). The wizard's components have none now.
 */
class SetupWizardDropTargetTest {

  /** Records the components told to drop their drop target (the tests run headless). */
  private static final List<Component> CLEARED = new CopyOnWriteArrayList<>();

  private static final class Field extends JTextField {
    @Override
    public synchronized void setDropTarget(DropTarget target) {
      if (target == null) CLEARED.add(this);
      super.setDropTarget(target);
    }
  }

  private static final class Label extends SetupWindow.MultiLineLabel {
    Label() {
      super("description");
    }

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
          Field nested = new Field();
          JPanel inner = new JPanel();
          inner.add(nested);
          JPanel root = new JPanel();
          root.add(outer);
          root.add(inner);

          SetupWindow.removeDropTargets(root);

          assertTrue(CLEARED.contains(outer));
          assertTrue(CLEARED.contains(nested), "nested components too");
        });
  }

  @Test
  void thePageDescriptionDropsItsDropTarget() throws Exception {
    CLEARED.clear();
    SwingUtilities.invokeAndWait(
        () -> {
          Label label = new Label();
          assertTrue(CLEARED.contains(label));
        });
  }

  /** Source check: the dialog and each page's content need a running wizard. */
  @Test
  void theWizardStripsItsComponentsBeforeTheyAreRealized() throws Exception {
    String manager = strip("SetupManager.java");
    String pack = manager.substring(manager.indexOf("privatevoidpackAndShowDialog()"));
    assertTrue(
        pack.indexOf("SetupWindow.removeDropTargets(") >= 0
            && pack.indexOf("SetupWindow.removeDropTargets(") < pack.indexOf("dialog.pack()"),
        "the dialog's components lose their drop targets before pack()");
    String window = strip("SetupWindow.java");
    String setSetupComponent = window.substring(window.indexOf("voidsetSetupComponent("));
    assertTrue(
        setSetupComponent.indexOf("removeDropTargets(setupComponent)")
            < setSetupComponent.indexOf("add(setupComponent"),
        "each page's content loses its drop targets before it joins the shown dialog");
  }

  private static String strip(String file) throws Exception {
    return Files.readString(
            Path.of("src/main/java/com/limegroup/gnutella/gui/init/" + file),
            StandardCharsets.UTF_8)
        .replaceAll("\\s+", "");
  }
}
