/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.concurrent.concurrent.ThreadExecutor;
import com.frostwire.jlibtorrent.*;
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
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * Creates a DHT based torrent with specified type and optionally shows the share dialog.
     *
     * @param file                   - The file/dir to make a torrent out of
     * @param uiTorrentMakerListener - an optional listener of this process
     * @param showShareTorrentDialog - show the share dialog when done
     * @param torrentType           - the type of torrent to create (v1, v2, or hybrid)
     */
    public static void makeTorrentAndDownload(final File file, final UITorrentMakerListener uiTorrentMakerListener, final boolean showShareTorrentDialog, TorrentType torrentType) {
        makeTorrentAndDownload(file, uiTorrentMakerListener, showShareTorrentDialog, true, torrentType);
    }

    public static info_hash_t infoHashTFromTorrentInfo(TorrentInfo ti) {
        Sha256Hash v2 = ti.infoHashV2();   // may be null or all zeros
        if (v2 != null && !v2.isAllZeros()) {
            return new info_hash_t(v2.swig()); // ctor from v2 hash
        }
        Sha1Hash v1 = ti.infoHashV1();
        return new info_hash_t(v1.swig());    // fallback
    }

    /**
     * Gets the appropriate info hash string for the torrent, handling both v1 and v2 torrents.
     * For v2-only torrents, returns the v2 hash; for v1-only or hybrid torrents, returns the v1 hash.
     * @param ti The TorrentInfo object
     * @return The hash string, or null if neither hash is available
     */
    public static String getInfoHashString(TorrentInfo ti) {
        try {
            // For v2-only torrents, use v2 hash
            if (ti.infoHashV1() == null) {
                Sha256Hash v2 = ti.infoHashV2();
                return v2 != null ? v2.toString() : null;
            }
            // For v1-only or hybrid torrents, use v1 hash for compatibility
            return ti.infoHashV1().toString();
        } catch (Exception e) {
            // Fallback: try v2 hash if v1 fails
            try {
                Sha256Hash v2 = ti.infoHashV2();
                return v2 != null ? v2.toString() : null;
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * @param file                   - The file/dir to make a torrent out of
     * @param uiTorrentMakerListener - an optional listener of this process
     * @param showShareTorrentDialog - show the share dialog when done
     * @param dhtTrackedOnly         - if true, no trackers are added, otherwise adds a list of default trackers.
     */
    private static void makeTorrentAndDownload(final File file, final UITorrentMakerListener uiTorrentMakerListener, final boolean showShareTorrentDialog, boolean dhtTrackedOnly) {
        makeTorrentAndDownload(file, uiTorrentMakerListener, showShareTorrentDialog, dhtTrackedOnly, TorrentType.HYBRID);
    }

    /**
     * @param file                   - The file/dir to make a torrent out of
     * @param uiTorrentMakerListener - an optional listener of this process
     * @param showShareTorrentDialog - show the share dialog when done
     * @param dhtTrackedOnly         - if true, no trackers are added, otherwise adds a list of default trackers.
     * @param torrentType           - the type of torrent to create (v1, v2, or hybrid)
     */
    private static void makeTorrentAndDownload(final File file, final UITorrentMakerListener uiTorrentMakerListener, final boolean showShareTorrentDialog, boolean dhtTrackedOnly, TorrentType torrentType) {
        if (EventQueue.isDispatchThread()) {
            // DO NOT DO THIS ON THE EDT
            ThreadExecutor.startThread(() -> makeTorrentAndDownload(file, uiTorrentMakerListener, showShareTorrentDialog, dhtTrackedOnly, torrentType), "TorrentUtil:makeTorrentAndDownload");
            return;
        }

        try {
            file_storage fs = new file_storage();
            add_files_listener addf_listener = new add_files_listener() {
                @Override
                public boolean pred(String p) {
                    return true;
                }
            };
            libtorrent.add_files_ex(fs, file.getAbsolutePath(), addf_listener, new create_flags_t());
            create_torrent torrentCreator = getCreateTorrent(dhtTrackedOnly, fs, torrentType);
            final File torrentFile = new File(SharingSettings.TORRENTS_DIR_SETTING.getValue(), file.getName() + ".torrent");
            final error_code ec = new error_code();

            // libtorrent.set_piece_hashes_ex crashes on macos because of its use of mmap, which handles memory errors via SIGBUS/SIGSEGV
            // and these signals sometimes leak to java and crash the JVM.
            // In windows this is not an issue because mmap provides structured exception handling.

            // no such issues with the posix disk io version.
            libtorrent.set_piece_hashes_posix_disk_io(torrentCreator, file.getParentFile().getAbsolutePath(), new set_piece_hashes_listener() {
                final AtomicBoolean progressInvoked = new AtomicBoolean(false);
                int totalPieces = torrentCreator.num_pieces();

                @Override
                public void progress(int n_piece) {
                    if (n_piece < 0) {
                        return;
                    }
                    if (totalPieces <= 0) {
                        totalPieces = torrentCreator.num_pieces();
                    }
                    if (uiTorrentMakerListener != null) {
                        if (!progressInvoked.get()) {
                            progressInvoked.set(true);
                            GUIMediator.safeInvokeLater(uiTorrentMakerListener::beforeOpenForSeedInUIThread);
                        }
                        GUIMediator.safeInvokeLater(() -> {
                            uiTorrentMakerListener.onPieceProgress(n_piece, totalPieces);
                        });
                    }

                }
            }, ec);

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

    private static @NotNull create_torrent getCreateTorrent(boolean dhtTrackedOnly, file_storage fs, TorrentType torrentType) {
        create_torrent torrentCreator = createTorrentWithType(fs, torrentType);
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
        return torrentCreator;
    }

    private static create_torrent createTorrentWithType(file_storage fs, TorrentType torrentType) {
        create_torrent torrent;
        int autoPieceSize = 0; // Let libtorrent auto-detect piece size
        
        switch (torrentType) {
            case V1_ONLY:
                // Create v1-only torrent - use legacy format
                torrent = new create_torrent(fs, autoPieceSize, create_torrent.v1_only);
                break;
                
            case V2_ONLY:
                // Create v2-only torrent - use modern format with v2 features only
                torrent = new create_torrent(fs, autoPieceSize, create_torrent.v2_only);
                break;
                
            case HYBRID:
            default:
                // Create hybrid torrent - supports both v1 and v2 protocols (default)
                torrent = new create_torrent(fs, autoPieceSize);
                break;
        }
        
        return torrent;
    }

    public interface UITorrentMakerListener {
        void onCreateTorrentError(final error_code ec);

        void beforeOpenForSeedInUIThread();

        void onException();

        void onPieceProgress(int nPiece, int totalPieces);
    }
}
