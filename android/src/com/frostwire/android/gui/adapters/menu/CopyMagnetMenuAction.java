/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.TorrentInfo;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class CopyMagnetMenuAction extends CopyToClipboardMenuAction {

    private final String filePath;
    private final boolean magnet;

    public CopyMagnetMenuAction(Context context, int drawable, int actionNameId
            , int messageId, String filePath, boolean magnet) {
        super(context, drawable, actionNameId, messageId, null);
        this.filePath = filePath;
        this.magnet = magnet;
    }

    public CopyMagnetMenuAction(Context context, int drawable, int actionNameId
            , int messageId, String filePath) {
        this(context, drawable, actionNameId, messageId, filePath, true);
    }

    @Override
    protected Object getData() {
        return readInfoFromTorrent(filePath, magnet);
    }

    // TODO: this will not work with SAF files
    private static String readInfoFromTorrent(String torrent, boolean magnet) {
        if (torrent == null) {
            return "";
        }

        String result = "";

        try {
            TorrentInfo ti = new TorrentInfo(new File(torrent));

            if (magnet) {
                result = ti.makeMagnetUri() + BTEngine.getInstance().magnetPeers();
            } else {
                result = ti.infoHash().toString();
            }
        } catch (Throwable e) {
            // not using log, since this method should be moved to a better place
            // with it's LOG
            e.printStackTrace();
        }

        return result;
    }
}
