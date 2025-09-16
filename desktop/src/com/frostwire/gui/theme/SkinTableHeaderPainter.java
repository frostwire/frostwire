/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
