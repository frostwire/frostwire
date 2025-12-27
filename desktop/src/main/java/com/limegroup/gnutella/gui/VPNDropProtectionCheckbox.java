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

import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.ConnectionSettings;

import javax.swing.*;

/**
 * A checkbox component for the status bar that toggles VPN-Drop protection.
 * When enabled, BitTorrent transfers will be paused if the VPN is disconnected.
 */
public class VPNDropProtectionCheckbox extends JCheckBox {

    public VPNDropProtectionCheckbox() {
        super(I18n.tr("VPN-Drop protection"));

        // Set initial state
        updateCheckboxState();

        // Add listener for checkbox changes
        addActionListener(e -> {
            // Disable the checkbox while we process the change
            setEnabled(false);

            // Use BackgroundQueuedExecutor to avoid blocking the EDT
            BackgroundQueuedExecutorService.schedule(() -> {
                try {
                    boolean newValue = isSelected();
                    ConnectionSettings.VPN_DROP_PROTECTION.setValue(newValue);

                    // Apply the protection logic (defined in VPNDropGuard to avoid duplication)
                    VPNDropGuard.applyVPNDropProtection();
                } finally {
                    // Re-enable the checkbox on the EDT
                    GUIMediator.safeInvokeLater(() -> setEnabled(true));
                }
            });
        });

        setToolTipText(I18n.tr("When enabled, BitTorrent transfers will pause if your VPN is disconnected"));
    }

    /**
     * Updates the checkbox state to reflect the current setting value.
     */
    public void updateCheckboxState() {
        setSelected(ConnectionSettings.VPN_DROP_PROTECTION.getValue());
    }
}
