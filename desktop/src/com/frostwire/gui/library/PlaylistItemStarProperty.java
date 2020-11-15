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
 * Wraps the current DataLine to be displayed in the table to pass it to the
 *
 * @author gubatron
 * @author aldenml
 */
class PlaylistItemStarProperty extends PlaylistItemProperty<PlaylistItemStarProperty> {
    PlaylistItemStarProperty(LibraryPlaylistsTableDataLine line, boolean exists) {
        super(line, exists);
    }

    @Override
    public int compareTo(PlaylistItemStarProperty o) {
        return Boolean.compare(line.getInitializeObject().isStarred(), o.line.getInitializeObject().isStarred());
    }

    @Override
    public String getStringValue() {
        return "";
    }
}
