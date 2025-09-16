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

        if (ThemeMediator.isDarkThemeOn()) {
            // dark mode gradient
            colors = new Color[]{
                    SkinColors.APPLICATION_HEADER_DARK_MODE_GRADIENT_COLOR_TOP,
                    SkinColors.APPLICATION_HEADER_DARK_MODE_GRADIENT_COLOR_BOTTOM
            };
        }

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
