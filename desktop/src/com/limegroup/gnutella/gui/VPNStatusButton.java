/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        iconButton = new IconButton("vpn_off");
        iconButton.setBorder(null);
        vpnDropGuardLabel = createVPNDisconnectLabel();
        initActionListener();
    }

    private void initActionListener() {
        iconButton.addActionListener(e -> GUIMediator.openURL(VPN_URL));
    }

    /**
     * Sets up the bittorrent connection disabled due to vpn settings info
     */
    private VPNBitTorrentDisabledWarningLabel createVPNDisconnectLabel() {
        VPNBitTorrentDisabledWarningLabel bitTorrentDisabledWarning = new VPNBitTorrentDisabledWarningLabel();
        bitTorrentDisabledWarning.setText("<html><b>" + I18n.tr("VPN Off: BitTorrent disabled") + "</b></html>");
        bitTorrentDisabledWarning.setToolTipText(I18n.tr("Due to current settings without VPN connection BitTorrent will not start. Click to see the settings screen"));
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
        if (vpnIsOn) {
            iconButton.setIcon(GUIMediator.getThemeImage("vpn_on"));
            iconButton.setToolTipText("<html><p width=\"260\">" +
                    I18n.tr("FrostWire has detected a VPN connection, your privacy is safe from prying eyes.") +
                    "</p></html>");
        } else {
            iconButton.setIcon(GUIMediator.getThemeImage("vpn_off"));
            iconButton.setToolTipText("<html><p width=\"260\">" +
                    I18n.tr("FrostWire can't detect an encrypted VPN connection, your privacy is at risk. Click icon to set up an encrypted VPN connection.") +
                    "</p></html>");
        }
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
