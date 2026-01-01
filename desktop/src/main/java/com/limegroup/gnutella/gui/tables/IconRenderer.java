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

package com.limegroup.gnutella.gui.tables;

import javax.swing.*;
import java.awt.*;

/**
 * Renders the column in the search window that displays an icon for
 * whether or not the host returning the result is chattable.
 */
public final class IconRenderer extends DefaultTableBevelledCellRenderer {
    /**
     *
     */

    /**
     * The constructor sets this `JLabel` to be opaque and sets the
     * border.
     */
    public IconRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    /**
     * Returns the `Component` that displays the stars based
     * on the number of stars in the `QualityHolder` object.
     */
    public Component getTableCellRendererComponent
    (JTable table, Object value, boolean isSelected,
     boolean hasFocus, int row, int column) {
        setIcon((Icon) value);
        return super.getTableCellRendererComponent(
                table, null, isSelected, hasFocus, row, column);
    }
}
