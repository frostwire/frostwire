/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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
