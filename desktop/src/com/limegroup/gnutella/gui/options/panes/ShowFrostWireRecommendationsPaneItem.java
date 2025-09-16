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

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.UpdateManagerSettings;

import javax.swing.*;

/**
 * Pane to let the user decide wether or not to see the FrostWire recommended software.
 */
public final class ShowFrostWireRecommendationsPaneItem extends AbstractPaneItem {
    public final static String TITLE = I18n.tr("FrostWire Recommendations");
    public final static String LABEL = I18n.tr("By enabling this feature you become eligible to receive FrostWire sponsored offers and software recommendations to complement your experience.");
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
    public ShowFrostWireRecommendationsPaneItem() {
        super(TITLE, LABEL);
        /*
          Constant for the key of the locale-specific <tt>String</tt> for the
          frostclick promotions enabled check box label..
         */
        String SHOW_FROSTWIRE_RECOMMENDATIONS_LABEL = I18n.tr("Enable FrostWire Recommendations (highly recommended):");
        LabeledComponent c = new LabeledComponent(SHOW_FROSTWIRE_RECOMMENDATIONS_LABEL,
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
        CHECK_BOX.setSelected(UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.getValue());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.setValue(CHECK_BOX.isSelected());
        return false;
    }

    public boolean isDirty() {
        return UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.getValue() != CHECK_BOX.isSelected();
    }
}


