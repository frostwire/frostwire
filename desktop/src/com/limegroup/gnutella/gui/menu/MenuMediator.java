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

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;

/**
 * This class acts as a mediator among all of the various items of the
 * application's menus.
 */
public final class MenuMediator {
    /**
     * Constant handle to the instance of this class for following
     * the singleton pattern.
     */
    private static MenuMediator INSTANCE;

    /*
      We call this so that the menu won't be covered by the SWT Browser.
     */
    static {
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
    }

    /**
     * Constant handle to the `JMenuBar` instance that holds all
     * of the `JMenu` instances.
     */
    private final JMenuBar MENU_BAR = new JMenuBar();

    /**
     * Private constructor that ensures that a `MenuMediator`
     * cannot be constructed from outside this class.  It adds all of
     * the menus.
     */
    private MenuMediator() {
        GUIMediator.setSplashScreenString(I18n.tr("Loading Menus..."));
        /*
          Constant handle to the single `FileMenu` instance for
          the application.
         */
        FileMenu FILE_MENU = new FileMenu();
        addMenu(FILE_MENU);
        /*
          Constant handle to the single `ViewMenu` instance for
          the application.
         */
        Menu VIEW_MENU = new ViewMenu();
        addMenu(VIEW_MENU);
        /*
          Constant handle to the single `ToolsMenu` instance for
          the application.
         */
        Menu TOOLS_MENU = new ToolsMenu();
        addMenu(TOOLS_MENU);
        /*
          Constant handle to the single `HelpMenu` instance for
          the application.
         */
        Menu HELP_MENU = new HelpMenu();
        addMenu(HELP_MENU);
    }

    /**
     * Singleton accessor method for obtaining the `MenuMediator`
     * instance.
     *
     * @return the `MenuMediator` instance
     */
    public static MenuMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new MenuMediator();
        }
        return INSTANCE;
    }

    /**
     * Returns the `JMenuBar` for the application.
     *
     * @return the application's `JMenuBar` instance
     */
    public JMenuBar getMenuBar() {
        return MENU_BAR;
    }

    /**
     * Adds a `Menu` to the next position on the menu bar.
     *
     * @param menu to the `Menu` instance that allows access to
     *             its wrapped `JMenu` instance
     */
    private void addMenu(Menu menu) {
        MENU_BAR.add(menu.getMenu());
    }
}
