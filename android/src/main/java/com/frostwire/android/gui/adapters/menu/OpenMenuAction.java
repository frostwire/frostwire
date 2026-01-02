/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 */
public class OpenMenuAction extends MenuAction {
    private final String path;
    private final String mime;
    private final byte fileType;
    private final FWFileDescriptor fd;
    private final int position;

    public OpenMenuAction(Context context, String title, String path, String mime) {
        super(context, R.drawable.contextmenu_icon_open, title, UIUtils.getAppIconPrimaryColor(context));
        this.path = path;
        this.mime = mime;
        this.fileType = -1;
        this.fd = null;
        this.position = -1;
    }

    public OpenMenuAction(Context context, String path, String mime) {
        super(context, R.drawable.contextmenu_icon_open, R.string.open, UIUtils.getAppIconPrimaryColor(context));
        this.path = path;
        this.mime = mime;
        this.fileType = -1;
        this.fd = null;
        this.position = -1;
    }

    public OpenMenuAction(Context context, FWFileDescriptor fwFileDescriptor, int position) {
        super(context, R.drawable.contextmenu_icon_open, R.string.open, UIUtils.getAppIconPrimaryColor(context));
        this.path = fwFileDescriptor.filePath;
        this.mime = fwFileDescriptor.mime;
        this.fileType = fwFileDescriptor.fileType;
        this.fd = fwFileDescriptor;
        this.position = position;
    }

    public OpenMenuAction(Context context, String title, FWFileDescriptor pictureFWFileDescriptor) {
        super(context, R.drawable.contextmenu_icon_picture, title, UIUtils.getAppIconPrimaryColor(context));
        this.path = pictureFWFileDescriptor.filePath;
        this.mime = pictureFWFileDescriptor.mime;
        this.fileType = pictureFWFileDescriptor.fileType;
        this.fd = pictureFWFileDescriptor;
        this.position = -1;
    }

    @Override
    public void onClick(Context context) {
        if (fileType == Constants.FILE_TYPE_RINGTONES) {
            if (MusicUtils.isPlaying()) {
                MusicUtils.playPauseOrResume();
            }
            MusicUtils.playSimple(this.path);
        } else if (fileType == Constants.FILE_TYPE_AUDIO) {
            UIUtils.playEphemeralPlaylist(context, fd);
        } else if (fd != null && "application/x-bittorrent".equals(fd.mime)) {
            String torrentFileUri = UIUtils.getFileUri(context, fd.filePath, false).toString();
            TransferManager.instance().downloadTorrent(torrentFileUri,
                    new HandpickedTorrentDownloadDialogOnFetch((MainActivity) context, false));
        } else {
            UIUtils.openFile(context, path, mime);
        }
    }
}
