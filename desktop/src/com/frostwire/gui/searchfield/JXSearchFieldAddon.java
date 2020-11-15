/*
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
