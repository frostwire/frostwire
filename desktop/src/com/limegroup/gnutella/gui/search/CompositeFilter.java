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

import java.util.ArrayList;
import java.util.List;

/**
 * A filter that takes multiple other filters.
 */
class CompositeFilter implements TableLineFilter<SearchResultDataLine> {
    /**
     * The underlying filters.
     */
    private final List<TableLineFilter<SearchResultDataLine>> delegates;

    /**
     * Creates a new CompositeFilter of the specified depth.
     * By default, all the filters are an AllowFilter.
     */
    CompositeFilter(int depth) {
        this.delegates = new ArrayList<>(depth);
        for (int i = 0; i < depth; i++) {
            this.delegates.add(null);
        }
        reset();
    }

    /**
     * Resets this filter to all AllowFilters.
     */
    public void reset() {
        for (int i = 0; i < delegates.size(); i++) {
            delegates.set(i, AllowFilter.instance());
        }
    }

    /**
     * Determines whether or not the specified TableLine
     * can be displayed.
     */
    public boolean allow(SearchResultDataLine line) {
        for (TableLineFilter<SearchResultDataLine> delegate : delegates) {
            if (!delegate.allow(line))
                return false;
        }
        return true;
    }

    /**
     * Sets the filter at the specified depth.
     */
    boolean setFilter(int depth, TableLineFilter<SearchResultDataLine> filter) {
        if (filter == this) {
            throw new IllegalArgumentException("Filter must not be composed of itself");
        }
        if (delegates.get(depth).equals(filter))
            return false;
        else {
            delegates.set(depth, filter);
            return true;
        }
    }
}
