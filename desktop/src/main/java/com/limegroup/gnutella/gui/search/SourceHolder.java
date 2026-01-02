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

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.tables.AbstractTableMediator;

/**
 * Holds the data for a search result's Source.
 *
 * @author gubatron
 */
public class SourceHolder implements Comparable<SourceHolder> {
    private final UISearchResult uiSearchResult;
    private final String sourceNameHTML;
    private final String sourceName;
    private final String sourceURL;

    SourceHolder(UISearchResult uiSearchResult) {
        this.uiSearchResult = uiSearchResult;
        this.sourceName = uiSearchResult.getSource();

        if (uiSearchResult.getSearchResult().getDetailsUrl() != null) {
            this.sourceNameHTML = "<html><div width=\"1000000px\"><nobr><a href=\"#\">" + sourceName + "</a></nobr></div></html>";
            this.sourceURL = uiSearchResult.getSearchResult().getDetailsUrl();
        } else {
            this.sourceNameHTML = "<html><div width=\"1000000px\"><nobr>" + sourceName + "</nobr></div></html>";
            this.sourceURL = null;
        }
    }

    @Override
    public int compareTo(SourceHolder o) {
        return AbstractTableMediator.compare(sourceName, o.getSourceName());
    }

    String getSourceName() {
        return sourceName;
    }

    String getSourceNameHTML() {
        return sourceNameHTML;
    }

    public UISearchResult getUISearchResult() {
        return uiSearchResult;
    }

    @Override
    public String toString() {
        return sourceName + " [" + sourceURL + "]";
    }
}