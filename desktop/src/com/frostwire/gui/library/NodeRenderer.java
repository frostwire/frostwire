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

import com.frostwire.gui.theme.ThemeMediator;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
class NodeRenderer extends DefaultTreeCellRenderer {
    private static final AbstractBorder DIRECTORY_HOLDER_NODE_BORDER = new EmptyBorder(5, 5, 5, 5);

    public NodeRenderer() {
        super();
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (sel) {
            setBackgroundSelectionColor(ThemeMediator.TABLE_SELECTED_BACKGROUND_ROW_COLOR);
        } else {
            setBackgroundNonSelectionColor(row % 2 == 0 ? Color.WHITE : ThemeMediator.TABLE_ALTERNATE_ROW_COLOR);
        }
        if (value instanceof DirectoryHolderNode) {
            DirectoryHolderNode node = (DirectoryHolderNode) value;
            DirectoryHolder dh = node.getDirectoryHolder();
            setText(dh.getName());
            setToolTipText(dh.getDescription());
            Icon icon = dh.getIcon();
            if (icon != null) {
                setIcon(icon);
                setBorder(DIRECTORY_HOLDER_NODE_BORDER);
            }
        }
        return this;
    }
}
