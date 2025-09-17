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
        donateLabel.setForeground(ThemeMediator.isDarkLafThemeOn() ? FONT_COLOR_DARK_THEME : FONT_COLOR);
        donateLabel.setFont(labelFont);
        return donateLabel;
    }

    private class DonationButton extends JButton {
        DonationButton(String text, String donationURL, String tipText) {
            initComponent(text, donationURL, tipText);
        }

        private void initComponent(String text, final String donationURL, String tipText) {
            boolean isDarkTheme = ThemeMediator.isDarkLafThemeOn();
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
