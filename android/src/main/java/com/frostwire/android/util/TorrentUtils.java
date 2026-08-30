/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.util;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.DefaultTrackers;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.jlibtorrent.swig.create_torrent;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.file_storage;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.set_piece_hashes_listener;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Objects;

/**
 * Utility class for torrent creation and seeding operations
 * 
 * @author copilot
 */
public final class TorrentUtils {
    
    private static final Logger LOG = Logger.getLogger(TorrentUtils.class);

    private TorrentUtils() {
        // Utility class
    }

    /**
     * Seeds a finished HTTP download if seeding is enabled in settings and network conditions allow it.
     * This method handles the configuration checks on the appropriate thread to avoid strict mode violations.
     * 
     * @param savePath The path to the downloaded file
     * @param displayName The display name for the download
     * @param fileType The type of file (e.g., Constants.FILE_TYPE_AUDIO, Constants.FILE_TYPE_DOCUMENTS)
     * @param manager The TransferManager instance
     */
    public static void seedFinishedHttpDownloadIfEnabled(File savePath, String displayName, byte fileType, TransferManager manager) {
        seedFinishedHttpDownloadIfEnabled(savePath, displayName, fileType, manager, null);
    }

    public static void seedFinishedHttpDownloadIfEnabled(File savePath, String displayName, byte fileType, TransferManager manager, Transfer httpTransfer) {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> {
            ConfigurationManager cm = ConfigurationManager.instance();
            if (!cm.isSeedFinishedTorrents()) {
                LOG.info("Auto-seed skipped: seeding disabled in settings");
                return;
            }
            if (cm.isSeedingEnabledOnlyForWifi() && !NetworkManager.instance().isDataWIFIUp()) {
                LOG.info("Auto-seed skipped: WiFi-only seeding and WiFi is down");
                return;
            }
            if (manager.isMobileAndDataSavingsOn()) {
                LOG.info("Auto-seed skipped: mobile data savings on");
                return;
            }
            if (savePath != null && savePath.exists()) {
                FWFileDescriptor fd = createFileDescriptor(savePath, displayName, fileType);
                if (fd != null) {
                    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC,
                            () -> buildTorrentAndSeedIt(fd, manager, httpTransfer));
                }
            } else {
                LOG.warn("Auto-seed skipped: save path missing " + savePath);
            }
        });
    }
    
    /**
     * Creates a FWFileDescriptor for the given file
     */
    private static FWFileDescriptor createFileDescriptor(File file, String displayName, byte fileType) {
        if (!file.exists()) {
            return null;
        }
        
        FWFileDescriptor fd = new FWFileDescriptor();
        fd.filePath = file.getAbsolutePath();
        fd.fileSize = file.length();
        fd.dateModified = file.lastModified();
        fd.dateAdded = System.currentTimeMillis();
        fd.mime = MimeDetector.getMimeType(FilenameUtils.getExtension(file.getName()));
        fd.fileType = fileType;
        fd.title = displayName;
        fd.deletable = true;
        
        return fd;
    }
    
    public static boolean seedFile(File file, String displayName, TransferManager manager, Transfer httpTransfer) {
        FWFileDescriptor fd = createFileDescriptor(file, displayName != null ? displayName : file.getName(), Constants.FILE_TYPE_DOCUMENTS);
        if (fd == null) {
            return false;
        }
        return buildTorrentAndSeedIt(fd, manager, httpTransfer);
    }

    private static boolean buildTorrentAndSeedIt(final FWFileDescriptor fd, TransferManager manager, Transfer httpTransfer) {
        try {
            File file = new File(fd.filePath);
            File saveDir = file.getParentFile();
            file_storage fs = new file_storage();
            libtorrent.add_files(fs, file.getAbsolutePath());
            fs.set_name(file.getName());
            create_torrent ct = new create_torrent(fs);
            ct.set_creator("FrostWire " + Constants.FROSTWIRE_VERSION_STRING + " build " + Constants.FROSTWIRE_BUILD);
            for (String tracker : DefaultTrackers.ANNOUNCE_URLS) {
                ct.add_tracker(tracker, 0);
            }
            ct.set_priv(false);
            final error_code ec = new error_code();
            libtorrent.set_piece_hashes_ex(ct, Objects.requireNonNull(saveDir).getAbsolutePath(), new set_piece_hashes_listener(), ec);
            final byte[] torrent_bytes = new Entry(ct.generate()).bencode();
            final TorrentInfo tinfo = TorrentInfo.bdecode(torrent_bytes);
            String hash = tinfo.infoHashV1().toString().toLowerCase();
            BittorrentDownload existing = manager.getBittorrentDownload(hash);
            if (existing == null) {
                BTEngine.getInstance().download(tinfo, saveDir, new boolean[]{true}, null, manager.isDeleteStartedTorrentEnabled());
                TorrentHandle th = BTEngine.getInstance().find(tinfo.infoHashV1());
                if (th != null && th.isValid()) {
                    th.resume();
                    existing = manager.ensureUiDownload(th);
                }
            }
            if (existing == null) {
                LOG.error("Torrent created but not in TransferManager, keeping HTTP row: " + fd.filePath);
                return false;
            }
            try {
                com.frostwire.android.gui.RelaySearchWiring wiring =
                        com.frostwire.android.gui.SearchEngine.DISTRIBUTED_WIRING;
                if (wiring.localIndex() != null) {
                    new com.frostwire.search.relay.SharedTorrentIndexer(
                            wiring.localIndex(), wiring.identity())
                            .indexTorrentInfo(tinfo, fd.title);
                }
            } catch (Throwable indexErr) {
                LOG.warn("Seeded HTTP download but mesh index failed: " + fd.filePath, indexErr);
            }
            if (httpTransfer != null) {
                manager.remove(httpTransfer);
            }
            refreshTransfersUi();
            LOG.info("Successfully created and started seeding torrent for HTTP download: " + fd.filePath + " hash=" + hash);
            return true;
        } catch (Throwable e) {
            LOG.error("Error creating torrent for HTTP download seed: " + fd.filePath, e);
            return false;
        }
    }

    private static void refreshTransfersUi() {
        SystemUtils.postToUIThread(() -> {
            try {
                MainActivity activity = MainActivity.instance();
                if (activity == null) {
                    return;
                }
                Object fragment = activity.getFragmentByNavMenuId(R.id.menu_main_transfers);
                if (fragment instanceof TransfersFragment) {
                    ((TransfersFragment) fragment).onTime(true);
                }
            } catch (Throwable t) {
                LOG.warn("Could not refresh transfers UI after seed", t);
            }
        });
    }
}