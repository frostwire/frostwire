/*
 * Created on 9 Jul 2007
 * Created by Allan Crooks
 * Copyright (C) 2007 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
/*
 * File    : ManagerUtils.java
 * Created : 7 d�c. 2003}
 * By      : Olivier
 *
 * Copyright (C) 2004, 2005, 2006 Aelitis SAS, All rights Reserved
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * AELITIS, SAS au capital de 46,603.30 euros,
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
package com.frostwire.gui.bittorrent;

import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.transfers.TransferItem;
import org.gudy.azureus2.core3.util.UrlUtils;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TorrentUtil {

    public static BittorrentDownload getDownloadManager(File f) {
        List<BTDownload> downloads = BTDownloadMediator.instance().getDownloads();
        for (BTDownload d : downloads) {
            if (d instanceof BittorrentDownload) {
                BittorrentDownload bt = (BittorrentDownload) d;
                com.frostwire.bittorrent.BTDownload dl = bt.getDl();

                List<TransferItem> items = dl.getItems();

                for (TransferItem item : items) {
                    if (f.equals(item.getFile())) {
                        return bt;
                    }
                }
            }
        }

        return null;
    }

    private static Set<File> getIncompleteFiles() {
        Set<File> set = new HashSet<File>();

        List<BTDownload> downloads = BTDownloadMediator.instance().getDownloads();
        for (BTDownload d : downloads) {
            if (d instanceof BittorrentDownload) {
                BittorrentDownload bt = (BittorrentDownload) d;
                com.frostwire.bittorrent.BTDownload dl = bt.getDl();
                set.addAll(dl.getIncompleteFiles());
            }
        }

        return set;
    }

    private static Set<File> getPartsFiles() {
        Set<File> set = new HashSet<File>();

        List<BTDownload> downloads = BTDownloadMediator.instance().getDownloads();
        for (BTDownload d : downloads) {
            if (d instanceof BittorrentDownload) {
                BittorrentDownload bt = (BittorrentDownload) d;
                com.frostwire.bittorrent.BTDownload dl = bt.getDl();
                set.add(dl.partsFile());
            }
        }

        return set;
    }

    public static String getMagnet(String hash) {
        return "magnet:?xt=urn:btih:" + hash;
    }

    public static String getMagnetURLParameters(TorrentInfo torrent) {
        StringBuilder sb = new StringBuilder();

        //dn
        sb.append("dn=" + UrlUtils.encode(torrent.getName()));

        final List<AnnounceEntry> trackers = torrent.getTrackers();
        for (AnnounceEntry tracker : trackers) {
            final String url = tracker.getUrl();
            sb.append("&tr=");
            sb.append(UrlUtils.encode(url));
        }

        return sb.toString();
    }

    public static Set<File> getIgnorableFiles() {
        Set<File> set = TorrentUtil.getIncompleteFiles();
        set.addAll(getPartsFiles());
        return set;
    }
}
