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

package com.limegroup.gnutella.gui.util;

import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * Utilities relating to JPopupMenu & JMenus.
 */
public class PopupUtils {
    /**
     * Adds a menu item defined by the ActionListener & String to the JPopupMenu, enabled or not.
     */
    public static void addMenuItem(String s, ActionListener l, JPopupMenu m, boolean enable) {
        addMenuItem(s, l, m, enable, -1);
    }

    /**
     * Adds a menu item defined by the ActionListener & String to the JPopupMenu, enabled or not at the given index.
     */
    public static void addMenuItem(String s, ActionListener l, JPopupMenu m, boolean enable, int idx) {
        JMenuItem item = m instanceof SkinPopupMenu ? new SkinMenuItem(s) : new JMenuItem(s);
        item.addActionListener(l);
        item.setEnabled(enable);
        m.add(item, idx);
    }
}
