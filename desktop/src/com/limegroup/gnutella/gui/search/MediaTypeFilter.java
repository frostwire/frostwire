/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

import com.frostwire.gui.filters.TableLineFilter;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
final class MediaTypeFilter implements TableLineFilter<SearchResultDataLine> {

    private final NamedMediaType nmt;

    public MediaTypeFilter(NamedMediaType nmt) {
        this.nmt = nmt;
    }

    public NamedMediaType getMediaType() {
        return nmt;
    }

    @Override
    public boolean allow(SearchResultDataLine node) {
        // TODO: refactor this code block
        try {
            // hard coding disable youtube extension
            if (node.getExtension().equals("youtube")) {
                return false;
            }
        } catch (Throwable e) {
            // ignore
        }

        return nmt.equals(node.getNamedMediaType());
    }
}
