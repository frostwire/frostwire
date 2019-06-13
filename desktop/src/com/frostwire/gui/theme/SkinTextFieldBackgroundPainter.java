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

import com.frostwire.gui.theme.ShapeGenerator.CornerSize;

import javax.swing.*;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinTextFieldBackgroundPainter extends AbstractSkinPainter {
    private final State state;

    public SkinTextFieldBackgroundPainter(State state) {
        this.state = state;
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        switch (state) {
            case Disabled:
                paintDisabledBackground(g, width, height);
                break;
            case Enabled:
            case Focused:
            default:
                paintDefaultBackground(g, width, height);
                break;
        }
    }

    private void paintDefaultBackground(Graphics2D g, int width, int height) {
        paintBackground(g, width, height, Color.WHITE);
    }

    private void paintDisabledBackground(Graphics2D g, int width, int height) {
        paintBackground(g, width, height, SkinColors.GENERAL_DISABLED_BACKGROUND_COLOR);
    }

    private void paintBackground(Graphics2D g, int width, int height, Color color) {
        int x = 1;
        int y = 1;
        int w = width - 2;
        int h = height - 2;
        if (testValid(x, y, w, h)) {
            Shape s = shapeGenerator.createRoundRectangle(x, y, w, h, CornerSize.BORDER);
            g.setPaint(color);
            g.fill(s);
        }
    }

    public enum State {
        Disabled, Enabled, Focused
    }
}
