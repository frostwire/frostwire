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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.UrlUtils;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.VPNDropGuard;
import com.limegroup.gnutella.gui.search.TorrentUISearchResult;
import com.limegroup.gnutella.gui.search.UISearchResult;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
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

    public static String getMagnet(UISearchResult sr) {
        if (sr instanceof TorrentUISearchResult) {
            String torrentUrl = ((TorrentUISearchResult) sr).getTorrentUrl();
            if (torrentUrl.startsWith("magnet:?")) {
                return torrentUrl;
            }
        }
        return getMagnet(sr.getHash());
    }

    static String getMagnetURLParameters(TorrentInfo torrent) {
        StringBuilder sb = new StringBuilder();
        //dn (display name)
        sb.append("dn=");
        sb.append(UrlUtils.encode(torrent.name()));
        //tr (trackers)
        final List<AnnounceEntry> trackers = torrent.trackers();
        for (AnnounceEntry tracker : trackers) {
            final String url = tracker.url();
            sb.append("&tr=");
            sb.append(UrlUtils.encode(url));
        }
        //x.pe (bootstrapping peer(s) ip:port)
        sb.append(BTEngine.getInstance().magnetPeers());
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
        if (!VPNDropGuard.canUseBitTorrent()) {
            return false;
        }
        if (downloaders != null) {
            for (BTDownload downloader : downloaders) {
                if (downloader.isCompleted()) {
                    oneIsCompleted = true;
                    break;
                }
            }
        }
        if ((oneIsCompleted || downloaders == null) && !SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
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
        }
        return allowedToResume;
    }

    /**
     * Creates a DHT based torrent (no trackers set) and optionally shows the share dialog.
     *
     * @param file                   - The file/dir to make a torrent out of
     * @param uiTorrentMakerListener - an optional listener of this process
     * @param showShareTorrentDialog - show the share dialog when done
     */
    public static void makeTorrentAndDownload(final File file, final UITorrentMakerListener uiTorrentMakerListener, final boolean showShareTorrentDialog) {
        makeTorrentAndDownload(file, uiTorrentMakerListener, showShareTorrentDialog, true);
    }

    /**
     * @param file                   - The file/dir to make a torrent out of
     * @param uiTorrentMakerListener - an optional listener of this process
     * @param showShareTorrentDialog - show the share dialog when done
     * @param dhtTrackedOnly         - if true, no trackers are added, otherwise adds a list of default trackers.
     */
    private static void makeTorrentAndDownload(final File file, final UITorrentMakerListener uiTorrentMakerListener, final boolean showShareTorrentDialog, boolean dhtTrackedOnly) {
        try {
            file_storage fs = new file_storage();
            libtorrent.add_files(fs, file.getAbsolutePath());
            create_torrent torrentCreator = new create_torrent(fs);
            if (!dhtTrackedOnly) {
                torrentCreator.add_tracker("udp://tracker.openbittorrent.com:80", 0);
                torrentCreator.add_tracker("udp://tracker.publicbt.com:80", 0);
                torrentCreator.add_tracker("udp://open.demonii.com:1337", 0);
                torrentCreator.add_tracker("udp://tracker.coppersurfer.tk:6969", 0);
                torrentCreator.add_tracker("udp://tracker.leechers-paradise.org:6969", 0);
                torrentCreator.add_tracker("udp://exodus.desync.com:6969", 0);
                torrentCreator.add_tracker("udp://tracker.pomf.se", 0);
            }
            torrentCreator.set_priv(false);
            torrentCreator.set_creator("FrostWire " + FrostWireUtils.getFrostWireVersion() + " build " + FrostWireUtils.getBuildNumber());
            final File torrentFile = new File(SharingSettings.TORRENTS_DIR_SETTING.getValue(), file.getName() + ".torrent");
            final error_code ec = new error_code();
            libtorrent.set_piece_hashes(torrentCreator, file.getParentFile().getAbsolutePath(), ec);
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
            GUIMediator.safeInvokeLater(() -> {
                if (uiTorrentMakerListener != null) {
                    uiTorrentMakerListener.beforeOpenForSeedInUIThread();
                }
                GUIMediator.instance().openTorrentForSeed(torrentFile, file.getParentFile());
                if (showShareTorrentDialog) {
                    new ShareTorrentDialog(GUIMediator.getAppFrame(), torrent).setVisible(true);
                }
            });
        } catch (final Exception e) {
            e.printStackTrace();
            if (uiTorrentMakerListener != null) {
                uiTorrentMakerListener.onException();
            }
        }
    }

    interface UITorrentMakerListener {
        void onCreateTorrentError(final error_code ec);

        void beforeOpenForSeedInUIThread();

        void onException();
    }
}
