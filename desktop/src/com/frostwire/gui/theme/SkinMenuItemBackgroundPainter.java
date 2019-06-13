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

package com.frostwire.gui.theme;

import javax.swing.*;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinMenuItemBackgroundPainter extends AbstractSkinPainter {
    public SkinMenuItemBackgroundPainter(State state) {
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        paintBackground(g, width, height);
    }

    private void paintBackground(Graphics2D g, int width, int height) {
        int x = 1;
        int w = width - 2;
        if (testValid(x, 0, w, height)) {
            Shape s = shapeGenerator.createRectangle(x, 0, w, height);
            g.setPaint(SkinColors.TABLE_SELECTED_BACKGROUND_ROW_COLOR);
            g.fill(s);
        }
    }

    public enum State {
        MouseOver
    }
}
