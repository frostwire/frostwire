/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.init;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.WindowsUtils;
import com.limegroup.gnutella.settings.StartupSettings;
import com.limegroup.gnutella.util.MacOSXUtils;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.*;

/**
 * This class displays a window to the user allowing them to specify
 * their connection speed.
 */
final class MiscWindow extends SetupWindow {
    /*
     * System Startup
     */
    private JCheckBox _startup;

    /**
     * Creates the window and its components.
     */
    MiscWindow(SetupManager manager) {
        super(manager, I18n.tr("Miscellaneous Settings"), I18n.tr("Below, are several options that affect the functionality of FrostWire."));
    }

    protected void createWindow() {
        super.createWindow();
        JPanel mainPanel = new JPanel(new GridBagLayout());
        // System Startup
        if (GUIUtils.shouldShowStartOnStartupWindow()) {
            GridBagConstraints gbc = new GridBagConstraints();
            JPanel startupPanel = new JPanel(new GridBagLayout());
            startupPanel.setBorder(ThemeMediator.createTitledBorder(I18n.tr("System Startup")));
            _startup = new JCheckBox(I18n.tr("Start Automatically"));
            _startup.setSelected(StartupSettings.RUN_ON_STARTUP.getValue());
            JLabel desc = new JLabel("<html>" + I18n.tr("Would you like FrostWire to start when you log into your computer? This will cause FrostWire to start faster when you use it later.") + "</html>");
            desc.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 5));
            desc.setForeground(Color.BLACK);
            desc.setFont(desc.getFont().deriveFont(Font.PLAIN));
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.weightx = 1.0;
            startupPanel.add(desc, gbc);
            startupPanel.add(_startup, gbc);
            gbc.insets = new Insets(0, 0, 10, 0);
            mainPanel.add(startupPanel, gbc);
            startupPanel.putClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND, Boolean.TRUE);
        }
        // Vertical Filler
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.weighty = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        mainPanel.add(new JPanel(), gbc);
        setSetupComponent(mainPanel);
    }

    /**
     * Overrides applySettings in SetupWindow superclass.
     * Applies the settings handled in this window.
     */
    public void applySettings(boolean loadCoreComponents) {
        // System Startup
        if (GUIUtils.shouldShowStartOnStartupWindow()) {
            boolean allow = _startup.isSelected();
            if (OSUtils.isMacOSX())
                MacOSXUtils.setLoginStatus(allow);
            else if (WindowsUtils.isLoginStatusAvailable())
                WindowsUtils.setLoginStatus(allow);
            StartupSettings.RUN_ON_STARTUP.setValue(allow);
        }
    }
}
