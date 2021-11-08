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

import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.SearchSettings;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class DetailsPaneItem extends AbstractPaneItem {
    private final JCheckBox DETAILS_CHECK_BOX = new JCheckBox();
    private final static String TITLE = I18n.tr("Details Page");
    private final static String DETAILS = I18n.tr("Show details web page after a download starts.");

    public DetailsPaneItem() {
        super(TITLE, "");
        BoxPanel panel = new BoxPanel();
        LabeledComponent comp = new LabeledComponent(I18n.tr(DETAILS), DETAILS_CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        add(panel);
    }

    @Override
    public boolean applyOptions() {
        SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.setValue(DETAILS_CHECK_BOX.isSelected());
        return false;
    }

    @Override
    public void initOptions() {
        DETAILS_CHECK_BOX.setSelected(SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.getValue());
    }

    @Override
    public boolean isDirty() {
        return SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.getValue() != DETAILS_CHECK_BOX.isSelected();
    }
}
