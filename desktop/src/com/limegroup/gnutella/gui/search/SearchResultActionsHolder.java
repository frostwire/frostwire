/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
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

import com.limegroup.gnutella.gui.tables.AbstractTableMediator;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchResultActionsHolder implements Comparable<SearchResultActionsHolder> {
    private final UISearchResult sr;
    private final String displayName;

    public SearchResultActionsHolder(final UISearchResult sr) {
        this.sr = sr;
        this.displayName = sr.getDisplayName();
    }

    public int compareTo(SearchResultActionsHolder o) {
        return AbstractTableMediator.compare(sr.getDisplayName(), o.sr.getDisplayName());
    }

    public UISearchResult getSearchResult() {
        return sr;
    }

    public String toString() {
        return displayName;
    }
}
