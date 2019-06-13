/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, 2013, FrostWire(R). All rights reserved.
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

import com.apple.laf.AquaMenuItemUI;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.synth.SynthRadioButtonMenuItemUI;

/**
 * @author gubatron
 * @author aldenml
 */
public class SkinRadioButtonMenuItemUI extends SynthRadioButtonMenuItemUI {
    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        if (OSUtils.isMacOSX() && !(comp instanceof SkinRadioButtonMenuItem)) {
            return AquaMenuItemUI.createUI(comp);
        } else {
            return new SkinRadioButtonMenuItemUI();
        }
    }
}
