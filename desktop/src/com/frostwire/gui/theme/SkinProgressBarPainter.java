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
public final class SkinProgressBarPainter extends AbstractSkinPainter {
    private final State state;
    private final int padding;

    public SkinProgressBarPainter(State state, int padding) {
        this.state = state;
        this.padding = padding;
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        switch (state) {
            case Enabled:
            case Disabled:
                paintBar(g, c, width, height);
                break;
            case EnabledIndeterminate:
            case DisabledIndeterminate:
                paintIndeterminateBar(g, width, height);
                break;
        }
    }

    private void paintBar(Graphics2D g, JComponent c, int width, int height) {
        int d = padding - 1;
        int d2 = 2 * d;
        width = width - d2;
        height = height - d2 - 1;
        if (testValid(d, d, width, height)) {
            Shape s = shapeGenerator.createRectangle(d, d, width, height);
            g.setPaint(getProgressBarPaint(s));
            g.fill(s);
            g.setPaint(getProgressBarBorderPaint());
            g.draw(s);
        }
    }

    private void paintIndeterminateBar(Graphics2D g, int width, int height) {
        if (testValid(0, 0, width, height)) {
            Shape s = shapeGenerator.createProgressBarIndeterminatePattern(0, 0, width, height);
            g.setPaint(getProgressBarIndeterminatePaint(s));
            g.fill(s);
        }
    }

    private Paint getProgressBarPaint(Shape s) {
        Color[] colors = state == State.Enabled ? SkinColors.PROGRESS_BAR_ENABLED_GRADIENT_COLORS : SkinColors.PROGRESS_BAR_DISABLED_GRADIENT_COLORS;
        return createVerticalGradient(s, colors);
    }

    private Paint getProgressBarBorderPaint() {
        return state == State.Enabled ? SkinColors.PROGRESS_BAR_ENABLED_BORDER_COLOR : SkinColors.PROGRESS_BAR_DISABLED_BORDER_COLOR;
    }

    private Paint getProgressBarIndeterminatePaint(Shape s) {
        Color[] colors = state == State.EnabledIndeterminate ? SkinColors.PROGRESS_BAR_ENABLED_INDERTERMINATE_GRADIENT_COLORS : SkinColors.PROGRESS_BAR_DISABLED_INDERTERMINATE_GRADIENT_COLORS;
        return createVerticalGradient(s, colors);
    }

    public enum State {
        Enabled, Disabled, EnabledIndeterminate, DisabledIndeterminate
    }
}
