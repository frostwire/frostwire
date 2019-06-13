/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
