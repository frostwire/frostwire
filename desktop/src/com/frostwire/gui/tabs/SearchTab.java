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
 * This class constructs the search tab, including all UI elements.
 */
public final class SearchTab extends AbstractTab {
    public SearchTab() {
        super(I18n.tr("Search"),
                I18n.tr("Search and Download Files from the Internet."),
                "search_tab");
    }

    public JComponent getComponent() {
        return SearchMediator.getResultComponent();
    }
}
