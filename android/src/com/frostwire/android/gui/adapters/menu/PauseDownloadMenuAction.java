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

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.transfers.BittorrentDownload;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class PauseDownloadMenuAction extends MenuAction {

    private final BittorrentDownload download;

    public PauseDownloadMenuAction(Context context, BittorrentDownload download) {
        super(context, R.drawable.contextmenu_icon_pause_transfer, R.string.pause_torrent_menu_action, UIUtils.getAppIconPrimaryColor(context));
        this.download = download;
    }

    @Override
    public void onClick(Context context) {
        if (!download.isPaused()) {
            download.pause();
            if (context instanceof TimerObserver) {
                ((TimerObserver) context).onTime();
            }
        }
    }
}
