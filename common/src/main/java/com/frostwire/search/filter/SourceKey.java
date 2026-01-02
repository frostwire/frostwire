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

/**
 * @author gubatron
 * @author aldenml
 */
public final class SourceKey implements FilterKey {
    private final String source;
    private final int ordinal;
    private String display;

    public SourceKey(String source, int ordinal) {
        this.source = source;
        this.ordinal = ordinal;
        this.display = source;
    }

    public String source() {
        return source;
    }

    @Override
    public String display() {
        return display;
    }

    public void display(String value) {
        this.display = value;
    }

    @Override
    public int compareTo(FilterKey o) {
        if (!(o instanceof SourceKey)) {
            return -1;
        }
        int y = ((SourceKey) o).ordinal;
        return Integer.compare(ordinal, y);
    }
}
