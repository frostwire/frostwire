/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import com.frostwire.gui.searchfield.GoogleSearchField;
import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.SearchSettings;

import javax.swing.*;

/**
 * @author gubatron
 */
public class AutoSearchClipboardURLsPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Auto-search URLs in Clipboard");
    private final static String DETAILS = I18n.tr("If there is an URL or magnet URL in your system's clipboard FrostWire will automatically paste and start a search with it");
    private final JCheckBox AUTO_SEARCH_CHECK_BOX = new JCheckBox();

    public AutoSearchClipboardURLsPaneItem() {
        super(TITLE, "");
        BoxPanel panel = new BoxPanel();
        LabeledComponent comp = new LabeledComponent(I18n.tr(DETAILS), AUTO_SEARCH_CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        add(panel);
    }

    @Override
    public boolean applyOptions() {
        SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.setValue(AUTO_SEARCH_CHECK_BOX.isSelected());
        ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.setValue(AUTO_SEARCH_CHECK_BOX.isSelected());
        if (!SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue()) {
            GoogleSearchField.eraseLastClipboardSearchQuery();
        }
        return false;
    }

    @Override
    public void initOptions() {
        AUTO_SEARCH_CHECK_BOX.setSelected(SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue());
    }

    @Override
    public boolean isDirty() {
        return false;//SearchSettings.AUTO_SEARCH_CLIPBOARD_URL.getValue() != AUTO_SEARCH_CHECK_BOX.isSelected();
    }
}
