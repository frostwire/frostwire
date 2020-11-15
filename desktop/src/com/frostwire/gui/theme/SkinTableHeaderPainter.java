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
import java.awt.geom.Path2D;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinTableHeaderPainter extends AbstractSkinPainter {
    private final State state;

    public SkinTableHeaderPainter(State state) {
        this.state = state;
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        if (testValid(0, 0, width - 1, height - 1)) {
            Shape s = shapeGenerator.createRectangle(0, 0, width, height);
            g.setPaint(getTableHeaderPaint(s));
            g.fill(s);
            paintBorder(g, width, height);
        }
    }

    private void paintBorder(Graphics2D g, int width, int height) {
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        path.reset();
        path.moveTo(0, height - 1);
        path.lineTo(width - 1, height - 1);
        path.lineTo(width - 1, 0);
        g.setPaint(SkinColors.TABLE_HEADER_BORDER_COLOR);
        g.draw(path);
    }

    private Paint getTableHeaderPaint(Shape s) {
        switch (state) {
            case Enabled:
                return createVerticalGradient(s, SkinColors.TABLE_HEADER_ENABLED_COLORS);
            case MouseOver:
            case Pressed:
                return createVerticalGradient(s, SkinColors.TABLE_HEADER_PRESSED_COLORS); // not an error, neede to check deep in nimbus
            default:
                throw new IllegalArgumentException("Not supported state");
        }
    }

    public enum State {
        Enabled, MouseOver, Pressed
    }
}
