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

/**
 * Filter denoting that anything is allowed.
 */
class AllowFilter implements TableLineFilter<SearchResultDataLine> {
    /**
     * The sole instance that can be returned, for convenience.
     */
    private static final AllowFilter INSTANCE = new AllowFilter();

    /**
     * Returns a reusable instance of AllowFilter.
     */
    public static AllowFilter instance() {
        return INSTANCE;
    }

    /**
     * Returns true.
     */
    public boolean allow(SearchResultDataLine line) {
        return true;
    }

    public boolean equals(Object o) {
        return (o instanceof AllowFilter);
    }
}