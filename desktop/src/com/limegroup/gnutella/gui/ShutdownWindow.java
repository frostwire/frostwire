/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class ShutdownWindow extends JDialog {
    /**
     *
     */
    private static final long serialVersionUID = 446845150731872693L;
    private final ImageIcon backgroundImage;

    ShutdownWindow() {
        super(GUIMediator.getAppFrame());
        final String image_path = String.format("org/limewire/gui/images/%s.jpg",
                !OSUtils.isWindowsAppStoreInstall() ? "app_shutdown" : "windows_appstore_install_shutdown");
        backgroundImage = ResourceManager.getImageFromResourcePath(image_path);
        final String backgroundUrl = !OSUtils.isWindowsAppStoreInstall() ?
                "https://www.frostwire.com/android/?from=shutdown" :
                "https://www.frostwire.com/give/?from=shutdown";

        setResizable(false);
        setTitle(I18n.tr("Shutting down FrostWire..."));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundImage.getImage(), 0, 0, null);
            }
        };
        backgroundPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                e.consume();
                GUIMediator.openURL(backgroundUrl);
            }
        });
        backgroundPanel.setLayout(null);
        backgroundPanel.setSize(800, 500);
        add(backgroundPanel);
        Insets insets = backgroundPanel.getInsets();
        JLabel label = new JLabel(I18n.tr("Please wait while FrostWire shuts down..."));
        label.setFont(new Font("Dialog", Font.PLAIN, 16));
        Dimension labelPrefSize = label.getPreferredSize();
        backgroundPanel.add(label);
        label.setBounds(65 + insets.left, 400 + insets.top, labelPrefSize.width, labelPrefSize.height);
        JProgressBar bar = new LimeJProgressBar();
        bar.setIndeterminate(true);
        bar.setStringPainted(false);
        backgroundPanel.add(bar);
        bar.setBounds(55 + insets.left, 428 + insets.top, 680, 30);
        getContentPane().setPreferredSize(new Dimension(800, 500));
        pack();
    }

    public static void main(String[] args) {
        ShutdownWindow window = new ShutdownWindow();
        window.setVisible(true);
    }
}