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

package com.limegroup.gnutella.gui.init;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.URLLabel;

import javax.swing.*;
import java.awt.*;

/**
 * this class displays information welcoming the user to the
 * setup wizard.
 */
final class WelcomeWindow extends SetupWindow {
    private static final String TEXT1 = I18n.tr("FrostWire is a Peer to Peer Application that enables you to share files of your choosing with other users connected to the BitTorrent network.");
    private static final String TEXT2 = I18n.tr("Installing and using the program does not constitute a license for obtaining or distributing unauthorized content.");

    /**
     * Creates the window and its components
     */
    WelcomeWindow(SetupManager manager, boolean partial) {
        super(manager, I18n.tr("Welcome"), partial ? I18n.tr("Welcome to the FrostWire setup wizard. FrostWire has recently added new features that require your configuration. FrostWire will guide you through a series of steps to configure these new features.") : I18n
                .tr("Welcome to the FrostWire setup wizard. FrostWire will guide you through a series of steps to configure FrostWire for optimum performance."));
    }

    public Icon getIcon() {
        return GUIMediator.getThemeImage("logo");
    }

    @Override
    protected void createWindow() {
        super.createWindow();
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c;
        JComponent label1 = createPanel(TEXT1, TEXT2);
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(0, 0, 10, 0);
        panel.add(label1, c);
        setSetupComponent(panel);
    }

    private JComponent createPanel(String text1, String text2) {
        JPanel panel = new JPanel();
        panel.putClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND, Boolean.TRUE);
        panel.setBorder(BorderFactory.createLineBorder(ThemeMediator.LIGHT_BORDER_COLOR));
        //panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GUIUtils.hexToColor("C8C8C8"), 1),
        //        BorderFactory.createLineBorder(GUIUtils.hexToColor("FBFBFB"), 3)));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        com.limegroup.gnutella.gui.MultiLineLabel label1 = new com.limegroup.gnutella.gui.MultiLineLabel(text1, 400);
        label1.setFont(label1.getFont().deriveFont(16f));
        label1.setForeground(GUIUtils.hexToColor("333333"));
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1;
        c.insets = new Insets(10, 10, 10, 10);
        panel.add(label1, c);
        com.limegroup.gnutella.gui.MultiLineLabel label2 = new com.limegroup.gnutella.gui.MultiLineLabel(text2, 400);
        label2.setFont(label2.getFont().deriveFont(16f));
        label2.setForeground(GUIUtils.hexToColor("333333"));
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1;
        c.insets = new Insets(10, 10, 10, 10);
        panel.add(label2, c);
        com.limegroup.gnutella.gui.MultiLineLabel label3 = new com.limegroup.gnutella.gui.MultiLineLabel(I18n.tr("FrostWire is free software,") + " ", 400);
        label3.setFont(label3.getFont().deriveFont(16f));
        label3.setForeground(GUIUtils.hexToColor("333333"));
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = 1;
        c.weightx = 0;
        c.insets = new Insets(10, 10, 10, 0);
        panel.add(label3, c);
        URLLabel findMore = new URLLabel("http://www.frostwire.com/scams", I18n.tr("Do not pay for FrostWire."));
        findMore.setFont(findMore.getFont().deriveFont(16f));
        findMore.setForeground(GUIUtils.hexToColor("333333"));
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1.0;
        panel.add(findMore, c);
        return panel;
    }
}
