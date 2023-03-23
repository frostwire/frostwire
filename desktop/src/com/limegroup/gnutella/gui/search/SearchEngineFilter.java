/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.

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