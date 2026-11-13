/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * Keeps windows that take no drops from registering for drag-and-drop.
 *
 * <p>Swing gives every text component a drop target (lists, tables and trees drop theirs unless
 * given a TransferHandler that takes drops). When the first drop target in a window is realized,
 * AWT registers that window with the OS for drag-and-drop. On Windows that is an OLE call
 * (WComponentPeer.addNativeDropTarget) that froze the setup wizard as it appeared
 * (frostwire/frostwire#1464).
 *
 * <p>Only the main window takes drops. Every other window and dialog calls {@link #removeAll} on
 * itself before it is packed or shown, and on content it adds while it is shown. Keyboard cut, copy
 * and paste go through the TransferHandler, not the drop target, so they keep working.
 */
public final class DropTargets {

  private DropTargets() {}

  /**
   * {@link JOptionPane#showInputDialog(Component, Object, String, int, javax.swing.Icon, Object[],
   * Object)} with a text field, for a dialog whose window registers for no drag-and-drop:
   * JOptionPane builds and shows that dialog itself, so its text field's drop target can't be
   * removed first. Builds the same dialog and removes it before the dialog is realized.
   *
   * @param initialValue the text the field starts with, or null
   * @return the text entered, or null if the dialog was cancelled or closed
   */
  public static String showInputDialog(
      Component parent, Object message, String title, int messageType, Object initialValue) {
    JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.OK_CANCEL_OPTION);
    pane.setWantsInput(true);
    pane.setInitialSelectionValue(initialValue);
    // Before createDialog: it packs the dialog, which realizes it, and a text field that still
    // has its drop target then registers it with the OS (#1536).
    removeAll(pane);
    JDialog dialog = pane.createDialog(parent, title);
    pane.selectInitialValue();
    removeAll(dialog);
    dialog.setVisible(true);
    dialog.dispose();
    Object value = pane.getInputValue();
    return value == JOptionPane.UNINITIALIZED_VALUE ? null : (String) value;
  }

  /** Removes the drop target of {@code root} and of every component in its tree. */
  public static void removeAll(Component root) {
    root.setDropTarget(null);
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) {
        removeAll(child);
      }
    }
  }
}
