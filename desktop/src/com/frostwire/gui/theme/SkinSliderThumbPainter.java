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
public final class SkinSliderThumbPainter extends AbstractSkinPainter {
    private final Image image;

    public SkinSliderThumbPainter(State state) {
        this.image = getImage(getImageName(state));
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        g.drawImage(image, 0, 0, null);
    }

    private String getImageName(State state) {
        String imageName = null;
        switch (state) {
            case Disabled:
                imageName = "slider_inactive";
                break;
            case Focused:
            case Enabled:
                imageName = "slider_active";
                break;
            case FocusedMouseOver:
            case MouseOver:
            case FocusedPressed:
            case Pressed:
                imageName = "slider_pressed";
                break;
            default:
                throw new RuntimeException("Not supported state");
        }
        return imageName;
    }

    public enum State {
        Disabled, Enabled, FocusedMouseOver, FocusedPressed, Focused, MouseOver, Pressed
    }
}
