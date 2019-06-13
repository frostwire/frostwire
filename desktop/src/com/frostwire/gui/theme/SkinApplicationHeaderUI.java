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
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPanelUI;
import java.awt.*;
import java.awt.geom.Point2D;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinApplicationHeaderUI extends BasicPanelUI {
    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        return new SkinApplicationHeaderUI();
    }

    @Override
    public void update(Graphics g, JComponent c) {
        Point2D start = new Point2D.Float(0, 0);
        Point2D end = new Point2D.Float(0, c.getHeight());
        float[] dist = {0.0f, 1.0f};
        Color[] colors = SkinColors.APPLICATION_HEADER_GRADIENT_COLORS;
        LinearGradientPaint paint = new LinearGradientPaint(start, end, dist, colors);
        Graphics2D graphics = (Graphics2D) g.create();
        // optimization - do not call fillRect on graphics
        // with anti-alias turned on
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setPaint(paint);
        graphics.fillRect(0, 0, c.getWidth(), c.getHeight());
        graphics.dispose();
        super.paint(g, c);
    }
}
