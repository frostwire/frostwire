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
public final class SkinCheckBoxIconPainter extends AbstractSkinPainter {
    private final Image image;

    public SkinCheckBoxIconPainter(State state) {
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
                imageName = "checkbox_checked_disabled";
                break;
            case Disabled:
                imageName = "checkbox_unchecked_disabled";
                break;
            case Enabled:
            case FocusedMouseOver:
            case Focused:
            case MouseOver:
                imageName = "checkbox_unchecked_active";
                break;
            case FocusedMouseOverSelected:
            case FocusedPressedSelected:
            case FocusedPressed:
            case FocusedSelected:
            case MouseOverSelected:
            case PressedSelected:
            case Pressed:
            case Selected:
                imageName = "checkbox_checked_active";
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
