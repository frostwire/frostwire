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
import com.limegroup.gnutella.settings.iTunesSettings;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class iTunesPreferencePaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Importing");
    private final static String LABEL = I18n.tr("You can have FrostWire import newly downloaded songs into iTunes.");
    /**
     * Constant for the check box that specifies whether or not downloads
     * should be automatically cleared.
     */
    private final JCheckBox CHECK_BOX = new JCheckBox();

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     *
     */
    public iTunesPreferencePaneItem() {
        super(TITLE, LABEL);
        String CHECK_BOX_LABEL = I18n.tr("Enable iTunes importing:");
        LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL, CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(comp.getComponent());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.
     * <p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        CHECK_BOX.setSelected(iTunesSettings.ITUNES_SUPPORT_ENABLED.getValue());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        iTunesSettings.ITUNES_SUPPORT_ENABLED.setValue(CHECK_BOX.isSelected());
        return false;
    }

    public boolean isDirty() {
        return iTunesSettings.ITUNES_SUPPORT_ENABLED.getValue() != CHECK_BOX.isSelected();
    }
}
