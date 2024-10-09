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

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
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
        super(context,
                R.drawable.contextmenu_icon_add_to_existing_playlist_dark,
                playlistName,
                UIUtils.getAppIconPrimaryColor(context));
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
