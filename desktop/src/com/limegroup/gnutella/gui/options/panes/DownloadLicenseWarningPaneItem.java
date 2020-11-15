/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
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

import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.QuestionsHandler;

import javax.swing.*;

/**
 * This class defines the pane in the options window that allows
 * the user to receive or not receive a warning about downloading
 * a file without a license.
 */
public class DownloadLicenseWarningPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("License Warning");
    private final static String LABEL = I18n.tr("You can choose whether to be warned about downloading a file without a license.");
    /**
     * Constant for the check box that specifies whether or not downloads
     * should be automatically cleared.
     */
    private final JCheckBox CHECK_BOX = new JCheckBox();
    /**
     * The stored value to allow rolling back changes.
     */
    private int skipWarning;

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     */
    @SuppressWarnings("unused")
    public DownloadLicenseWarningPaneItem() {
        super(TITLE, LABEL);

        /*
          Constant for the key of the locale-specific <code>String</code> for the
          download pane check box label in the options window.
         */
        String CHECK_BOX_LABEL = I18n.tr("Show License Warning:");
        LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL,
                CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(comp.getComponent());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    @SuppressWarnings("unused")
    public void initOptions() {
        skipWarning = QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getValue();
        CHECK_BOX.setSelected(DialogOption.parseInt(skipWarning) != DialogOption.YES);
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    @SuppressWarnings("unused")
    public boolean applyOptions() {
        final boolean skip = !CHECK_BOX.isSelected();
        if (skip) {
            if (DialogOption.parseInt(skipWarning) != DialogOption.YES)
                QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.setValue(DialogOption.YES.toInt());
        } else {
            if (DialogOption.parseInt(skipWarning) == DialogOption.YES)
                QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.setValue(0);
        }
        return false;
    }

    @SuppressWarnings("unused")
    public boolean isDirty() {
        final boolean skip = !CHECK_BOX.isSelected();
        if (skip)
            return DialogOption.parseInt(skipWarning) != DialogOption.YES;
        else
            return DialogOption.parseInt(skipWarning) == DialogOption.YES;
    }
}
