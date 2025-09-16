/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * This class contains the logo and the searching icon for the application.
 */
final class LogoPanel extends BoxPanel {
    /**
     * Constructs a new panel containing the logo and the search icon.
     */
    LogoPanel() {
        super(BoxPanel.X_AXIS);
        setupUI();
    }

    private void setupUI() {
        JLabel labelLogo = new JLabel();
        ImageIcon logoIcon = GUIMediator.getThemeImage("logo_header");
        labelLogo.setIcon(logoIcon);
        labelLogo.setSize(logoIcon.getIconWidth(), logoIcon.getIconHeight());
        GUIUtils.setOpaque(false, this);
        add(Box.createHorizontalGlue());
        add(labelLogo);
        add(Box.createHorizontalGlue());
        this.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent me) {
                GUIMediator.openURL("http://www.frostwire.com/?from=header");
            }

            public void mouseEntered(MouseEvent me) {
                setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        });
    }
}
