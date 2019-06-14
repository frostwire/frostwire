/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import net.miginfocom.swing.MigLayout;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
class DonationButtons extends JPanel {
    private static final Color FONT_COLOR = new Color(0x1f3545);

    DonationButtons() {
        setLayout(new MigLayout("insets 0, nogrid, ltr, gapx 6", "", "[align center]"));
        add(createDonateLabel());
        //MigLayout lesson: Using px explicitly as the unit does make a big difference.
        int bitcoinWidth = (OSUtils.isLinux()) ? 52 : 40;
        int paypalWidth = (OSUtils.isLinux()) ? 52 : 46;
        add(new DonationButton("BTC", "http://www.frostwire.com/bitcoin", I18n.tr("Support FrostWire development with a Bitcoin donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("BCH", "http://www.frostwire.com/bitcoin-cash", I18n.tr("Support FrostWire development with a Bitcoin Cash donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("ETH", "http://www.frostwire.com/ethereum", I18n.tr("Support FrostWire development with an Ether donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("DASH", "http://www.frostwire.com/dash", I18n.tr("Support FrostWire development with a Dash donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("LTC", "http://www.frostwire.com/litecoin", I18n.tr("Support FrostWire development with a Litecoin donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("ZEC", "http://www.frostwire.com/zcash", I18n.tr("Support FrostWire development with a ZCash donation")), "w " + bitcoinWidth + "px!, h 18px!");
        add(new DonationButton("PayPal", "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=XNBZ6GMVTDWQQ", I18n.tr("Support FrostWire development with a Paypal donation")), "w " + paypalWidth + "px!, h 18px!");
    }

    private JLabel createDonateLabel() {
        Font labelFont = getFont().deriveFont(Font.BOLD);
        JLabel donateLabel = new JLabel(I18n.tr("Donate") + ":");
        donateLabel.setForeground(FONT_COLOR);
        donateLabel.setFont(labelFont);
        return donateLabel;
    }

    private class DonationButton extends JButton {
        DonationButton(String text, String donationURL, String tipText) {
            initComponent(text, donationURL, tipText);
        }

        private void initComponent(String text, final String donationURL, String tipText) {
            Font buttonFont = new Font("Dialog", Font.BOLD, 12);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(null);
            setContentAreaFilled(false);
            setOpaque(false);
            setFont(buttonFont);
            setForeground(FONT_COLOR);
            setBackground(new Color(0xedf1f4));
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
