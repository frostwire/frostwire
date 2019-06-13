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
public final class SkinScrollBarButtonPainter extends AbstractSkinPainter {
    private final State state;

    public SkinScrollBarButtonPainter(State state) {
        this.state = state;
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        paintBox(g, width, height);
        paintArrowButton(g, width / 2.0 - 4, height / 2.0 - 4);
    }

    private void paintBox(Graphics2D g, int width, int height) {
        if (testValid(0, 0, width - 1, height)) {
            Shape s = shapeGenerator.createRectangle(0, 0, width - 1, height);
            g.setPaint(getScrollBarButtonBoxPaint(s));
            g.fill(s);
            g.setPaint(getScrollBarButtonBoxBorderColor());
            g.draw(s);
        }
    }

    private void paintArrowButton(Graphics2D g, double x, double y) {
        Shape s = shapeGenerator.createArrowLeft(x, y, 7, 8);
        g.setPaint(getScrollBarButtonArrowColor());
        g.fill(s);
    }

    private Color getScrollBarButtonArrowColor() {
        switch (state) {
            case Disabled:
                return SkinColors.SCROLL_BUTTON_ARROW_DISABLED_COLOR;
            case Enabled:
            case Pressed:
            case MouseOver:
                return SkinColors.SCROLL_BUTTON_ARROW_ENABLED_COLOR;
            default:
                throw new IllegalArgumentException("Not supported state");
        }
    }

    private Paint getScrollBarButtonBoxPaint(Shape s) {
        switch (state) {
            case Disabled:
                return createVerticalGradient(s, SkinColors.SCROLL_BUTTON_ARROW_BOX_DISABLED_COLORS);
            case Enabled:
                return createVerticalGradient(s, SkinColors.SCROLL_BUTTON_ARROW_BOX_ENABLED_COLORS);
            case MouseOver:
                return createVerticalGradient(s, SkinColors.SCROLL_BUTTON_ARROW_BOX_MOUSEOVER_COLORS);
            case Pressed:
                return createVerticalGradient(s, SkinColors.SCROLL_BUTTON_ARROW_BOX_PRESSED_COLORS);
            default:
                throw new IllegalArgumentException("Not supported state");
        }
    }

    private Paint getScrollBarButtonBoxBorderColor() {
        switch (state) {
            case Disabled:
            case Pressed:
            case MouseOver:
            case Enabled:
                return SkinColors.SCROLL_BUTTON_ARROW_BOX_BORDER_COLOR;
            default:
                throw new IllegalArgumentException("Not supported state");
        }
    }

    public enum State {
        Disabled, Enabled, MouseOver, Pressed
    }
}
