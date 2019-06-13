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
