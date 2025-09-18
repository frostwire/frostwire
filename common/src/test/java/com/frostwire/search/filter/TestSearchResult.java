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

import com.frostwire.licenses.License;
import com.frostwire.search.SearchResult;

/**
 * @author gubatron
 * @author aldenml
 */
class TestSearchResult implements SearchResult, Cloneable {

    private String id;

    private String source;

    public TestSearchResult(String id) {
        this.id = id;

        this.source = "source:" + id;
    }

    @Override
    public String getDisplayName() {
        return "DisplayName:" + id;
    }

    @Override
    public String getDetailsUrl() {
        return "http://" + id + ".com/details";
    }

    @Override
    public long getCreationTime() {
        return 0;
    }

    @Override
    public String getSource() {
        return source;
    }

    public TestSearchResult source(String source) {
        this.source = source;
        return this;
    }

    @Override
    public License getLicense() {
        return null;
    }

    @Override
    public String getThumbnailUrl() {
        return "http://" + id + ".com/thumbnail";
    }

    @Override
    public int uid() {
        return 0;
    }

    @Override
    public TestSearchResult clone() {
        TestSearchResult sr = new TestSearchResult(id);

        sr.source = source;

        return sr;
    }
}
