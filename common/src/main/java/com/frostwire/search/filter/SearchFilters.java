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

package com.frostwire.search.filter;

import com.frostwire.search.SearchResult;

import java.util.Comparator;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchFilters {
    private SearchFilters() {
    }

    public static SearchFilter add(SearchFilter f1, SearchFilter f2) {
        return new AddFilter(f1, f2);
    }

    private static final class AddFilter implements SearchFilter {
        private final SearchFilter f1;
        private final SearchFilter f2;

        public AddFilter(SearchFilter f1, SearchFilter f2) {
            this.f1 = f1;
            this.f2 = f2;
        }

        @Override
        public FilterKey key(SearchResult sr) {
            return new AddKey(f1.key(sr), f2.key(sr));
        }

        @Override
        public boolean accept(SearchResult sr) {
            return f1.accept(sr) && f2.accept(sr);
        }

        @Override
        public Comparator<SearchResult> comparator() {
            return new AddComparator(f1.comparator(), f2.comparator());
        }
    }

    private static final class AddKey implements FilterKey {
        private final FilterKey k1;
        private final FilterKey k2;

        public AddKey(FilterKey k1, FilterKey k2) {
            this.k1 = k1;
            this.k2 = k2;
        }

        @Override
        public String display() {
            return k1.display() + ", " + k2.display();
        }

        @Override
        public int compareTo(FilterKey o) {
            if (!(o instanceof AddKey)) {
                return -1;
            }
            int n = k1.compareTo(((AddKey) o).k1);
            return n != 0 ? n : k2.compareTo(((AddKey) o).k2);
        }
    }

    private static final class AddComparator implements Comparator<SearchResult> {
        private final Comparator<SearchResult> c1;
        private final Comparator<SearchResult> c2;

        public AddComparator(Comparator<SearchResult> c1, Comparator<SearchResult> c2) {
            this.c1 = c1;
            this.c2 = c2;
        }

        @Override
        public int compare(SearchResult o1, SearchResult o2) {
            int n = c1.compare(o1, o2);
            return n != 0 ? n : c2.compare(o1, o2);
        }
    }
}
