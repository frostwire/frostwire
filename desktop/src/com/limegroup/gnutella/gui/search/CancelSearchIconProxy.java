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

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.*;
import java.util.MissingResourceException;

/**
 * This class acts as a wrapper around the "kill" icon displayed in the
 * search tabs.
 */
final class CancelSearchIconProxy implements Icon {
    private static final int PLAIN = 0;
    private static final int SELECTED = 1;
    private static final int ARMED = 2;
    private static Icon PLAIN_ICON;
    private static Icon SELECTED_ICON;
    private static Icon ARMED_ICON;
    /**
     * The style of this icon.
     */
    private final int style;
    /**
     * The <tt>ImageIcon</tt> for our cancel image.
     */
    private Icon _cancelIcon;
    /**
     * The width of the icon in pixels.
     */
    private int _width;
    /**
     * The height of the icon in pixels.
     */
    private int _height;
    /**
     * The x position of the icon within its tab.
     */
    private int _x;
    /**
     * The y position of the icon within its tab.
     */
    private int _y;

    /**
     * the constructor loads the image icon and stores the location
     * and dimensions.
     */
    private CancelSearchIconProxy(int style) {
        this.style = style;
        setIcon();
    }

    static CancelSearchIconProxy createPlain() {
        return new CancelSearchIconProxy(PLAIN);
    }

    static CancelSearchIconProxy createSelected() {
        return new CancelSearchIconProxy(SELECTED);
    }

    // resets the cached icons for each kind of icon
    static void updateTheme() {
        GUIMediator.safeInvokeAndWait(() -> {
//                if (ThemeSettings.isWindowsTheme() && WindowsXPIcon.isAvailable()) {
//                    try {
//                        PLAIN_ICON = new WindowsXPIcon(PLAIN);
//                        SELECTED_ICON = new WindowsXPIcon(SELECTED);
//                        ARMED_ICON = new WindowsXPIcon(ARMED);
//                        return;
//                    } catch (IllegalArgumentException iae) {
//                        // couldn't create image to resize
//                    } catch (NullPointerException npe) {
//                        // internal windows plaf error
//                    } catch (ArithmeticException ae) {
//                        // internal windows error (see https://www.limewire.org/jira/browse/GUI-8)
//                    }
//                    // if construction failed, fall through...
//                }
            PLAIN_ICON = GUIMediator.getThemeImage("kill");
            try {
                SELECTED_ICON = GUIMediator.getThemeImage("kill_on");
            } catch (MissingResourceException mre) {
                SELECTED_ICON = PLAIN_ICON;
            }
            ARMED_ICON = SELECTED_ICON;
        });
    }

    /**
     * Sets the appropriate icon.
     */
    private void setIcon() {
        switch (style) {
            case ARMED:
                _cancelIcon = ARMED_ICON;
                break;
            case SELECTED:
                _cancelIcon = SELECTED_ICON;
                break;
            case PLAIN:
                _cancelIcon = PLAIN_ICON;
                break;
        }
        _width = _cancelIcon.getIconWidth();
        _height = _cancelIcon.getIconHeight();
        _x = 0;
        _y = 0;
    }

    /**
     * implements Icon interface.
     * Gets the width of the icon.
     *
     * @return the width in pixels of this icon
     */
    public int getIconWidth() {
        return _width;
    }

    /**
     * implements Icon interface.
     * Gets the height of the icon.
     *
     * @return the height in pixels of this icon
     */
    public int getIconHeight() {
        return _height;
    }

    /**
     * implements Icon interface.
     * forwards the call to the proxied Icon object and stores the
     * x and y coordinates of the icon.
     */
    public void paintIcon(Component c, Graphics g, int x, int y) {
        _x = x;
        _y = y;
        _cancelIcon.paintIcon(c, g, x, y);
    }

    /**
     * Determines whether or not a click at the given x, y position
     * is a "hit" on the kill search icon.
     *
     * @param x the x location of the mouse event
     * @param y the y location of the mouse event
     * @return <tt>true</tt> if the mouse event occurred within the
     * bounding rectangle of the icon, <tt>false</tt> otherwise.
     */
    boolean shouldKill(int x, int y) {
        int xMax = _x + _width;
        int yMax = _y + _height;
        if (!((x >= _x) && (x <= xMax)))
            return false;
        return (y >= _y) && (y <= yMax);
    }
}
