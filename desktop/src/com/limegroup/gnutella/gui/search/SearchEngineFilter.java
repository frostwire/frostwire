package com.limegroup.gnutella.gui.search;

import java.util.Map;

import javax.swing.JCheckBox;

import com.frostwire.gui.filters.TableLineFilter;

class SearchEngineFilter implements TableLineFilter<SearchResultDataLine> {

    private final Map<SearchEngine,JCheckBox> engineCheckboxes;

    public SearchEngineFilter(Map<SearchEngine,JCheckBox> engineCheckboxes) {
        this.engineCheckboxes = engineCheckboxes;
    }

    public boolean allow(SearchResultDataLine node) {
        boolean result = false;
        final SearchEngine searchEngine = node.getSearchEngine();
        JCheckBox box = engineCheckboxes.get(searchEngine);
        if (box != null) {
            result = searchEngine.isEnabled() && box.isEnabled() && box.isSelected();
        }
        return result;
    }
}