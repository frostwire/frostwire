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

import com.frostwire.bittorrent.BTEngine;
import com.limegroup.gnutella.gui.MainFrame;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.ConnectionSettings;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

import static com.limegroup.gnutella.gui.I18n.tr;

public class VPNDropGuard implements VPNStatusRefresher.VPNStatusListener {
    private static boolean lastKnownVPNStatus = false;

    private static boolean canUseBitTorrent(boolean showExplanationDialog) {
        return canUseBitTorrent(showExplanationDialog, null);
    }

    static boolean canUseBitTorrent(boolean showExplanationDialog, Runnable uiCallback) {
        if (ConnectionSettings.VPN_DROP_PROTECTION.getValue() && !VPNs.isVPNActive()) {
            if (showExplanationDialog) {
                showExplanationDialog(uiCallback);
            }
            return false;
        }
        return true;
    }

    private static void showExplanationDialog(final Runnable uiCallback) {
        GUIMediator.safeInvokeLater(() -> {
            final JDialog dialog = new JDialog();
            dialog.setTitle(tr("VPN-Drop Protection Active"));
            dialog.setLayout(new FlowLayout()); // gets rid of that default border layout
            dialog.setModal(true);
            dialog.setResizable(false);
            // Icon and labels
            JLabel icon = new JLabel(GUIMediator.getThemeImage("vpn_drop_guard_dialog_icon"));
            icon.setPreferredSize(new Dimension(115, 96));
            JPanel labelPanel = new JPanel(new MigLayout());
            labelPanel.add(new JLabel("<html><p><strong>" + tr("BitTorrent is off because your VPN is disconnected") + "</strong></p></html>"), "wrap");
            labelPanel.add(new JLabel("<html><p>" + tr("Check the status of your VPN connection or disable the VPN-Drop Protection") + ".</p></html>"), "wrap");
            JPanel upperPanel = new JPanel(new MigLayout());
            upperPanel.add(icon);
            upperPanel.add(labelPanel);
            // Buttons
            JButton whatIsAVPN = new JButton(tr("What is a VPN?"));
            whatIsAVPN.addActionListener(e -> {
                dialog.dispose();
                GUIMediator.openURL(VPNStatusButton.VPN_URL);
            });
            JButton disableVPNDrop = new JButton(tr("Disable VPN-Drop protection"));
            disableVPNDrop.addActionListener(e -> {
                dialog.dispose();
                ConnectionSettings.VPN_DROP_PROTECTION.setValue(false);
                if (uiCallback != null) {
                    try {
                        uiCallback.run();
                    } catch (Throwable ignored) {
                    }
                }
                // Update the status bar checkbox to reflect the disabled state
                GUIMediator.safeInvokeLater(() -> {
                    MainFrame mainFrame = GUIMediator.instance().getMainFrame();
                    if (mainFrame != null) {
                        mainFrame.getStatusLine().updateVPNDropProtectionCheckboxState();
                    }
                });
                MessageService.instance().showMessage(tr("VPN-Drop protection disabled. Restarting BitTorrent engine."));
                BackgroundQueuedExecutorService.schedule(() -> {
                    if (BTEngine.getInstance().isPausedCached()) {
                        BTEngine.getInstance().resume();
                    }
                });
            });
            JButton ok = new JButton(tr("Ok"));
            ok.addActionListener(e -> dialog.dispose());
            JPanel buttonsPanel = new JPanel(new MigLayout("insets 10px 0 0 0, align right", ""));
            buttonsPanel.add(whatIsAVPN);
            buttonsPanel.add(disableVPNDrop);
            buttonsPanel.add(ok, "growx, shrink 0");
            // Put it all together
            JPanel panel = new JPanel(new MigLayout());
            panel.add(upperPanel, "wrap");
            panel.add(buttonsPanel, "growx");
            // Add it to the dialog
            dialog.add(panel);
            dialog.setPreferredSize(new Dimension(700, 225));
            dialog.pack();
            dialog.setLocationRelativeTo(GUIMediator.getAppFrame()); // centers dialog with respect to parent frame
            dialog.setVisible(true);
        });
    }

    public static boolean canUseBitTorrent() {
        return canUseBitTorrent(true);
    }

    /**
     * Applies VPN-Drop protection logic based on cached VPN status and protection setting.
     * Uses the last known VPN status to avoid expensive system calls.
     * This is called both when VPN status changes and when the protection setting changes.
     */
    public static void applyVPNDropProtection() {
        boolean vpnDropProtectionOn = ConnectionSettings.VPN_DROP_PROTECTION.getValue();
        BTEngine instance = BTEngine.getInstance();

        if (vpnDropProtectionOn) {
            if (lastKnownVPNStatus && instance.isPausedCached()) {
                instance.resume();
            }
            if (!lastKnownVPNStatus && !instance.isPausedCached()) {
                instance.pause();
            }
        } else if (!vpnDropProtectionOn && instance.isPausedCached()) {
            instance.resume();
        }
    }

    @Override
    public void onStatusUpdated(boolean vpnIsOn) {
        lastKnownVPNStatus = vpnIsOn;
        applyVPNDropProtection();
    }
}
