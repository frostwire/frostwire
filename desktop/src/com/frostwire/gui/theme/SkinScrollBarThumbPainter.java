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

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinScrollBarThumbPainter extends AbstractSkinPainter {
    private final State state;

    public SkinScrollBarThumbPainter(State state) {
        this.state = state;
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        if (testValid(0, 0, width - 2, height - 2)) {
            Shape s1 = shapeGenerator.createRectangle(0, 0, width, height);
            g.setColor(SkinColors.SCROLL_THUMB_BORDER_COLOR);
            g.fill(s1);
            Shape s2 = shapeGenerator.createRectangle(1, 1, width - 2, height - 1);
            g.setPaint(getScrollBarThumbPaint(s2));
            g.fill(s2);
        }
    }

    private Paint getScrollBarThumbPaint(Shape s) {
        switch (state) {
            case Enabled:
                return createVerticalGradient(s, SkinColors.SCROLL_THUMB_ENABLED_COLORS);
            case MouseOver:
                return createVerticalGradient(s, SkinColors.SCROLL_THUMB_MOUSEOVER_COLORS);
            case Pressed:
                //gubatron: this one never happens
                return createVerticalGradient(s, SkinColors.SCROLL_THUMB_PRESSED_COLORS);
            default:
                throw new IllegalArgumentException("Not supported state");
        }
    }

    public enum State {
        Enabled, MouseOver, Pressed
    }
}
