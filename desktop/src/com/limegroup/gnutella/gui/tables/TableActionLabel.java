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

package com.limegroup.gnutella.gui.tables;

import com.frostwire.gui.AlphaIcon;

import javax.swing.*;

/**
 * Use these Label buttons next time you need to add a button on a custom
 * cell renderer. They will show solid on mouse over and semi transparent on mouse out.
 * <p>
 * Note: when we invoke updateAction and we pass the 'enabled' state for the label
 * we don't use JLabel's "setEnabled" as it makes the label look too dark,
 * so we update another internal variable to represent the enablement state.
 * Use isActionEnabled() on your mouse listener implementation to know wether to act or not.
 * (Do not use isEnabled())
 *
 * @author gubatron
 */
public class TableActionLabel extends JLabel {
    private final static float BUTTONS_TRANSPARENCY = 0.85f;
    private final ImageIcon enabledSolid;
    private final AlphaIcon enabledTransparent;
    private final ImageIcon disabledSolid;
    private final AlphaIcon disabledTransparent;
    private boolean actionEnabled;

    public TableActionLabel(ImageIcon enabledSolidIcon, ImageIcon disabledSolidIcon) {
        enabledSolid = enabledSolidIcon;
        enabledTransparent = new AlphaIcon(enabledSolid, BUTTONS_TRANSPARENCY);
        disabledSolid = disabledSolidIcon;
        disabledTransparent = new AlphaIcon(disabledSolid, BUTTONS_TRANSPARENCY);
    }

    public void updateActionIcon(boolean enableAction, boolean showSolid) {
        actionEnabled = enableAction;
        Icon icon = null;
        if (enableAction) {
            if (showSolid) {
                icon = enabledSolid;
            } else {
                icon = enabledTransparent;
            }
        } else {
            if (showSolid) {
                icon = disabledSolid;
            } else {
                icon = disabledTransparent;
            }
        }
        setIcon(icon);
    }

    public boolean isActionEnabled() {
        return actionEnabled;
    }
}