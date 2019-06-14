/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.library;

/**
 * @author gubatron
 * @author aldenml
 */
class PlaylistItemIntProperty extends PlaylistItemProperty<PlaylistItemIntProperty> {
    private final String stringValue;
    private final int intValue;

    public PlaylistItemIntProperty(LibraryPlaylistsTableDataLine line, int value, boolean exists) {
        super(line, exists);
        intValue = value;
        stringValue = value > 0 ? String.valueOf(value) : "";
    }

    PlaylistItemIntProperty(LibraryPlaylistsTableDataLine line, String stringValue, int intValue, boolean exists) {
        super(line, exists);
        this.intValue = intValue;
        this.stringValue = stringValue;
    }

    @Override
    public int compareTo(PlaylistItemIntProperty o) {
        return Integer.compare(intValue, o.intValue);
    }

    @Override
    public String getStringValue() {
        return stringValue;
    }
}
