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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A compact checkbox component for the status bar that toggles VPN-Drop protection.
 * When enabled, BitTorrent transfers will be paused if the VPN is disconnected.
 * Features clickable VPN link that opens VPN info page.
 */
public class VPNDropProtectionCheckbox extends JPanel {
    private final JCheckBox checkbox;

    public VPNDropProtectionCheckbox() {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);

        // Create checkbox with minimal text
        checkbox = new JCheckBox();
        checkbox.setOpaque(false);
        checkbox.setFont(new Font(checkbox.getFont().getName(), Font.PLAIN, 9));

        // Set initial state
        updateCheckboxState();

        // Add listener for checkbox changes
        checkbox.addActionListener(e -> {
            // Disable the checkbox while we process the change
            checkbox.setEnabled(false);

            // Use BackgroundQueuedExecutor to avoid blocking the EDT
            BackgroundQueuedExecutorService.schedule(() -> {
                try {
                    boolean newValue = checkbox.isSelected();
                    ConnectionSettings.VPN_DROP_PROTECTION.setValue(newValue);

                    // Apply the protection logic (defined in VPNDropGuard to avoid duplication)
                    VPNDropGuard.applyVPNDropProtection();
                } finally {
                    // Re-enable the checkbox on the EDT
                    GUIMediator.safeInvokeLater(() -> checkbox.setEnabled(true));
                }
            });
        });

        // Add checkbox to panel
        add(checkbox);

        // Create clickable VPN link label
        JLabel vpnLink = new JLabel(I18n.tr("VPN"));
        vpnLink.setFont(new Font(vpnLink.getFont().getName(), Font.PLAIN, 9));
        vpnLink.setForeground(new Color(0, 0, 200)); // Blue color for link
        vpnLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Add underline and hover effects
        vpnLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                vpnLink.setText("<html><u>VPN</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                vpnLink.setText(I18n.tr("VPN"));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                GUIMediator.openURL(VPNStatusButton.VPN_URL);
            }
        });

        add(vpnLink);
        JLabel dropProtectionLabel = new JLabel(I18n.tr("-Drop protection"));
        dropProtectionLabel.setFont(new Font(dropProtectionLabel.getFont().getName(), Font.PLAIN, 9));
        add(dropProtectionLabel);

        setToolTipText(I18n.tr("When enabled, BitTorrent transfers will pause if your VPN is disconnected"));
        setPreferredSize(new Dimension(130, 20));
    }

    /**
     * Updates the checkbox state to reflect the current setting value.
     */
    public void updateCheckboxState() {
        checkbox.setSelected(ConnectionSettings.VPN_DROP_PROTECTION.getValue());
    }
}
