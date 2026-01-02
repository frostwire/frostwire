/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui;

import com.frostwire.gui.theme.ThemeMediator;
import net.miginfocom.swing.MigLayout;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
class DonationButtons extends JPanel {
    private static final Color FONT_COLOR = new Color(0x1F3545);
    private static final Color FONT_COLOR_DARK_THEME = new Color(0xFFFFFF);

    DonationButtons() {
        setLayout(new MigLayout("insets 0, nogrid, ltr, gapx 6", "", "[align center]"));
        // Single simplified donate button
        int donateButtonWidth = (OSUtils.isLinux()) ? 70 : 60;
        add(new DonationButton("Donate", "https://www.frostwire.com/give", I18n.tr("Support FrostWire development with a donation")), "w " + donateButtonWidth + "px!, h 18px!");
    }

    private class DonationButton extends JButton {
        DonationButton(String text, String donationURL, String tipText) {
            initComponent(text, donationURL, tipText);
        }

        private void initComponent(String text, final String donationURL, String tipText) {
            boolean isDarkTheme = ThemeMediator.isDarkLafThemeOn();
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(isDarkTheme ? BorderFactory.createLineBorder(Color.BLACK) : null);
            setContentAreaFilled(false);
            setOpaque(false);
            // Defer font loading to avoid EDT violation
            SwingUtilities.invokeLater(() -> setFont(new Font("Dialog", Font.BOLD, 12)));
            setForeground(isDarkTheme ? FONT_COLOR_DARK_THEME : FONT_COLOR);
            setBackground(isDarkTheme ? Color.darkGray : new Color(0xedf1f4));
            setText(text);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.CENTER);
            setToolTipText(tipText);
            addActionListener(e -> GUIMediator.openURL(donationURL));
        }

        @Override
        protected void paintComponent(Graphics g) {
            // TODO Move this code to a UI if necessary, for now KIFSS
            g.setColor(getBackground());
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 2, 2);
            g.setColor(new Color(0xe4e8ea));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 2, 2);
            super.paintComponent(g);
        }
    }
}
