/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
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

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.UpdateManagerSettings;

import javax.swing.*;

/**
 * Pane to let the user decide wether or not to see the FrostClick promotions.
 */
public final class ShowPromoOverlaysPaneItem extends AbstractPaneItem {
    public final static String TITLE = I18n.tr("FrostClick Promotions");
    public final static String LABEL = I18n.tr("You can enable or disable the FrostClick Promotion on the welcome screen. FrostClick promotions help artists and content creators distribute their content legally and freely to hundreds of thousands of people via FrostWire, BitTorrent and Gnutella. Keep this option on to support file sharing and the future of content distribution.");
    /**
     * Constant for the check box that specifies whether to enable or
     * disable frostclick promos
     */
    private final JCheckBox CHECK_BOX = new JCheckBox();

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     *
     */
    public ShowPromoOverlaysPaneItem() {
        super(TITLE, LABEL);
        /*
          Constant for the key of the locale-specific <tt>String</tt> for the
          frostclick promotions enabled check box label..
         */
        String SHOW_PROMOTION_OVERLAYS_LABEL = I18n.tr("Enable FrostClick Promotions (highly recommended):");
        LabeledComponent c = new LabeledComponent(SHOW_PROMOTION_OVERLAYS_LABEL,
                CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(c.getComponent());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        CHECK_BOX.setSelected(UpdateManagerSettings.SHOW_PROMOTION_OVERLAYS.getValue());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        UpdateManagerSettings.SHOW_PROMOTION_OVERLAYS.setValue(CHECK_BOX.isSelected());
        return false;
    }

    public boolean isDirty() {
        return UpdateManagerSettings.SHOW_PROMOTION_OVERLAYS.getValue() != CHECK_BOX.isSelected();
    }
}


