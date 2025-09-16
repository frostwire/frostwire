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
public final class SkinTabbedPaneTabAreaBackgroundPainter extends AbstractSkinPainter {
    private final State state;

    public SkinTabbedPaneTabAreaBackgroundPainter(State state) {
        this.state = state;
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        switch (state) {
            case Disabled:
            case EnableMouseOver:
            case EnablePressed:
            case Enable:
                paintBorder(g, width, height);
                break;
        }
    }

    private void paintBorder(Graphics2D g, int width, int height) {
        g.setPaint(SkinColors.GENERAL_BORDER_COLOR);
        int y = height - 1;
        g.drawLine(0, y, width, y);
    }

    public enum State {
        Disabled, EnableMouseOver, EnablePressed, Enable
    }
}
