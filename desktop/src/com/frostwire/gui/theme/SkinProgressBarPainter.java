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
