/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.tabs;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchMediator;

import javax.swing.*;

/**
 * This class constructs the search/download tab, including all UI elements.
 */
public final class SearchDownloadTab extends AbstractTab {

    /**
     * Split pane for the split between the search and download sections
     * of the window.
     */
    //private final JSplitPane searchDownloadSplitPane;

    public SearchDownloadTab() {
        super(I18n.tr("Search"),
              I18n.tr("Search and Download Files from the Internet."),
              "search_tab");
        /*
        searchDownloadSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, SearchMediator.getResultComponent(), downloadMediator.getComponent());
        searchDownloadSplitPane.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeMediator.LIGHT_BORDER_COLOR));
        searchDownloadSplitPane.setContinuousLayout(true);
        searchDownloadSplitPane.setResizeWeight(0.6);
        searchDownloadSplitPane.setDividerLocation(UISettings.UI_TRANSFERS_DIVIDER_LOCATION.getValue());

        searchDownloadSplitPane.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                JSplitPane splitPane = (JSplitPane) evt.getSource();
                int current = splitPane.getDividerLocation();
                if (splitPane.getSize().height - current < BTDownloadMediator.MIN_HEIGHT) {
                    splitPane.setDividerLocation(splitPane.getSize().height - BTDownloadMediator.MIN_HEIGHT);
                }
                UISettings.UI_TRANSFERS_DIVIDER_LOCATION.setValue(splitPane.getDividerLocation());
            }
        });
        */
    }

    public JComponent getComponent() {
        //return searchDownloadSplitPane;
        return SearchMediator.getResultComponent();
    }
    
    public void setDividerLocation(int newLocation) {
        //searchDownloadSplitPane.setDividerLocation(newLocation);
    }
}
