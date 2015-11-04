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

package com.limegroup.gnutella.gui.menu;

import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;

/**
 * This class acts as a mediator among all of the various items of the 
 * application's menus.
 */
public final class MenuMediator {

    /**
     * We call this so that the menu won't be covered by the SWT Browser.
     */
    static {
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
    }

    /**
     * Constant handle to the instance of this class for following 
     * the singleton pattern.
     */
    private static MenuMediator INSTANCE;

    /**
     * Constant handle to the <tt>JMenuBar</tt> instance that holds all
     * of the <tt>JMenu</tt> instances.
     */
    private final JMenuBar MENU_BAR = new JMenuBar();

    /**
     * Constant handle to the single <tt>FileMenu</tt> instance for
     * the application.
     */
    private final FileMenu FILE_MENU = new FileMenu();

    /**
     * Constant handle to the single <tt>ToolsMenu</tt> instance for
     * the application.
     */
    private final Menu TOOLS_MENU = new ToolsMenu();

    /**
     * Constant handle to the single <tt>HelpMenu</tt> instance for
     * the application.
     */
    private final Menu HELP_MENU = new HelpMenu();

    /**
     * Constant handle to the single <tt>ViewMenu</tt> instance for
     * the application.
     */
    private final Menu VIEW_MENU = new ViewMenu("VIEW");

    /**
     * Singleton accessor method for obtaining the <tt>MenuMediator</tt>
     * instance.
     *
     * @return the <tt>MenuMediator</tt> instance
     */
    public static final MenuMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new MenuMediator();
        }
        return INSTANCE;
    }

    /**
     * Private constructor that ensures that a <tt>MenuMediator</tt> 
     * cannot be constructed from outside this class.  It adds all of 
     * the menus.
     */
    private MenuMediator() {
        GUIMediator.setSplashScreenString(I18n.tr("Loading Menus..."));

        addMenu(FILE_MENU);
        addMenu(VIEW_MENU);
        addMenu(TOOLS_MENU);
        addMenu(HELP_MENU);
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

    /**
     * Returns the height of the main menu bar.
     *
     * @return the height of the main menu bar
     */
    public int getMenuBarHeight() {
        return MENU_BAR.getHeight();
    }
}
