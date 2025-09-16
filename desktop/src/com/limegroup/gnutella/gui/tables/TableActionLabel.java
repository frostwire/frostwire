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