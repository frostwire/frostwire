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
