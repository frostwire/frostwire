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
public final class SkinRadioButtonIconPainter extends AbstractSkinPainter {
    private final Image image;

    public SkinRadioButtonIconPainter(State state) {
        this.image = getImage(getImageName(state));
    }

    @Override
    protected void doPaint(Graphics2D g, JComponent c, int width, int height, Object[] extendedCacheKeys) {
        g.drawImage(image, 0, 0, null);
    }

    private String getImageName(State state) {
        String imageName = null;
        switch (state) {
            case DisabledSelected:
                imageName = "radio_btn_checked_disabled";
                break;
            case Disabled:
                imageName = "radio_btn_unchecked_disabled";
                break;
            case Enabled:
            case FocusedMouseOver:
            case Focused:
            case MouseOver:
                imageName = "radio_btn_unchecked_active";
                break;
            case FocusedMouseOverSelected:
            case FocusedPressedSelected:
            case FocusedPressed:
            case FocusedSelected:
            case MouseOverSelected:
            case PressedSelected:
            case Pressed:
            case Selected:
                imageName = "radio_btn_checked_active";
                break;
            default:
                throw new RuntimeException("Not supported state");
        }
        return imageName;
    }

    public enum State {
        DisabledSelected, Disabled, Enabled, FocusedMouseOverSelected, FocusedMouseOver, FocusedPressedSelected, FocusedPressed, FocusedSelected, Focused, MouseOverSelected, MouseOver, PressedSelected, Pressed, Selected
    }
}
