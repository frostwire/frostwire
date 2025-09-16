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

package com.limegroup.gnutella.gui.menu;

import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.actions.ToggleSettingAction;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.ActionEvent;

/**
 * Provides a skeletal implementation of the <tt>Menu</tt> interface to
 * minimize the necessary work in classes that extend <tt>AbstractMenu</tt>.
 */
abstract class AbstractMenu implements Menu {
    /**
     * Constant handle to the <tt>JMenu</tt> instance for this
     * <tt>AbstractMenu</tt>.
     */
    final JMenu MENU;

    /**
     * Creates a new <tt>AbstractMenu</tt>, using the <tt>key</tt>
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
     * Returns the <tt>JMenu</tt> instance for this <tt>AbstractMenu</tt>.
     *
     * @return the <tt>JMenu</tt> instance for this <tt>AbstractMenu</tt>
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
     * Adds a separator to the <tt>JMenu</tt> instance.
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