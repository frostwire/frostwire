/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.frostwire.gui.library;

import com.limegroup.gnutella.gui.tables.MouseObserver;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;

final class TreeMouseObserver implements MouseObserver {
    private final JTree tree;
    private final JPopupMenu popup;

    public TreeMouseObserver(JTree tree, JPopupMenu popup) {
        this.tree = tree;
        this.popup = popup;
    }

    public void handleMouseClick(MouseEvent e) {
    }

    /**
     * Handles when the mouse is double-clicked.
     */
    public void handleMouseDoubleClick() {
    }

    /**
     * Handles a right-mouse click.
     */
    public void handleRightMouseClick(MouseEvent e) {
    }

    /**
     * Handles a trigger to the popup menu.
     */
    public void handlePopupMenu(MouseEvent e) {
        TreePath path = tree.getUI().getClosestPathForLocation(tree, e.getPoint().x, e.getPoint().y);
        if (path != null) {
            tree.setSelectionPath(path);
            popup.show(tree, e.getX(), e.getY());
        }
    }
}
