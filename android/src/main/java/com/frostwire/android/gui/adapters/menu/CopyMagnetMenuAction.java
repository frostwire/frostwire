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

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.info_hash_t;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
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
                info_hash_t infoHashT = ti.infoHashType();
                if (infoHashT.has_v2()) {
                    result = ti.infoHashV2().toString();
                } else if (infoHashT.has_v1()) {
                    result = ti.infoHashV1().toString();
                }
            }
        } catch (Throwable e) {
            // not using log, since this method should be moved to a better place
            // with it's LOG
            e.printStackTrace();
        }

        return result;
    }
}
