/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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