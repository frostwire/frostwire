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
 * Created : 7 dec. 2003
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
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.UrlUtils;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import javax.swing.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TorrentUtil {

    public interface UITorrentMakerListener {
        void onCreateTorrentError(final error_code ec);
        void beforeOpenForSeedInUIThread();
        void onException();
    }

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
        Set<File> set = new HashSet<>();

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
        Set<File> set = new HashSet<>();

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
        sb.append("dn=");
        sb.append(UrlUtils.encode(torrent.getName()));

        final List<AnnounceEntry> trackers = torrent.trackers();
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

    public static boolean askForPermissionToSeedAndSeedDownloads(BTDownload[] downloaders) {
        boolean allowedToResume = true;
        boolean oneIsCompleted = false;

        if (downloaders != null) {
            for (BTDownload downloader : downloaders) {
                if (downloader.isCompleted()) {
                    oneIsCompleted = true;
                    break;
                }
            }
        }

        if ((oneIsCompleted || downloaders==null) && !SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
            DialogOption answer;
            String message1 = "";
            if (downloaders != null) {
                message1 = (downloaders.length > 1) ? I18n.tr("One of the transfers is complete and resuming will cause it to start seeding") : I18n.tr("This transfer is already complete, resuming it will cause it to start seeding");
            }

            String message2 = I18n.tr("Do you want to enable torrent seeding?");
            answer = GUIMediator.showYesNoMessage(message1 + "\n\n" + message2, DialogOption.YES);
            allowedToResume = answer.equals(DialogOption.YES);

            if (allowedToResume) {
                SharingSettings.SEED_FINISHED_TORRENTS.setValue(true);
            }
        }

        if (allowedToResume && downloaders != null) {
            for (BTDownload downloader : downloaders) {
                downloader.resume();
            }
            UXStats.instance().log(UXAction.DOWNLOAD_RESUME);
        }
        return allowedToResume;
    }

    public static void makeTorrentAndDownload(final File file, final UITorrentMakerListener uiTorrentMakerListener, final boolean showShareTorrentDialog) {
        try {
            file_storage fs = new file_storage();
            libtorrent.add_files(fs, file.getAbsolutePath());
            create_torrent torrentCreator = new create_torrent(fs);
            torrentCreator.add_tracker("udp://tracker.openbittorrent.com:80");
            torrentCreator.add_tracker("udp://tracker.publicbt.com:80");
            torrentCreator.add_tracker("udp://open.demonii.com:1337");
            torrentCreator.add_tracker("udp://tracker.coppersurfer.tk:6969");
            torrentCreator.add_tracker("udp://tracker.leechers-paradise.org:6969");
            torrentCreator.add_tracker("udp://exodus.desync.com:6969");
            torrentCreator.add_tracker("udp://tracker.pomf.se");

            torrentCreator.set_priv(false);
            torrentCreator.set_creator("FrostWire " + FrostWireUtils.getFrostWireVersion() + " build " + FrostWireUtils.getBuildNumber());
            final File torrentFile = new File(SharingSettings.TORRENTS_DIR_SETTING.getValue(), file.getName() + ".torrent");
            final error_code ec = new error_code();
            libtorrent.set_piece_hashes(torrentCreator,file.getParentFile().getAbsolutePath(), ec);
            if (ec.value() != 0 && uiTorrentMakerListener != null) {
                uiTorrentMakerListener.onCreateTorrentError(ec);
                return;
            }

            final entry torrentEntry = torrentCreator.generate();
            byte[] bencoded_torrent_bytes = Vectors.byte_vector2bytes(torrentEntry.bencode());
            FileOutputStream fos = new FileOutputStream(torrentFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            bos.write(bencoded_torrent_bytes);
            bos.flush();
            bos.close();

            final TorrentInfo torrent = TorrentInfo.bdecode(bencoded_torrent_bytes);

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (uiTorrentMakerListener != null) {
                        uiTorrentMakerListener.beforeOpenForSeedInUIThread();
                    }
                    GUIMediator.instance().openTorrentForSeed(torrentFile, file.getParentFile());
                    if (showShareTorrentDialog) {
                        new ShareTorrentDialog(torrent).setVisible(true);
                    }
                }
            });

        } catch (final Exception e) {
            e.printStackTrace();
            if (uiTorrentMakerListener != null) {
                uiTorrentMakerListener.onException();
            }
        }
    }
}
