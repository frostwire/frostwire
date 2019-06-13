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

package com.limegroup.gnutella.gui.init;

import javax.swing.*;
import java.awt.*;

/**
 * This class serves two purposes.  First, it is a JPanel that
 * contains the body of a LimeWire setup window.  Second, it
 * serves as a proxy for the underlying SetupWindow object that
 * that handles the actual drawing.
 */
final class SetupWindowHolder extends JPanel {
    /**
     * The <tt>CardLayout</tt> instance for the setup windows.
     */
    private final CardLayout CARD_LAYOUT = new CardLayout();

    /**
     * Sets the <tt>CardLayout</tt> for the setup windows.
     */
    SetupWindowHolder() {
        setLayout(CARD_LAYOUT);
    }

    /**
     * Adds the specified window to the CardLayout based on its title.
     *
     * @param window the <tt>SetupWindow</tt> to add
     */
    void add(SetupWindow window) {
        add(window, window.getKey());
    }

    /**
     * Shows the window specified by its title.
     *
     * @param key the unique key of the <tt>Component</tt> to show
     */
    void show(String key) {
        CARD_LAYOUT.show(this, key);
    }
}
