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

package com.limegroup.gnutella.gui.tables;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * So that table cells look beveled, details that make the difference.
 *
 * @author gubatron
 * @author aldenml
 */
public class DefaultTableBevelledCellRenderer extends DefaultTableCellRenderer {
    private boolean isSelected;

    @Override
    protected void paintBorder(Graphics g) {
        super.paintBorder(g);
        if (!isSelected) {
            BeveledCellPainter.paintBorder(g, getWidth(), getHeight());
        }
        setBorder(new EmptyBorder(3, 3, 3, 3));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        this.isSelected = isSelected;
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
}