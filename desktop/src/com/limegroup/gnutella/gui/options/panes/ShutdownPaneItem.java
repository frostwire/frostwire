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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.ResourceManager;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.frostwire.util.OSUtils;

import javax.swing.*;

/**
 * This class defines the panel in the options
 * window that allows the user to select the
 * default shutdown behavior.
 */
public class ShutdownPaneItem extends AbstractPaneItem {
    public final static String TITLE = I18n.tr("Shutdown Behavior");
    public final static String LABEL = I18n.tr("You can choose the default shutdown behavior.");
    /**
     * RadioButton for selecting immediate shutdown
     */
    private final JRadioButton shutdownImmediately;
    /**
     * RadioButton for selecting the minimize to tray option.  This
     * option is only displayed on systems that support the tray.
     */
    private final JRadioButton minimizeToTray;
    private final JCheckBox _checkBoxShowHideExitDialog;

    /**
     * Creates new ShutdownOptionsPaneItem
     *
     */
    public ShutdownPaneItem() {
        super(TITLE, LABEL);
        BoxPanel buttonPanel = new BoxPanel();
        String immediateLabel = I18n.tr("Shutdown Immediately");
        String minimizeLabel = I18n.tr("Minimize to System Tray");
        shutdownImmediately = new JRadioButton(I18n.tr(immediateLabel));
        minimizeToTray = new JRadioButton(I18n.tr(minimizeLabel));
        String showHideExitDialogLabel = I18n.tr("Show dialog to ask before close");
        _checkBoxShowHideExitDialog = new JCheckBox(showHideExitDialogLabel);
        ButtonGroup bg = new ButtonGroup();
        buttonPanel.add(shutdownImmediately);
        bg.add(shutdownImmediately);
        if (OSUtils.supportsTray() && ResourceManager.instance().isTrayIconAvailable()) {
            buttonPanel.add(minimizeToTray);
            bg.add(minimizeToTray);
        }
        BoxPanel mainPanel = new BoxPanel(BoxPanel.X_AXIS);
        mainPanel.add(buttonPanel);
        mainPanel.add(Box.createHorizontalGlue());
        mainPanel.add(_checkBoxShowHideExitDialog);
        mainPanel.add(Box.createHorizontalGlue());
        add(mainPanel);
    }

    /**
     * Applies the options currently set in this <tt>PaneItem</tt>.
     *
     */
    public boolean applyOptions() {
        if (minimizeToTray.isSelected()) {
            ApplicationSettings.MINIMIZE_TO_TRAY.setValue(true);
        } else { // if(shutdownImmediately.isSelected())
            ApplicationSettings.MINIMIZE_TO_TRAY.setValue(false);
        }
        ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.setValue(_checkBoxShowHideExitDialog.isSelected());
        return false;
    }

    /**
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        if (ApplicationSettings.MINIMIZE_TO_TRAY.getValue()) {
//            if (OSUtils.supportsTray() && !ResourceManager.instance().isTrayIconAvailable()) {
//                //shutdownAfterTransfers.setSelected(true);
//            } else {
            minimizeToTray.setSelected(true);
//            }
        } else {
            shutdownImmediately.setSelected(true);
        }
        _checkBoxShowHideExitDialog.setSelected(ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.getValue());
    }

    public boolean isDirty() {
        boolean minimized = ApplicationSettings.MINIMIZE_TO_TRAY.getValue();
        boolean reallyMinimized = minimized && ResourceManager.instance().isTrayIconAvailable();
        boolean immediate = !ApplicationSettings.MINIMIZE_TO_TRAY.getValue();
        return minimizeToTray.isSelected() != reallyMinimized || shutdownImmediately.isSelected() != immediate
                || _checkBoxShowHideExitDialog.isSelected() != ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.getValue();
    }
}
