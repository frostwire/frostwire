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
     * Constant handle to the <tt>JMenuBar</tt> instance that holds all
     * of the <tt>JMenu</tt> instances.
     */
    private final JMenuBar MENU_BAR = new JMenuBar();

    /**
     * Private constructor that ensures that a <tt>MenuMediator</tt>
     * cannot be constructed from outside this class.  It adds all of
     * the menus.
     */
    private MenuMediator() {
        GUIMediator.setSplashScreenString(I18n.tr("Loading Menus..."));
        /*
          Constant handle to the single <tt>FileMenu</tt> instance for
          the application.
         */
        FileMenu FILE_MENU = new FileMenu();
        addMenu(FILE_MENU);
        /*
          Constant handle to the single <tt>ViewMenu</tt> instance for
          the application.
         */
        Menu VIEW_MENU = new ViewMenu();
        addMenu(VIEW_MENU);
        /*
          Constant handle to the single <tt>ToolsMenu</tt> instance for
          the application.
         */
        Menu TOOLS_MENU = new ToolsMenu();
        addMenu(TOOLS_MENU);
        /*
          Constant handle to the single <tt>HelpMenu</tt> instance for
          the application.
         */
        Menu HELP_MENU = new HelpMenu();
        addMenu(HELP_MENU);
    }

    /**
     * Singleton accessor method for obtaining the <tt>MenuMediator</tt>
     * instance.
     *
     * @return the <tt>MenuMediator</tt> instance
     */
    public static MenuMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new MenuMediator();
        }
        return INSTANCE;
    }

    /**
     * Returns the <tt>JMenuBar</tt> for the application.
     *
     * @return the application's <tt>JMenuBar</tt> instance
     */
    public JMenuBar getMenuBar() {
        return MENU_BAR;
    }

    /**
     * Adds a <tt>Menu</tt> to the next position on the menu bar.
     *
     * @param menu to the <tt>Menu</tt> instance that allows access to
     *             its wrapped <tt>JMenu</tt> instance
     */
    private void addMenu(Menu menu) {
        MENU_BAR.add(menu.getMenu());
    }
}
