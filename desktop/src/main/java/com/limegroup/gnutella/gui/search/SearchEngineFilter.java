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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.filters.TableLineFilter;

import javax.swing.*;
import java.util.Map;

class SearchEngineFilter implements TableLineFilter<SearchResultDataLine> {
    private final Map<SearchEngine, JCheckBox> engineCheckboxes;

    public SearchEngineFilter(Map<SearchEngine, JCheckBox> engineCheckboxes) {
        this.engineCheckboxes = engineCheckboxes;
    }

    public boolean allow(SearchResultDataLine node) {
        boolean result = false;
        final SearchEngine searchEngine = node.getSearchEngine();
        if (searchEngine.getId().equals(SearchEngine.SearchEngineID.TELLURIDE_ID) || searchEngine.getId().equals(SearchEngine.SearchEngineID.YT_ID)) {
            return true;
        }
        JCheckBox box = engineCheckboxes.get(searchEngine);
        if (box != null) {
            result = searchEngine.isEnabled() && box.isEnabled() && box.isSelected();
        }
        return result;
    }
}