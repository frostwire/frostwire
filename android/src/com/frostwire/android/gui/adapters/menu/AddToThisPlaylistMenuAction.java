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

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.MenuAction;

/**
 * Created on 12/18/14.
 * <p>
 * This action is built to show the name of a playlist, when used it will
 * add the given file descriptors to the current playlist.
 *
 * @author gubatron
 * @author aldenml
 */
public final class AddToThisPlaylistMenuAction extends MenuAction {

    private final long playlistId;
    private final long[] fileDescriptors;

    public AddToThisPlaylistMenuAction(Context context, long playlistId, String playlistName, long[] fileDescriptors) {
        super(context, R.drawable.contextmenu_icon_add_to_existing_playlist_dark, playlistName);
        this.playlistId = playlistId;
        this.fileDescriptors = fileDescriptors;
    }

    @Override
    public void onClick(Context context) {
        try {
            MusicUtils.addToPlaylist(context, fileDescriptors, playlistId);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
