/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

import com.frostwire.util.ThreadPool;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VPNStatusButton extends IconButton implements VPNStatusRefresher.VPNStatusListener {

    static final String VPN_URL = "http://www.frostwire.com/vpn";

    VPNStatusButton() {
        super("vpn_off");
        setBorder(null);
        initActionListener();
    }

    private void initActionListener() {
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GUIMediator.openURL(VPN_URL);
            }
        });
    }

    private void updateVPNIcon(boolean vpnIsOn) {
        if (vpnIsOn) {
            setIcon(GUIMediator.getThemeImage("vpn_on"));
            setToolTipText("<html><p width=\"260\">" +
                    I18n.tr("FrostWire has detected a VPN connection, your privacy is safe from prying eyes.") +
                    "</p></html>");
        } else {
            setIcon(GUIMediator.getThemeImage("vpn_off"));
            setToolTipText("<html><p width=\"260\">" +
                    I18n.tr("FrostWire can't detect an encrypted VPN connection, your privacy is at risk. Click icon to set up an encrypted VPN connection.") +
                    "</p></html>");
        }
    }

    @Override
    public void onStatusUpdated(boolean vpnIsOn) {
        updateVPNIcon(vpnIsOn);
    }
}
