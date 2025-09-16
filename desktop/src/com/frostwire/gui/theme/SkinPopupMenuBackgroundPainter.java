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
public final class SkinPopupMenuBackgroundPainter extends AbstractSkinPainter {
    public SkinPopupMenuBackgroundPainter(State state) {
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        paintBackground(g, width, height);
    }

    private void paintBackground(Graphics2D g, int width, int height) {
        int w = width - 1;
        int h = height - 1;
        if (testValid(0, 0, w, h)) {
            Shape s = shapeGenerator.createRectangle(0, 0, w, h);
            g.setPaint(SkinColors.LIGHT_BACKGROUND_COLOR);
            g.fill(s);
            // border
            g.setPaint(SkinColors.GENERAL_BORDER_COLOR);
            g.draw(s);
        }
    }

    public enum State {
        Disabled, Enabled
    }
}
