/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, 2013, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.library;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
class PlaylistItemIntProperty extends PlaylistItemProperty<PlaylistItemIntProperty> {

    private final String stringValue;
    private final int intValue;

    public PlaylistItemIntProperty(LibraryPlaylistsTableDataLine line, int value, boolean playing, boolean exists) {
        super(line, playing, exists);
        intValue = value;
        stringValue = value > 0 ? String.valueOf(value) : "";
    }

    public PlaylistItemIntProperty(LibraryPlaylistsTableDataLine line, String stringValue, int intValue, boolean playing, boolean exists) {
        super(line, playing, exists);
        this.intValue = intValue;
        this.stringValue = stringValue;
    }

    @Override
    public int compareTo(PlaylistItemIntProperty o) {
        return Integer.valueOf(intValue).compareTo(o.intValue);
    }

    @Override
    public String getStringValue() {
        return stringValue;
    }
}
