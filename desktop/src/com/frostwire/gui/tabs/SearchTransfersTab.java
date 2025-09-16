/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
package com.frostwire.gui.tabs;

import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UISettings;

import javax.swing.*;

/**
 * Created on 6/15/16.
 *
 * @author gubatron
 * @author aldenml
 */
public final class SearchTransfersTab extends AbstractTab {
    private final JSplitPane searchDownloadSplitPane;

    public SearchTransfersTab(SearchTab searchTab, TransfersTab transfersTab) {
        // It will look like the SearchTab.
        super(I18n.tr("Search"),
                I18n.tr("Search and Download Files from the Internet."),
                "search_tab");
        searchDownloadSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                searchTab.getComponent(),
                transfersTab.getComponent());
        searchDownloadSplitPane.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeMediator.LIGHT_BORDER_COLOR));
        searchDownloadSplitPane.setContinuousLayout(true);
        searchDownloadSplitPane.setResizeWeight(0.6);
        searchDownloadSplitPane.setDividerLocation(UISettings.UI_TRANSFERS_DIVIDER_LOCATION.getValue());
        searchDownloadSplitPane.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, evt -> {
            JSplitPane splitPane = (JSplitPane) evt.getSource();
            int current = splitPane.getDividerLocation();
            if (splitPane.getSize().height - current < BTDownloadMediator.MIN_HEIGHT) {
                splitPane.setDividerLocation(splitPane.getSize().height - BTDownloadMediator.MIN_HEIGHT);
            }
            UISettings.UI_TRANSFERS_DIVIDER_LOCATION.setValue(splitPane.getDividerLocation());
        });
    }

    @Override
    public JComponent getComponent() {
        return searchDownloadSplitPane;
    }

    public void setDividerLocation(int newLocation) {
        searchDownloadSplitPane.setDividerLocation(newLocation);
    }
}
