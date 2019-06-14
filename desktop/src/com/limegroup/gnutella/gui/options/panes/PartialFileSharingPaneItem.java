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

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.SharingSettings;

import javax.swing.*;

/**
 * Allows the user to change whether or not partial files are shared.
 */
public final class PartialFileSharingPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Partial Files");
    private final static String LABEL = I18n.tr("You can choose whether or not to automatically share partially downloaded files.");
    /**
     * Constant for the check box that specifies whether or not partial
     * files should be shared.
     */
    private final JCheckBox CHECK_BOX = new JCheckBox();

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     */
    public PartialFileSharingPaneItem() {
        super(TITLE, LABEL);

        /*
          Constant for the key of the locale-specific <code>String</code> for the
          upload pane check box label in the options window.
         */
        String CHECK_BOX_LABEL = I18n.tr("Allow Partial Sharing:");
        LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL,
                CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(comp.getComponent());
    }

    public void initOptions() {
        CHECK_BOX.setSelected(SharingSettings.ALLOW_PARTIAL_SHARING.getValue());
    }

    public boolean applyOptions() {
        SharingSettings.ALLOW_PARTIAL_SHARING.setValue(CHECK_BOX.isSelected());
        return false;
    }

    public boolean isDirty() {
        return SharingSettings.ALLOW_PARTIAL_SHARING.getValue() != CHECK_BOX.isSelected();
    }
}

