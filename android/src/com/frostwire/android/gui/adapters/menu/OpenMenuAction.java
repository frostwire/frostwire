/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
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

import androidx.core.content.ContextCompat;

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
        super(context, R.drawable.contextmenu_icon_open, title, getTintColor(context)); // Corrected super() call
        this.path = path;
        this.mime = mime;
        this.fileType = -1;
        this.fd = null;
        this.position = -1;
    }

    public OpenMenuAction(Context context, String path, String mime) {
        super(context, R.drawable.contextmenu_icon_open, R.string.open, getTintColor(context)); // Corrected super() call
        this.path = path;
        this.mime = mime;
        this.fileType = -1;
        this.fd = null;
        this.position = -1;
    }

    public OpenMenuAction(Context context, FWFileDescriptor fwFileDescriptor, int position) {
        super(context, R.drawable.contextmenu_icon_open, R.string.open, getTintColor(context)); // Corrected super() call
        this.path = fwFileDescriptor.filePath;
        this.mime = fwFileDescriptor.mime;
        this.fileType = fwFileDescriptor.fileType;
        this.fd = fwFileDescriptor;
        this.position = position;
    }

    public OpenMenuAction(Context context, String title, FWFileDescriptor pictureFWFileDescriptor) {
        super(context, R.drawable.contextmenu_icon_picture, title, getTintColor(context)); // Corrected super() call
        this.path = pictureFWFileDescriptor.filePath;
        this.mime = pictureFWFileDescriptor.mime;
        this.fileType = pictureFWFileDescriptor.fileType;
        this.fd = pictureFWFileDescriptor;
        this.position = -1;
    }

    // Method to retrieve tint color
    private static int getTintColor(Context context) {
        return ContextCompat.getColor(context, R.color.app_icon_primary);
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
