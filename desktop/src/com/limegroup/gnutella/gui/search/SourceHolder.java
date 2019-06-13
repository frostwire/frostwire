/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
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
        this.sourceNameHTML = "<html><div width=\"1000000px\"><nobr><a href=\"#\">" + sourceName + "</a></nobr></div></html>";
        this.sourceURL = uiSearchResult.getSearchResult().getDetailsUrl();
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