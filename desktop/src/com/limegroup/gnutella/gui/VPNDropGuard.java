/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Grzesiek Rzaca (grzesiekrzaca)
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

import com.frostwire.bittorrent.BTEngine;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.ConnectionSettings;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

import static com.limegroup.gnutella.gui.I18n.tr;

public class VPNDropGuard implements VPNStatusRefresher.VPNStatusListener {
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
                MessageService.instance().showMessage(tr("VPN-Drop protection disabled. Restarting BitTorrent engine."));
                BackgroundExecutorService.schedule(() -> {
                    if (BTEngine.getInstance().isPaused()) {
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

    @Override
    public void onStatusUpdated(boolean vpnIsOn) {
        boolean vpnDropProtectionOn = ConnectionSettings.VPN_DROP_PROTECTION.getValue();
        BTEngine instance = BTEngine.getInstance();
        if (vpnDropProtectionOn) {
            if (vpnIsOn && instance.isPaused()) {
                instance.resume();
            }
            if (!vpnIsOn && !instance.isPaused()) {
                instance.pause();
            }
        } else if (!vpnDropProtectionOn && instance.isPaused()) {
            instance.resume();
        }
    }
}
