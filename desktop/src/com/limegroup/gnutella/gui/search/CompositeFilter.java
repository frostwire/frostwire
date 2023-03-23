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
     * Determines whether the specified TableLine
     * can be displayed.
     */
    public boolean allow(SearchResultDataLine line) {
        for (TableLineFilter<SearchResultDataLine> delegate : delegates) {
            if (!delegate.allow(line)) {
                return false;
            }
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
