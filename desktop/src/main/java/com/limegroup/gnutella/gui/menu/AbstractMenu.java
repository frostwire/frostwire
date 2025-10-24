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

package com.limegroup.gnutella.gui.menu;

import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.actions.ToggleSettingAction;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.ActionEvent;

/**
 * Provides a skeletal implementation of the `Menu` interface to
 * minimize the necessary work in classes that extend `AbstractMenu`.
 */
abstract class AbstractMenu implements Menu {
    /**
     * Constant handle to the `JMenu` instance for this
     * `AbstractMenu`.
     */
    final JMenu MENU;

    /**
     * Creates a new `AbstractMenu`, using the `key`
     * argument for setting the locale-specific title and
     * accessibility text.
     *
     */
    AbstractMenu(String name) {
        // using an action here to get the mnemonic parsed
        MENU = new JMenu(new MenuAction(name));
        MENU.addMenuListener(new RefreshMenuListener());
    }

    /**
     * Returns the `JMenu` instance for this `AbstractMenu`.
     *
     * @return the `JMenu` instance for this `AbstractMenu`
     */
    public JMenu getMenu() {
        return MENU;
    }

    @SuppressWarnings("UnusedReturnValue")
    JMenuItem addMenuItem(Action action) {
        return addMenuItem(action, null);
    }

    JMenuItem addMenuItem(Action action, KeyStroke acceleratorKeyStroke) {
        JMenuItem item = new JMenuItem(action);
        if (acceleratorKeyStroke != null) {
            item.setAccelerator(acceleratorKeyStroke);
        }
        MENU.add(item);
        return item;
    }

    private JMenuItem addToggleMenuItem(Action action, boolean selected) {
        JMenuItem item;
        if (OSUtils.isMacOSX()) {
            item = new JRadioButtonMenuItem(action);
        } else {
            item = new JCheckBoxMenuItem(action);
        }
        item.setSelected(selected);
        MENU.add(item);
        return item;
    }

    @SuppressWarnings("UnusedReturnValue")
    JMenuItem addToggleMenuItem(ToggleSettingAction action) {
        return addToggleMenuItem(action, action.getSetting().getValue());
    }

    /**
     * Adds a separator to the `JMenu` instance.
     */
    void addSeparator() {
        MENU.addSeparator();
    }

    void refresh() {
    }

    private static class MenuAction extends AbstractAction {
        private static final long serialVersionUID = -4311768902578846258L;

        MenuAction(String name) {
            super(name);
        }

        public void actionPerformed(ActionEvent e) {
        }
    }

    private class RefreshMenuListener implements MenuListener {
        @Override
        public void menuSelected(MenuEvent e) {
            refresh();
        }

        @Override
        public void menuDeselected(MenuEvent e) {
        }

        @Override
        public void menuCanceled(MenuEvent e) {
        }
    }
}