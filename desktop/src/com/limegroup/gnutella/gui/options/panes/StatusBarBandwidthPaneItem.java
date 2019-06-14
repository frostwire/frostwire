/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     *
     */
    public StatusBarBandwidthPaneItem() {
        super(TITLE, LABEL);
        /*
          Constant for the key of the locale-specific <tt>String</tt> for whether
          the firewall status should be displayed in the status bar.
         */
        String CHECK_BOX_LABEL = I18n.tr("Show Bandwidth Indicator:");
        LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL,
                CHECK_BOX, LabeledComponent.LEFT_GLUE,
                LabeledComponent.LEFT);
        add(comp.getComponent());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        CHECK_BOX.setSelected(StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
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
