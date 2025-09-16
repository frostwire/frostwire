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
import javax.swing.plaf.synth.SynthPanelUI;
import java.awt.*;
import java.beans.PropertyChangeEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinPanelUI extends SynthPanelUI {
    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        return new SkinPanelUI();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        updateOpaque(c);
    }

    private void updateOpaque(JComponent c) {
        if (Boolean.TRUE.equals(c.getClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND))) {
            c.setOpaque(true);
        } else {
            c.setBackground(SkinColors.TRANSPARENT_COLOR);
            c.setOpaque(false);
        }
    }

    @Override
    public void update(Graphics g, JComponent c) {
        if (c.isOpaque()) {
            if (Boolean.TRUE.equals(c.getClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND))) {
                g.setColor(SkinColors.DARK_BOX_BACKGROUND_COLOR);
                if (c.getBorder() instanceof SkinTitledBorder) {
                    g.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 2, 14, 14);
                } else {
                    g.fillRect(0, 0, c.getWidth(), c.getHeight());
                }
            } else {
                g.setColor(c.getBackground());
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
            }
        } else {
            super.update(g, c);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        super.propertyChange(pce);
        if (ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND.equals(pce.getPropertyName())) {
            if (pce.getSource() instanceof JComponent) {
                updateOpaque((JComponent) pce.getSource());
            }
        }
    }
}
