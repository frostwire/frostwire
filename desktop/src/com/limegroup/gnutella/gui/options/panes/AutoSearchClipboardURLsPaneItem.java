/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
 *
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
