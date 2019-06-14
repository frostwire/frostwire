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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.ResourceManager;
import com.limegroup.gnutella.settings.ApplicationSettings;
import org.limewire.util.OSUtils;

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
