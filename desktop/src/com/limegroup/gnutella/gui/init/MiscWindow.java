/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.init;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.limewire.util.OSUtils;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.WindowsUtils;
import com.limegroup.gnutella.gui.options.panes.UXStatsPaneItem;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.StartupSettings;
import com.limegroup.gnutella.util.MacOSXUtils;

/**
 * This class displays a window to the user allowing them to specify
 * their connection speed.
 */
final class MiscWindow extends SetupWindow {

    /*
     * System Startup
     */
    private JCheckBox _startup;
    private JCheckBox checkBoxUXStats;

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

        // UX Stats
        {
            JPanel panelUXStats = new JPanel(new GridLayout(2, 0));

            panelUXStats.setBorder(ThemeMediator.createTitledBorder(UXStatsPaneItem.TITLE));

            checkBoxUXStats = new JCheckBox(UXStatsPaneItem.CHECK_BOX_LABEL);
            checkBoxUXStats.setSelected(ApplicationSettings.UX_STATS_ENABLED.getValue());

            JLabel desc = new JLabel("<html>" + UXStatsPaneItem.LABEL + "</html>");
            desc.setBorder(BorderFactory.createEmptyBorder(0, 10, 2, 5));
            desc.setForeground(Color.BLACK);
            desc.setFont(desc.getFont().deriveFont(Font.PLAIN));

            panelUXStats.add(desc);
            panelUXStats.add(checkBoxUXStats);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridy = GridBagConstraints.RELATIVE;
            gbc.weightx = 1.0;
            
            mainPanel.add(panelUXStats, gbc);

            panelUXStats.putClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND, Boolean.TRUE);
            panelUXStats.updateUI();
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

        ApplicationSettings.UX_STATS_ENABLED.setValue(checkBoxUXStats.isSelected());
    }
}
