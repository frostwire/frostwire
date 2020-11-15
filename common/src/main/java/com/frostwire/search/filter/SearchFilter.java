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
