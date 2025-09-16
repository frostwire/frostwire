/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.core.player;

import com.frostwire.android.core.FWFileDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Playlist based on a given list of file descriptors.
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class EphemeralPlaylist implements Playlist {

    private final List<PlaylistItem> items;

    private int currentIndex;

    public EphemeralPlaylist(List<FWFileDescriptor> fds) {
        this.items = new ArrayList<>();

        for (FWFileDescriptor fd : fds) {
            this.items.add(new PlaylistItem(fd));
        }
        
        Collections.sort(this.items, (a, b) -> {
            if (a.getFD().dateAdded == b.getFD().dateAdded) {
                return 0;
            }
            return (a.getFD().dateAdded > b.getFD().dateAdded) ? -1 : 1;
        });

        this.currentIndex = -1;
    }

    public void setNextItem(PlaylistItem playlistItem) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).equals(playlistItem)) {
                currentIndex = index;
                break;
            }
        }
    }

    @Override
    public List<PlaylistItem> getItems() {
        return items;
    }
    
    @Override
    public PlaylistItem getCurrentItem() {
        if (currentIndex >= 0) {
            return items.get(currentIndex);
        }
        return null;
    }
}
