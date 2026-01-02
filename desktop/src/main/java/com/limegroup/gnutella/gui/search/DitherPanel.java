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

package com.limegroup.gnutella.gui.search;

import javax.swing.*;
import java.awt.*;

/**
 * Simple extension of JPanel that makes a FlowLayout.LEADING JPanel that
 * has a background image which is painted.
 */
public class DitherPanel extends JPanel {
    private final Ditherer DITHERER;

    /**
     * Creates a FlowLayout.LEADING layout.
     *
     * @param ditherer the `Ditherer` that paints the dithered
     *                 background
     */
    public DitherPanel(Ditherer ditherer) {
        super();
        DITHERER = ditherer;
    }

    /**
     * Does the actual placement of the background image.
     */
    public void paintComponent(java.awt.Graphics g) {
        Dimension size = getSize();
        DITHERER.draw(g, size.height, size.width);
    }
}
