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

import javax.swing.*;

/**
 * Defines the minimal necessary methods for menus in the main application
 * menu bar.
 */
interface Menu {
    /**
     * Returns the <tt>JMenu</tt> instance for this <tt>Menu</tt>.
     *
     * @return the <tt>JMenu</tt> instance for this <tt>Menu</tt>
     */
    JMenu getMenu();
}
