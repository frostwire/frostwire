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

package com.frostwire.gui.searchfield;

import com.frostwire.gui.searchfield.JXSearchField.LayoutStyle;
import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import javax.swing.plaf.IconUIResource;
import javax.swing.plaf.InsetsUIResource;

class JXSearchFieldAddon {
    public static final String SEARCH_FIELD_SOURCE = "searchField";

    public JXSearchFieldAddon() {
    }

    public void addDefaults() {
        UIManager.put("SearchField.layoutStyle", LayoutStyle.MAC);
        UIManager.put("SearchField.icon", getIcon("search.png"));
        UIManager.put("SearchField.rolloverIcon", getIcon("search.png"));
        UIManager.put("SearchField.pressedIcon", getIcon("search.png"));
        UIManager.put("SearchField.popupIcon", getIcon("search_popup.png"));
        UIManager.put("SearchField.popupRolloverIcon", getIcon("search_popup.png"));
        UIManager.put("SearchField.popupPressedIcon", getIcon("search_popup.png"));
        UIManager.put("SearchField.clearIcon", getIcon("clear.png"));
        UIManager.put("SearchField.clearRolloverIcon", getIcon("clear_rollover.png"));
        UIManager.put("SearchField.clearPressedIcon", getIcon("clear_pressed.png"));
        UIManager.put("SearchField.buttonMargin", new InsetsUIResource(0, 0, 0, 0));
        UIManager.put("SearchField.popupSource", SEARCH_FIELD_SOURCE);
    }

    private IconUIResource getIcon(String iconName) {
        ImageIcon icon = GUIMediator.getThemeImage("searchfield_" + iconName);
        return icon != null ? new IconUIResource(icon) : null;
    }
}
