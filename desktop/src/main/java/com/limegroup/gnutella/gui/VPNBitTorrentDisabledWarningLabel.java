/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Grzesiek Rzaca (grzesiekrzaca)
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

import com.limegroup.gnutella.settings.ConnectionSettings;

import javax.swing.*;

public class VPNBitTorrentDisabledWarningLabel extends JLabel implements VPNStatusRefresher.VPNStatusListener {
    private boolean vpnIsOn;

    boolean shouldBeShown() {
        return ConnectionSettings.VPN_DROP_PROTECTION.getValue() && !vpnIsOn;
    }

    @Override
    public void onStatusUpdated(boolean vpnIsOn) {
        this.vpnIsOn = vpnIsOn;
    }
}
