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
