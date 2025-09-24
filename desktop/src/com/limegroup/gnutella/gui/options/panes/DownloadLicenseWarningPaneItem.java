/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
     * `AbstractPaneItem`.
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
     * Defines the abstract method in `AbstractPaneItem`.
     * <p>
     * Sets the options for the fields in this `PaneItem` when the
     * window is shown.
     */
    @SuppressWarnings("unused")
    public void initOptions() {
        skipWarning = QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getValue();
        CHECK_BOX.setSelected(DialogOption.parseInt(skipWarning) != DialogOption.YES);
    }

    /**
     * Defines the abstract method in `AbstractPaneItem`.
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
