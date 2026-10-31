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

import com.limegroup.gnutella.gui.tables.MouseObserver;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.tree.TreePath;

final class TreeMouseObserver implements MouseObserver {
  private final JTree tree;
  private final JPopupMenu popup;
  private final Runnable onEntryDoubleClicked;

  /** The tree row the last single click landed on, -1 for the empty space below the rows. */
  private int lastClickedRow = -1;

  /**
   * @param onEntryDoubleClicked runs when a tree entry is double-clicked (the first click has
   *     already selected it)
   */
  public TreeMouseObserver(JTree tree, JPopupMenu popup, Runnable onEntryDoubleClicked) {
    this.tree = tree;
    this.popup = popup;
    this.onEntryDoubleClicked = onEntryDoubleClicked;
  }

  public void handleMouseClick(MouseEvent e) {
    lastClickedRow = tree.getRowForLocation(e.getX(), e.getY());
  }

  /** Handles when the mouse is double-clicked: on an entry, runs the double-click action. */
  public void handleMouseDoubleClick() {
    if (lastClickedRow >= 0) {
      onEntryDoubleClicked.run();
    }
  }

  /** Handles a right-mouse click. */
  public void handleRightMouseClick(MouseEvent e) {}

  /** Handles a trigger to the popup menu. */
  public void handlePopupMenu(MouseEvent e) {
    TreePath path = tree.getUI().getClosestPathForLocation(tree, e.getPoint().x, e.getPoint().y);
    if (path != null) {
      tree.setSelectionPath(path);
      popup.show(tree, e.getX(), e.getY());
    }
  }
}
