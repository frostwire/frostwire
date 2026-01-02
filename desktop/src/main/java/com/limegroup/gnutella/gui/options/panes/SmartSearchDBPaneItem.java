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
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.gui.util.DesktopParallelExecutor;
import com.limegroup.gnutella.settings.SearchSettings;

import javax.swing.*;
import java.awt.*;

public final class SmartSearchDBPaneItem extends AbstractPaneItem {
    public final static String TITLE = I18n.tr("Smart Search");
    public final static String LABEL = I18n.tr("The Smart Search database is used to speed up individual file searches, it's how FrostWire remembers information about .torrent contents.");
    private final JLabel _numTorrentsLabel;
    private final JCheckBox smartSearchEnabled;
    private long _numTorrents = 0;

    /**
     * The constructor constructs all of the elements of this
     * `AbstractPaneItem`.
     *
     */
    public SmartSearchDBPaneItem() {
        super(TITLE, LABEL);
        Font font = new Font("dialog", Font.BOLD, 12);
        _numTorrentsLabel = new JLabel();
        _numTorrentsLabel.setFont(font);
        smartSearchEnabled = new JCheckBox(I18n.tr("Enable Smart Search"), SearchSettings.SMART_SEARCH_ENABLED.getValue());
        LabeledComponent numTorrentsComp = new LabeledComponent(I18n.tr("Total torrents indexed"), _numTorrentsLabel);
        add(getVerticalSeparator());
        JButton resetButton = new JButton(I18n.tr("Reset Smart Search Database"));
        resetButton.addActionListener(e -> GUIMediator.safeInvokeLater(() -> {
            resetSmartSearchDB();
            initOptions();
        }));
        add(smartSearchEnabled);
        add(getVerticalSeparator());
        add(numTorrentsComp.getComponent());
        add(getVerticalSeparator());
        add(resetButton);
    }

    protected void resetSmartSearchDB() {
        DialogOption showConfirmDialog = GUIMediator.showYesNoMessage(I18n.tr("If you continue you will erase all the information related to\n{0} torrents that FrostWire has learned to speed up your search results.\nDo you wish to continue?", _numTorrents),
                I18n.tr("Are you sure?"), JOptionPane.QUESTION_MESSAGE);
        if (showConfirmDialog == DialogOption.YES) {
            SearchMediator.instance().clearCache();
        }
    }

    /**
     * Defines the abstract method in `AbstractPaneItem`.<p>
     * <p>
     * Sets the options for the fields in this `PaneItem` when the
     * window is shown.
     */
    public void initOptions() {
        _numTorrentsLabel.setText("...");
        DesktopParallelExecutor.execute(() -> {
            _numTorrents = SearchMediator.instance().getTotalTorrents();
            GUIMediator.safeInvokeLater(() -> {
                _numTorrentsLabel.setText(String.valueOf(_numTorrents));
                smartSearchEnabled.setSelected(SearchSettings.SMART_SEARCH_ENABLED.getValue());
            });
        });
    }

    /**
     * Defines the abstract method in `AbstractPaneItem`.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        SearchSettings.SMART_SEARCH_ENABLED.setValue(smartSearchEnabled.isSelected());
        return true;
    }

    public boolean isDirty() {
        return false;
    }
}
