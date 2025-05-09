/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
 *
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
        add(createDonateLabel());
        //MigLayout lesson: Using px explicitly as the unit does make a big difference.
        int bitcoinWidth = (OSUtils.isLinux()) ? 52 : 40;
        int usdErc20Width = (OSUtils.isLinux()) ? 78 : 68;
        add(new DonationButton("$5", "https://www.frostwire.com/?id=donate&amt=5", I18n.tr("Support FrostWire development with a $1 donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("$10", "https://www.frostwire.com/?id=donate&amt=10", I18n.tr("Support FrostWire development with a $1 donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("$x", "https://www.frostwire.com/?id=donate&amt=open", I18n.tr("Support FrostWire development with a $1 donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("BTC", "https://www.frostwire.com/bitcoin", I18n.tr("Support FrostWire development with a Bitcoin donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("ETH", "https://www.frostwire.com/ethereum", I18n.tr("Support FrostWire development with an Ether donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("USD*/DAI", "https://www.frostwire.com/ethereum", I18n.tr("Support FrostWire development with a Dash donation")), "w " + usdErc20Width + "px!, h 18px!");
    }

    private JLabel createDonateLabel() {
        Font labelFont = getFont().deriveFont(Font.BOLD);
        JLabel donateLabel = new JLabel(I18n.tr("Donate") + ":");
        donateLabel.setForeground(ThemeMediator.isDarkThemeOn() ? FONT_COLOR_DARK_THEME : FONT_COLOR);
        donateLabel.setFont(labelFont);
        return donateLabel;
    }

    private class DonationButton extends JButton {
        DonationButton(String text, String donationURL, String tipText) {
            initComponent(text, donationURL, tipText);
        }

        private void initComponent(String text, final String donationURL, String tipText) {
            boolean isDarkTheme = ThemeMediator.isDarkThemeOn();
            Font buttonFont = new Font("Dialog", Font.BOLD, 12);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(isDarkTheme ? BorderFactory.createLineBorder(Color.BLACK) : null);
            setContentAreaFilled(false);
            setOpaque(false);
            setFont(buttonFont);
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
