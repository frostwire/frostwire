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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.limegroup.gnutella.gui.I18n.tr;

public class VPNDropGuard {

    public static boolean canUseBitTorrent(boolean showExplanationDialog) {
        if (ConnectionSettings.VPN_DROP_PROTECTION.getValue() && !VPNs.isVPNActive()) {
            if (showExplanationDialog) {
                showExplanationDialog();
            }
            return false;
        }
        return true;
    }

    private static void showExplanationDialog() {
        GUIMediator.safeInvokeLater(new Runnable() {
            @Override
            public void run() {
                final JDialog dialog = new JDialog();
                dialog.setTitle(tr("VPN-Drop Protection Active"));
                dialog.setLayout(new FlowLayout()); // gets rid of that default border layout
                dialog.setModal(true);
                dialog.setResizable(false);
                JPanel panel = new JPanel(new MigLayout());
                panel.add(new JLabel("<html><h1>" + tr("BitTorrent is off because your VPN is disconnected") + "</h1></html>"), "wrap");
                panel.add(new JLabel("<html><h2>" + tr("Check the status of your VPN connection or disable the VPN-Drop Protection") + ".</h2></html>"), "wrap");
                JButton whatIsAVPN = new JButton("<html><h3>" + tr("What is a VPN?"));
                whatIsAVPN.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        dialog.dispose();
                        GUIMediator.openURL(VPNStatusButton.VPN_URL);
                    }
                });
                JButton disableVPNDrop = new JButton("<html><h3>" + tr("Disable VPN-Drop protection"));
                disableVPNDrop.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        dialog.dispose();
                        ConnectionSettings.VPN_DROP_PROTECTION.setValue(false);
                        MessageService.instance().showMessage(tr("VPN-Drop protection disabled. Restarting BitTorrent engine."));
                        BackgroundExecutorService.schedule(new Runnable() {
                            @Override
                            public void run() {
                                BTEngine.getInstance().restart(); // has a sleep call, don't do this on UI thread.
                            }
                        });
                    }
                });
                JButton ok = new JButton("<html><h3>" + tr("Ok"));
                ok.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        dialog.dispose();
                    }
                });
                JPanel panelButtons = new JPanel(new MigLayout("","[][][grow]"));
                panelButtons.add(whatIsAVPN);
                panelButtons.add(disableVPNDrop);
                panelButtons.add(ok,"growx, shrink 0");
                panel.add(panelButtons, "growx");
                dialog.add(panel);

                dialog.setPreferredSize(new Dimension(752, 214));
                dialog.pack();
                dialog.setLocationRelativeTo(GUIMediator.getAppFrame());
                dialog.setVisible(true);
            }
        });

    }

    public static boolean canUseBitTorrent() {
        return canUseBitTorrent(true);
    }

}
