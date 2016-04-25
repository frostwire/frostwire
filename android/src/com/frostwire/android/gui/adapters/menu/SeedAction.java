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

package com.frostwire.android.gui.adapters.menu;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.logging.Logger;

import java.io.File;
import java.util.List;

/**
 * Created on 4/22/16.
 *
 * @author gubatron
 * @author aldenml
 */
public class SeedAction extends MenuAction implements AbstractDialog.OnDialogClickListener {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(SeedAction.class);
    private final FileDescriptor fd;
    private final List<FileDescriptor> fds;
    private final ConfigurationManager CM;
    private static String DLG_SEEDING_OFF_TAG = "DLG_SEEDING_OFF_TAG";

    public SeedAction(Context context, FileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_share, R.string.seed);
        this.fd = fd;
        fds = null;
        CM = ConfigurationManager.instance();
    }

    public SeedAction(Context context, List<FileDescriptor> checked) {
        super(context, R.drawable.contextmenu_icon_share, R.string.seed);
        fd = null;
        fds = checked;
        CM = ConfigurationManager.instance();
    }

    @Override
    protected void onClick(Context context) {
        // NOTES.
        // Performance note: (specially when creating a .torrent of a big video)
        // wish we could know in advance if we've already created it
        // and have a .torrent already for this file on disk.
        // I think we could keep them in a db table(filepath -> sha1_hash)
        // and then we could just look it up on the session.
        // For now we just create the <file-name>-<infohash>.torrent after
        // we've added the TorrentInfo to the session.
        // Note: Let's try Merkle torrents to keep them small and use less
        // storage on the android device.

        // in case user seeds only on wifi and there's no wifi, we let them know what will occur.
        if (seedingOnlyOnWifiButNoWifi()) {
            showNoWifiInformationDialog();
        }

        // 1. If Seeding is turned off let's ask the user if they want to
        //    turn seeding on, or else cancel this.
        if (!isSeedingEnabled()) {
            showSeedingDialog();
        } else {
            seedEm();
            UIUtils.showTransfersOnDownloadStart(getContext());
        }
    }

    private void showNoWifiInformationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.wifi_network_unavailable);
        builder.setMessage(R.string.according_to_settings_i_cant_seed_unless_wifi);
        builder.create().show();
    }

    private void showSeedingDialog() {
        YesNoDialog dlg = YesNoDialog.newInstance(
                DLG_SEEDING_OFF_TAG,
                R.string.enable_seeding,
                R.string.seeding_is_currently_disabled);
        dlg.setOnDialogClickListener(this);
        dlg.show(((Activity) getContext()).getFragmentManager());
    }

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(DLG_SEEDING_OFF_TAG)) {
            if (which == Dialog.BUTTON_NEGATIVE) {
                UIUtils.showLongMessage(getContext(),
                        R.string.the_file_could_not_be_seeded);
            } else if (which == Dialog.BUTTON_POSITIVE) {
                onSeedingEnabled();
            }
        }
    }

    private boolean seedingOnlyOnWifiButNoWifi() {
        return isSeedingEnabled() && isSeedingEnabledOnlyForWifi() && !NetworkManager.instance().isDataWIFIUp();
    }

    private void seedEm() {
        if (fd != null) {
            seedFileDescriptor(fd);
        } else if (fds != null) {
            seedFileDescriptors();
        }
    }

    private void seedFileDescriptor(FileDescriptor fd) {
        if (fd.filePath.endsWith(".torrent")) {
            BTEngine.getInstance().download(new File(fd.filePath), null, new boolean[]{true});
        } else {
            buildTorrentAndSeedIt(fd);
        }
    }

    private void seedFileDescriptors() {
        for (FileDescriptor f : fds) {
            seedFileDescriptor(f);
        }
    }

    private void buildTorrentAndSeedIt(final FileDescriptor fd) {
        // TODO: re-do this with new TorrentBuilder
        Engine.instance().getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                File file = new File(fd.filePath);
                File saveDir = file.getParentFile();
                file_storage fs = new file_storage();
                fs.add_file(file.getName(), file.length());
                fs.set_name(file.getName());
                create_torrent ct = new create_torrent(fs); //, 0, -1, create_torrent.flags_t.merkle.swigValue());
                // commented out the merkle flag above because torrent doesn't appear as "Seeding", piece count doesn't work
                // as the algorithm in BTDownload.getProgress() doesn't make sense at the moment for merkle torrents.
                ct.set_creator("FrostWire " + Constants.FROSTWIRE_VERSION_STRING + " build " + Constants.FROSTWIRE_BUILD);
                ct.set_priv(false);

                final error_code ec = new error_code();
                libtorrent.set_piece_hashes_ex(ct, saveDir.getAbsolutePath(),
                        new set_piece_hashes_listener() {
                            @Override
                            public void progress(int i) {
                                super.progress(i);
                                LOG.info("progress(" + i + ")");
                            }
                        }, ec);

                final byte[] torrent_bytes = new Entry(ct.generate()).bencode();
                final TorrentInfo tinfo = TorrentInfo.bdecode(torrent_bytes);

                // so the TorrentHandle object is created and added to the libtorrent session.
                BTEngine.getInstance().download(tinfo, saveDir, new boolean[]{true});

                final TorrentHandle torrentHandle =
                        BTEngine.getInstance().getSession().findTorrent(tinfo.infoHash());
                torrentHandle.saveResumeData();
                torrentHandle.pause();
                torrentHandle.setAutoManaged(true);
                torrentHandle.forceDHTAnnounce();
                torrentHandle.scrapeTracker();

                // so it will call fireDownloadUpdate(torrentHandle) -> UIBittorrentDownload.updateUI()
                // which calculates the download items;
                BTEngine.getInstance().download(tinfo, saveDir, new boolean[]{true});

                torrentHandle.forceRecheck();
                torrentHandle.forceReannounce();
            }
        });
    }

    private void onSeedingEnabled() {
        enableSeeding();
        seedEm();
    }

    private void enableSeeding() {
        CM.setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS, true);
        TransferManager.instance().resumeResumableTransfers();
    }

    private boolean isSeedingEnabled() {
        return CM.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS);
    }

    private boolean isSeedingEnabledOnlyForWifi() {
        return CM.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY);
    }
}
