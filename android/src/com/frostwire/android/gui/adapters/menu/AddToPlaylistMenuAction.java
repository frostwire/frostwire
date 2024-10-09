/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;

import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.util.UIUtils;
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
 * @author marcelinkaaa
 */
public final class AddToPlaylistMenuAction extends MenuAction {

    private long[] fds;

    public AddToPlaylistMenuAction(Context context, List<FWFileDescriptor> fds) {
        super(context, R.drawable.contextmenu_icon_playlist_add_dark, R.string.add_to_playlist, UIUtils.getAppIconPrimaryColor(context));
        setFileIdList(fds);
    }

    public AddToPlaylistMenuAction(Context context, long[] fds) {
        super(context, R.drawable.contextmenu_icon_playlist_add_dark, R.string.add_to_playlist, UIUtils.getAppIconPrimaryColor(context));
        this.fds = fds;
    }

    @Override
    public void onClick(Context context) {
        MenuBuilder menuBuilder = new MenuBuilder(new MenuAdapter(getContext(),
                R.string.add_to_playlist,
                getMenuActions()));
        menuBuilder.show();
    }

    private void setFileIdList(List<FWFileDescriptor> fdList) {
        fds = new long[fdList.size()];
        for (int i = 0; i < fdList.size(); i++) {
            fds[i] = fdList.get(i).id;
        }
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
