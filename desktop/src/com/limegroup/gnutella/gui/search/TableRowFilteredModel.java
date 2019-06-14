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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.filters.TableLineFilter;
import com.limegroup.gnutella.settings.SearchSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters out certain rows from the data model.
 *
 * @author Sumeet Thadani, Sam Berlin
 */
public class TableRowFilteredModel extends ResultPanelModel {
    /**
     *
     */
    private static final long serialVersionUID = -7810977044778830969L;
    /**
     * A list of all filtered results.
     */
    private final List<SearchResultDataLine> HIDDEN;
    /**
     * The filter to use in this row filter.
     */
    private final TableLineFilter<SearchResultDataLine> FILTER;
    /**
     * The Junk Filter
     */
    private TableLineFilter<SearchResultDataLine> junkFilter = AllowFilter.instance();
    private int _numResults;

    /**
     * Constructs a TableRowFilter with the specified TableLineFilter.
     */
    public TableRowFilteredModel(TableLineFilter<SearchResultDataLine> f) {
        super();
        if (f == null) {
            throw new NullPointerException("null filter");
        }
        FILTER = f;
        HIDDEN = new ArrayList<>();
        _numResults = 0;
    }

    /**
     * Returns true if Table is sorted which means either
     * it is really sorted OR 'move junk to bottom' is
     * selected which is also some kind of sorting!
     */
    public boolean isSorted() {
        return super.isSorted() || SearchSettings.moveJunkToBottom();
    }

    /**
     * Determines whether or not this line should be added.
     */
    public int add(SearchResultDataLine tl, int row) {
        boolean isNotJunk = junkFilter.allow(tl);
        boolean allow = allow(tl);
        if (isNotJunk || !SearchSettings.hideJunk()) {
            if (allow) {
                return super.add(tl, row);
            } else {
                HIDDEN.add(tl);
                _numResults += 1;
            }
        } else {
            _numResults += 1;
        }
        return -1;
    }

    /**
     * Intercepts to clear the hidden map.
     */
    protected void simpleClear() {
        _numResults = 0;
        HIDDEN.clear();
        super.simpleClear();
    }

    @Override
    public void clear() {
        _numResults = 0;
        HIDDEN.clear();
        super.clear();
    }

    /**
     * Notification that the filters have changed.
     */
    void filtersChanged() {
        rebuild();
        fireTableDataChanged();
    }

    /**
     * Sets the Junk Filter. Pass null as argument to disable the filter
     */
    void setJunkFilter(TableLineFilter<SearchResultDataLine> junkFilter) {
        if (junkFilter != null) {
            this.junkFilter = junkFilter;
        } else {
            this.junkFilter = AllowFilter.instance();
        }
    }

    /**
     * Determines whether or not the specified line is allowed by the filter.
     */
    private boolean allow(SearchResultDataLine line) {
        return FILTER.allow(line);
    }

    /**
     * Rebuilds the internal map to denote a new filter.
     */
    private void rebuild() {
        List<SearchResultDataLine> existing = new ArrayList<>(_list);
        List<SearchResultDataLine> hidden = new ArrayList<>(HIDDEN);
        simpleClear();
        // For stuff in _list, we can just re-add the DataLines as-is.
        if (isSorted()) {
            for (SearchResultDataLine searchResultDataLine : existing) {
                addSorted(searchResultDataLine);
            }
        } else {
            for (SearchResultDataLine searchResultDataLine : existing) {
                add(searchResultDataLine);
            }
        }
        // Merge the hidden TableLines
        Map<String, SearchResultDataLine> mergeMap = new HashMap<>();
        for (SearchResultDataLine tl : hidden) {
            //SearchResult sr = tl.getInitializeObject();
            //String urn = sr.getHash();
            if (isSorted()) {
                addSorted(tl);
            } else {
                add(tl);
            }
            //            TableLine tableLine = mergeMap.get(urn);
            //            if (tableLine == null) {
            //                mergeMap.put(urn, tl); // re-use TableLines
            //            } else {
            //                tableLine.addNewResult(sr, METADATA);
            //            }
        }
        // And add them
        if (isSorted()) {
            for (SearchResultDataLine line : mergeMap.values())
                addSorted(line);
        } else {
            for (SearchResultDataLine line : mergeMap.values())
                add(line);
        }
    }

    private int getFilteredResults() {
        return super.getTotalResults();
    }

    public int getTotalResults() {
        return getFilteredResults() + _numResults;
    }
}
