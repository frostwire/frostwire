/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

import org.limewire.util.OSUtils;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class DonationButtons extends JPanel {

    private static final Color FONT_COLOR = new Color(0x1f3545);

    public DonationButtons() {
        setLayout(new MigLayout("insets 0, nogrid, ltr, gapx 6", "", "[align center]"));
        add(createDonateLabel());
        //MigLayout lesson: Using px explicitly as the unit does make a big difference.

        int bitcoinWidth = (OSUtils.isLinux()) ? 52 : 46;
        int paypalWidth = (OSUtils.isLinux()) ? 52 : 46;

        add(new DonationButton("bitcoin", "http://www.frostwire.com/bitcoin", I18n.tr("Support FrostWire development with a Bitcoin donation")), "w "+bitcoinWidth+"px!, h 18px!");
        add(new DonationButton("$1", "http://www.frostwire.com/?id=donate&amt=1", I18n.tr("Support FrostWire development with a USD $1 donation")), "w 26px!, h 18px!");
        add(new DonationButton("$5", "http://www.frostwire.com/?id=donate&amt=5", I18n.tr("Support FrostWire development with a USD $5 donation")), "w 26px!, h 18px!");
        add(new DonationButton("$10", "http://www.frostwire.com/?id=donate&amt=10", I18n.tr("Support FrostWire development with a USD $10 donation")), "w 30px!, h 18px!");
        add(new DonationButton("$25", "http://www.frostwire.com/?id=donate&amt=25", I18n.tr("Support FrostWire development with a USD $25 donation")), "w 30px!, h 18px!");
        add(new DonationButton("paypal", "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=XNBZ6GMVTDWQQ", I18n.tr("Support FrostWire development with a Paypal donation")), "w "+ paypalWidth +"px!, h 18px!");
    }

    private JLabel createDonateLabel() {
        Font labelFont = getFont().deriveFont(Font.BOLD);// new Font("Helvetica", Font.BOLD, 12);
        JLabel donateLabel = new JLabel(I18n.tr("Donate") + ":");
        donateLabel.setForeground(FONT_COLOR);
        donateLabel.setFont(labelFont);
        return donateLabel;
    }

    private class DonationButton extends JButton {

        public DonationButton(String text, String donationURL, String tipText) {
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
            addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    GUIMediator.openURL(donationURL);
                }
            });
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
