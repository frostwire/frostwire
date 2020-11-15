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

package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * This class contains the logo and the searching icon for the application.
 */
final class LogoPanel extends BoxPanel {
    /**
     * Constructs a new panel containing the logo and the search icon.
     */
    LogoPanel() {
        super(BoxPanel.X_AXIS);
        setupUI();
    }

    private void setupUI() {
        JLabel labelLogo = new JLabel();
        ImageIcon logoIcon = GUIMediator.getThemeImage("logo_header");
        labelLogo.setIcon(logoIcon);
        labelLogo.setSize(logoIcon.getIconWidth(), logoIcon.getIconHeight());
        GUIUtils.setOpaque(false, this);
        add(Box.createHorizontalGlue());
        add(labelLogo);
        add(Box.createHorizontalGlue());
        this.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent me) {
                GUIMediator.openURL("http://www.frostwire.com/?from=header");
            }

            public void mouseEntered(MouseEvent me) {
                setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        });
    }
}
