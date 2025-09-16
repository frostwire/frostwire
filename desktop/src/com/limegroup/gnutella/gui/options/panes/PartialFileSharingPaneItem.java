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

