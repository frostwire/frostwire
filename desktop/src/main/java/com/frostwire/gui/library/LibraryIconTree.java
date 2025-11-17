/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

import com.frostwire.gui.theme.IconRepainter;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author gubatron
 * @author aldenml
 */
class LibraryIconTree extends JTree {
    private static final Logger LOG = Logger.getLogger(LibraryIconTree.class.getName());

    private LibraryIconTree() {
        loadIcons();
    }

    public LibraryIconTree(TreeModel dataModel) {
        super(dataModel);
        loadIcons();
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Media player removed
    }

    private void loadIcons() {
    }

    private void paintIcon(Graphics g, Image image, TreePath path) {
        Rectangle rect = getUI().getPathBounds(this, path);
        if (rect != null) {
            Dimension lsize = rect.getSize();
            Point llocation = rect.getLocation();
            g.drawImage(image, llocation.x + getWidth() - image.getWidth(null) - 4, llocation.y + (lsize.height - image.getHeight(null)) / 2, null);
        }
    }

    private TreePath getAudioPath() {
        Enumeration<?> e = ((LibraryNode) getModel().getRoot()).depthFirstEnumeration();
        while (e.hasMoreElements()) {
            LibraryNode node = (LibraryNode) e.nextElement();
            if (node instanceof DirectoryHolderNode) {
                DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                if (holder instanceof MediaTypeSavedFilesDirectoryHolder && ((MediaTypeSavedFilesDirectoryHolder) holder).getMediaType().equals(MediaType.getAudioMediaType())) {
                    return new TreePath(node.getPath());
                }
            }
        }
        return null;
    }
}
