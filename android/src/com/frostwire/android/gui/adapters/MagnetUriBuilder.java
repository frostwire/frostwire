/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Address;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.logging.Logger;
import com.frostwire.transfers.BittorrentDownload;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MagnetUriBuilder {

    private static Logger LOG = Logger.getLogger(MagnetUriBuilder.class);
    private final String torrentFilePath;
    private final BittorrentDownload btDownload;

    MagnetUriBuilder(String torrentFilePath, BittorrentDownload btDownload) {
        this.torrentFilePath = torrentFilePath;
        this.btDownload = btDownload;
    }

    public MagnetUriBuilder(String torrentFilePath) {
        this(torrentFilePath, null);
    }

    public MagnetUriBuilder(BittorrentDownload download) {
        this(null, download);
    }

    @Override
    public String toString() {
        if (this.torrentFilePath != null) {
            try {
                String magnetUri = new TorrentInfo(new File(this.torrentFilePath)).makeMagnetUri();
                magnetUri = appendXPEParameter(magnetUri);
                return magnetUri;
            } catch (Throwable e) {
                LOG.warn("Error trying to get magnet", e);
            }
        } else if (this.btDownload != null) {
            return appendXPEParameter(btDownload.magnetUri());
        }
        return super.toString();
    }

    private String appendXPEParameter(String magnetUri) {
        String xpe = null;
        if (BTEngine.getInstance().getExternalAddress() != null) {
            Address externalAddress = BTEngine.getInstance().getExternalAddress();
            xpe = externalAddress.toString() + externalAddress.toV4()
        }
        if (xpe != null) {
            magnetUri = magnetUri + "&x.pe=" + xpe;
        }
        return magnetUri;
    }
}
