/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.Component;
import java.awt.Container;

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
