/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.search.filter;

import com.frostwire.search.SearchResult;

import java.util.Comparator;

/**
 * @author gubatron
 * @author aldenml
 */
public interface SearchFilter {
    SearchFilter NONE = new SearchFilter() {
        private Comparator<SearchResult> CMP = (o1, o2) -> 0;

        @Override
        public FilterKey key(SearchResult sr) {
            return FilterKey.NULL;
        }

        @Override
        public boolean accept(SearchResult sr) {
            return true;
        }

        @Override
        public Comparator<SearchResult> comparator() {
            return CMP;
        }
    };

    FilterKey key(SearchResult sr);

    boolean accept(SearchResult sr);

    Comparator<SearchResult> comparator();
}
