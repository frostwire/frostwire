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

package com.frostwire.search;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractSearchResult implements SearchResult {
    @Override
    public License getLicense() {
        return Licenses.UNKNOWN;
    }

    @Override
    public long getCreationTime() {
        return -1;
    }

    @Override
    public String toString() {
        return getDetailsUrl();
    }

    @Override
    public String getThumbnailUrl() {
        return null;
    }

    public int getViewCount() {
        return -1;
    }
}
