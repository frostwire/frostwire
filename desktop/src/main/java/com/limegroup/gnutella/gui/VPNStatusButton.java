/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VPNStatusButton extends JPanel implements VPNStatusRefresher.VPNStatusListener {
    static final String VPN_URL = "http://www.frostwire.com/vpn";
    private final IconButton iconButton;
    private final VPNBitTorrentDisabledWarningLabel vpnDropGuardLabel;
    private boolean lastVPNStatus;

    VPNStatusButton() {
        iconButton = new IconButton("vpn_off", true);
        iconButton.setBorder(null);
        vpnDropGuardLabel = createVPNDisconnectLabel();
        initActionListener();
    }

    private void initActionListener() {
        iconButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                GUIMediator.openURL(VPN_URL);
            }
        });
    }

    /**
     * Sets up the bittorrent connection disabled due to vpn settings info
     */
    private VPNBitTorrentDisabledWarningLabel createVPNDisconnectLabel() {
        VPNBitTorrentDisabledWarningLabel bitTorrentDisabledWarning = new VPNBitTorrentDisabledWarningLabel();
        bitTorrentDisabledWarning.setToolTipText(I18n.tr("Due to current settings without VPN connection BitTorrent will not start. Click to see the settings screen"));
        // Defer HTML content to avoid EDT violation
        // HTML rendering triggers expensive font metrics calculations (>2 second EDT block)
        SwingUtilities.invokeLater(() -> {
            bitTorrentDisabledWarning.setText("<html><b>" + I18n.tr("VPN Off: BitTorrent disabled") + "</b></html>");
        });
        bitTorrentDisabledWarning.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onVPNBitTorrentDisabledWarningLabelClicked();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                onVPNBitTorrentDisabledWarningLabelClicked();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                onVPNBitTorrentDisabledWarningLabelClicked();
            }
        });
        return bitTorrentDisabledWarning;
    }

    private void onVPNBitTorrentDisabledWarningLabelClicked() {
        VPNDropGuard.canUseBitTorrent(true, () -> {
            updateVPNIcon(false);
            GUIMediator.instance().getStatusLine().refresh();
        });
    }

    private void updateVPNIcon(boolean vpnIsOn) {
        lastVPNStatus = vpnIsOn;
        iconButton.setIcon(GUIMediator.getThemeImage(vpnIsOn ? "vpn_on" : "vpn_off"));
        // Defer HTML ToolTipText to avoid EDT violation
        // HTML rendering triggers expensive font metrics calculations (>2 second EDT block)
        SwingUtilities.invokeLater(() -> {
            if (vpnIsOn) {
                iconButton.setToolTipText("<html><p width=\"260\">" +
                        I18n.tr("FrostWire has detected a VPN connection, your privacy is safe from prying eyes.") +
                        "</p></html>");
            } else {
                iconButton.setToolTipText("<html><p width=\"260\">" +
                        I18n.tr("FrostWire can't detect an encrypted VPN connection, your privacy is at risk. Click icon to set up an encrypted VPN connection.") +
                        "</p></html>");
            }
        });
        removeAll();
        add(iconButton);
        if (!vpnIsOn && vpnDropGuardLabel.shouldBeShown()) {
            add(vpnDropGuardLabel);
        }
    }

    @Override
    public void onStatusUpdated(boolean vpnIsOn) {
        updateVPNIcon(vpnIsOn);
    }

    public boolean getLastVPNStatus() {
        return lastVPNStatus;
    }
}
