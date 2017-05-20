/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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
import android.content.Intent;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.activities.ImageViewerActivity;
import com.frostwire.android.gui.fragments.ImageViewerFragment;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class OpenMenuAction extends MenuAction {
    private final String path;
    private final String mime;
    private final byte fileType;
    private final FileDescriptor fd;

    public OpenMenuAction(Context context, String title, String path, String mime) {
        super(context, R.drawable.contextmenu_icon_open, R.string.open_menu_action, title);
        this.path = path;
        this.mime = mime;
        this.fileType = -1;
        this.fd = null;
    }

    public OpenMenuAction(Context context, String path, String mime) {
        super(context, R.drawable.contextmenu_icon_open, R.string.open);
        this.path = path;
        this.mime = mime;
        this.fileType = -1;
        this.fd = null;
    }

    public OpenMenuAction(Context context, FileDescriptor fileDescriptor) {
        super(context, R.drawable.contextmenu_icon_open, R.string.open);
        this.path = fileDescriptor.filePath;
        this.mime = fileDescriptor.mime;
        this.fileType = fileDescriptor.fileType;
        this.fd = fileDescriptor;
    }


    public OpenMenuAction(Context context, String title, FileDescriptor pictureFileDescriptor) {
        super(context, R.drawable.contextmenu_icon_picture, R.string.open_menu_action, title);
        this.path = pictureFileDescriptor.filePath;
        this.mime = pictureFileDescriptor.mime;
        this.fileType = pictureFileDescriptor.fileType;
        this.fd = pictureFileDescriptor;
    }

    @Override
    protected void onClick(Context context) {
        if (fileType == Constants.FILE_TYPE_PICTURES && fd != null) {
            Intent intent = new Intent(context, ImageViewerActivity.class);
            intent.putExtra(ImageViewerFragment.EXTRA_FILE_DESCRIPTOR, fd.toBundle());
            context.startActivity(intent);
        } else if (fileType == Constants.FILE_TYPE_RINGTONES) {
            if (MusicUtils.isPlaying()) {
                MusicUtils.playOrPause();
            }
            MusicUtils.playSimple(this.path);
        } else if (fileType == Constants.FILE_TYPE_AUDIO) {
            UIUtils.playEphemeralPlaylist(fd);
        } else {
            UIUtils.openFile(context, path, mime, true);
        }
    }
}
