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

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;

import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.android.gui.views.MenuBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * The first action towards adding a song(s) to a playlist.
 * Populates a menu with an action to add the song(s) to a new Playlist
 * and then for each existing playlist it creates further actions for a next step.
 * <p>
 * Created on 12/18/14.
 *
 * @author gubatron
 * @author aldenml
 */
public final class AddToPlaylistMenuAction extends MenuAction {

    private long[] fds;

    public AddToPlaylistMenuAction(Context context, List<FWFileDescriptor> fds) {
        super(context, R.drawable.contextmenu_icon_playlist_add_dark, R.string.add_to_playlist);
        setFileIdList(fds);
    }

    public AddToPlaylistMenuAction(Context context, long[] fds) {
        super(context, R.drawable.contextmenu_icon_playlist_add_dark, R.string.add_to_playlist);
        this.fds = fds;
    }

    @Override
    public void onClick(Context context) {
        MenuBuilder menuBuilder = new MenuBuilder(new MenuAdapter(getContext(),
                R.string.add_to_playlist,
                getMenuActions()));
        menuBuilder.show();
    }

    private long[] setFileIdList(List<FWFileDescriptor> fdList) {
        fds = new long[fdList.size()];
        for (int i = 0; i < fdList.size(); i++) {
            fds[i] = fdList.get(i).id;
        }
        return fds;
    }

    private List<MenuAction> getMenuActions() {
        List<MenuAction> actions = new ArrayList<>();

        // Create new Playlist
        actions.add(new CreateNewPlaylistMenuAction(getContext(), fds));

        // Add to Playlist[s] available
        List<Playlist> playlists = MusicUtils.getPlaylists(getContext());
        for (int i = 0; i < playlists.size(); i++) {
            final Playlist playlist = playlists.get(i);
            actions.add(new AddToThisPlaylistMenuAction(getContext(), playlist.mPlaylistId, playlist.mPlaylistName, fds));
        }

        return actions;
    }
}
