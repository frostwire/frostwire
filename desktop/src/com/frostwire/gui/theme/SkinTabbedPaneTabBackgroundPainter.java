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
public final class SkinTabbedPaneTabBackgroundPainter extends AbstractSkinPainter {
    private final State state;

    public SkinTabbedPaneTabBackgroundPainter(State state) {
        this.state = state;
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        switch (state) {
            case FocusedMouseOverSelected:
            case FocusedPressedSelected:
            case FocusedSelected:
            case MouseOverSelected:
            case PressedSelected:
            case Selected:
                paintSelectedTab(g, width, height);
                break;
            default:
                paintDefaultTab(g, width, height);
                break;
        }
    }

    private void paintSelectedTab(Graphics2D g, int width, int height) {
        if (testValid(0, 0, width, height)) {
            Shape s = shapeGenerator.createRectangle(0, 0, width, height);
            g.setPaint(SkinColors.LIGHT_BACKGROUND_COLOR);
            g.fill(s);
            paintBorder(g, width, height);
        }
    }

    private void paintDefaultTab(Graphics2D g, int width, int height) {
        int w = width - 4;
        if (testValid(0, 0, w, height)) {
            Shape s = shapeGenerator.createRectangle(0, 0, w, height);
            g.setPaint(SkinColors.GENERAL_BORDER_COLOR);
            g.fill(s);
            paintBorder(g, width, height);
        }
    }

    private void paintBorder(Graphics2D g, int width, int height) {
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        path.reset();
        int w = width - 4;
        int h = height - 1;
        path.moveTo(0, h);
        path.lineTo(0, 0);
        path.lineTo(w, 0);
        path.lineTo(w, h);
        path.lineTo(width, h);
        g.setPaint(SkinColors.GENERAL_BORDER_COLOR);
        g.draw(path);
    }

    public enum State {
        DisabledSelected, Disabled, EnabledMouseOver, EnabledPressed, Enabled, FocusedMouseOverSelected, FocusedPressedSelected, FocusedSelected, MouseOverSelected, PressedSelected, Selected
    }
}
