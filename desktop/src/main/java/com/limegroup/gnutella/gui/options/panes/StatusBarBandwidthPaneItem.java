/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.StatusBarSettings;

import javax.swing.*;

/**
 * This class defines the panel in the options window that allows the user
 * to change whether the firewall indicator is shown in the status bar.
 */
public final class StatusBarBandwidthPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Bandwidth Indicator");
    private final static String LABEL = I18n.tr("You can display your bandwidth consumption in the status bar.");
    private final JCheckBox CHECK_BOX = new JCheckBox();

    /**
     * The constructor constructs all the elements of this
     * `AbstractPaneItem`.
     *
     */
    public StatusBarBandwidthPaneItem() {
        super(TITLE, LABEL);
        /*
          Constant for the key of the locale-specific `String` for whether
          the firewall status should be displayed in the status bar.
         */
        String CHECK_BOX_LABEL = I18n.tr("Show Bandwidth Indicator:");
        LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL,
                CHECK_BOX, LabeledComponent.LEFT_GLUE,
                LabeledComponent.LEFT);
        add(comp.getComponent());
    }

    /**
     * Defines the abstract method in `AbstractPaneItem`.<p>
     * <p>
     * Sets the options for the fields in this `PaneItem` when the
     * window is shown.
     */
    public void initOptions() {
        CHECK_BOX.setSelected(StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue());
    }

    /**
     * Defines the abstract method in `AbstractPaneItem`.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        if (!isDirty())
            return false;
        StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.setValue(CHECK_BOX.isSelected());
        GUIMediator.instance().getStatusLine().refresh();
        return false;
    }

    public boolean isDirty() {
        return StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue() != CHECK_BOX.isSelected();
    }
}
