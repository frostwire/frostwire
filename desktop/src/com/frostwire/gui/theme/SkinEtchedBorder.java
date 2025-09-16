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

import javax.swing.border.Border;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinEtchedBorder implements Border {
    private final Color color;
    private final Color bottomShadeColor;

    public SkinEtchedBorder(Color color) {
        this.color = color;
        this.bottomShadeColor = new Color(255, 255, 255, 150);
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D) g.create();
        //float strokeWidth = SubstanceSizeUtils.getBorderStrokeWidth(SubstanceSizeUtils.getComponentFontSize(c));
        //g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(color);
        //g2d.drawRect(x, y, w - 1, h - 1);
        g2d.drawRoundRect(x, y, width - 1, height - 2, 16, 16);
        g2d.setColor(bottomShadeColor);
        g2d.drawLine(x + 7, height - 1, width - 7, height - 1);
        /*
        g2d.translate(x, y);

        //g2d.setColor(getShadowColor(c));

        // this is to prevent clipping of thick outer borders.
        int delta = (int) Math.floor(strokeWidth / 2.0);

        //g2d.draw(new Rectangle2D.Float(delta, delta, w - delta - 2 * strokeWidth, h - delta - 2 * strokeWidth));
        // g2d.drawRect(0, 0, w - 2, h - 2);

        g2d.setColor(color);
        g2d.draw(new Line2D.Float(strokeWidth, h - 3 * strokeWidth, strokeWidth, strokeWidth));
        // g2d.drawLine(1, h - 3, 1, 1);
        g2d.draw(new Line2D.Float(delta + strokeWidth, delta + strokeWidth, w - delta - 3 * strokeWidth, delta + strokeWidth));
        // g2d.drawLine(1, 1, w - 3, 1);

        g2d.draw(new Line2D.Float(delta, h - delta - strokeWidth, w - delta - strokeWidth, h - delta - strokeWidth));
        // g2d.drawLine(0, h - 1, w - 1, h - 1);
        g2d.draw(new Line2D.Float(w - delta - strokeWidth, h - delta - strokeWidth, w - delta - strokeWidth, delta));
        // g2d.drawLine(w - 1, h - 1, w - 1, 0);
        */
        g2d.dispose();
        // this is a fix for defect 248 - in order to paint the TitledBorder
        // text respecting the AA settings of the display, we have to
        // set rendering hints on the passed Graphics object.
        ///RenderingUtils.installDesktopHints((Graphics2D) g, c);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        float borderStrokeWidth = 2;//SubstanceSizeUtils.getBorderStrokeWidth(SubstanceSizeUtils.getComponentFontSize(c));
        int prefSize = (int) (Math.ceil(2.0 * borderStrokeWidth));
        return new Insets(prefSize, prefSize, prefSize, prefSize);
    }

    @Override
    public boolean isBorderOpaque() {
        return true;
    }
}
