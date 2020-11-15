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
public final class SkinPopupMenuBackgroundPainter extends AbstractSkinPainter {
    public SkinPopupMenuBackgroundPainter(State state) {
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        paintBackground(g, width, height);
    }

    private void paintBackground(Graphics2D g, int width, int height) {
        int w = width - 1;
        int h = height - 1;
        if (testValid(0, 0, w, h)) {
            Shape s = shapeGenerator.createRectangle(0, 0, w, h);
            g.setPaint(SkinColors.LIGHT_BACKGROUND_COLOR);
            g.fill(s);
            // border
            g.setPaint(SkinColors.GENERAL_BORDER_COLOR);
            g.draw(s);
        }
    }

    public enum State {
        Disabled, Enabled
    }
}
