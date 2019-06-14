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
package com.frostwire.gui.library;

/**
 * @author gubatron
 * @author aldenml
 */
final class PlaylistItemBitRateProperty extends PlaylistItemIntProperty {
    PlaylistItemBitRateProperty(LibraryPlaylistsTableDataLine line, String stringValue, boolean exists) {
        super(line, getStringValue(stringValue), getIntValue(stringValue), exists);
    }

    private static String getStringValue(String stringValue) {
        return stringValue.replace("~", "").trim();
    }

    private static int getIntValue(String stringValue) {
        // using Integer.MAX_VALUE to put entries with no bitrate at the bottom of the list
        String s = stringValue.toLowerCase().replace("kbps", "").replace("~", "").trim();
        int value = Integer.MAX_VALUE;
        if (s.length() > 0) {
            try {
                value = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return value;
    }
}
