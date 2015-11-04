/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014,, FrostWire(R). All rights reserved.
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

import javax.swing.JCheckBox;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.ApplicationSettings;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class UXStatsPaneItem extends AbstractPaneItem {

    public final static String TITLE = I18n.tr("Anonymous Usage Statistics");

    public final static String LABEL = I18n.tr("Send anonymous usage statistics so that FrostWire can be improved more effectively. No information regarding content searched, shared or played nor any information that can personally identify you will be stored on disk or sent through the network.");

    public final static String CHECK_BOX_LABEL = I18n.tr("Send anonymous usage statistics");

    private final JCheckBox CHECK_BOX = new JCheckBox();

    public UXStatsPaneItem() {
        super(TITLE, LABEL);

        LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL, CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(comp.getComponent());
    }

    public void initOptions() {
        CHECK_BOX.setSelected(ApplicationSettings.UX_STATS_ENABLED.getValue());
    }

    public boolean applyOptions() {
        ApplicationSettings.UX_STATS_ENABLED.setValue(CHECK_BOX.isSelected());
        return true;
    }

    public boolean isDirty() {
        return ApplicationSettings.UX_STATS_ENABLED.getValue() != CHECK_BOX.isSelected();
    }
}
