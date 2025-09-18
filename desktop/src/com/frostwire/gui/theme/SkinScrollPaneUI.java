/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.synth.SynthScrollPaneUI;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinScrollPaneUI extends SynthScrollPaneUI {
    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        return new SkinScrollPaneUI();
    }

    @Override
    protected void installDefaults(JScrollPane scrollpane) {
        super.installDefaults(scrollpane);
        JScrollBar scrollBar = scrollpane.getVerticalScrollBar();
        if (scrollBar != null) {
            scrollBar.setPreferredSize(new Dimension(18, 100));
        }
    }
}
