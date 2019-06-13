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

import java.awt.*;

public final class BeveledCellPainter {
    private static final Color CELL_UPPER_BORDER_COLOR = Color.WHITE;
    private static final Color CELL_BOTTOM_BORDER_COLOR = new Color(0xe8eaed);

    public static void paintBorder(Graphics g, int width, int height) {
        //and then paint an upper white 1px line and a bottom 1px gray line to give depth effect on each row.
        if (g != null) {
            g.setColor(CELL_UPPER_BORDER_COLOR);
            g.drawLine(0, 0, width, 0);
            g.setColor(CELL_BOTTOM_BORDER_COLOR);
            g.drawLine(0, height - 1, width, height - 1);
        }
    }
}
