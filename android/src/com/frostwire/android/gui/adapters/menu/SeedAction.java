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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.create_torrent;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.file_storage;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.set_piece_hashes_listener;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.io.File;
import java.lang.ref.WeakReference;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public class SeedAction extends MenuAction implements AbstractDialog.OnDialogClickListener {

    private static final Logger LOG = Logger.getLogger(SeedAction.class);

    private static final String DLG_SEEDING_OFF_TAG = "DLG_SEEDING_OFF_TAG";
    private static final String DLG_TURN_BITTORRENT_BACK_ON = "DLG_TURN_BITTORRENT_BACK_ON";

    private final FWFileDescriptor fd;
    private final BittorrentDownload btDownload;
    private final Transfer transferToClear;
    private final OnBittorrentConnectRunnable onBittorrentConnectRunnable;

    private SeedAction(Context context,
                       FWFileDescriptor fd,
                       BittorrentDownload existingBittorrentDownload,
                       Transfer transferToClear) {
        super(context, R.drawable.contextmenu_icon_seed, R.string.seed);
        this.fd = fd;
        this.btDownload = existingBittorrentDownload;
        this.transferToClear = transferToClear;
        this.onBittorrentConnectRunnable = new OnBittorrentConnectRunnable(this);
    }

    /**
     * Seeds a file that's not a torrent yet.
     * Reminder: Currently disabled when using SD Card.
     */
    public SeedAction(Context context, FWFileDescriptor fd) {
        this(context, fd, null, null);
    }

    /**
     * Seeds a file that's not a torrent yet but was an existing transfer. Pass the transfer
     * object so we're able to replace it with the new BittorrentDownload that will be created
     * <p>
     * Reminder: Currently disabled when using SD Card.
     *
     * @param context
     * @param fd
     * @param transferToClear
     */
    public SeedAction(Context context, FWFileDescriptor fd, Transfer transferToClear) {
        this(context, fd, null, transferToClear);
    }

    /**
     * Seed an existing torrent transfer that's finished and paused.
     * It's not disabled since it exists for existing torrent transfers.
     *
     * @param context
     * @param download
     */
    public SeedAction(Context context, BittorrentDownload download) {
        this(context, null, download, null);
    }

    public SeedAction(Context context) {
        this(context, null, null, null);
    }

    @Override
    public void onClick(Context context) {
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
        // if BitTorrent is turned off
        if (TransferManager.instance().isBittorrentDisconnected()) {
            showBittorrentDisconnectedDialog();
            return;
        }
        // in case user seeds only on wifi and there's no wifi, we let them know what will occur.
        if (seedingOnlyOnWifiButNoWifi()) {
            showNoWifiInformationDialog();
            return;
        } else if (TransferManager.instance().isMobileAndDataSavingsOn()) {
            showMobileDataProtectionInformationDialog();
        }
        // 1. If Seeding is turned off let's ask the user if they want to
        //    turn seeding on, or else cancel this.
        if (!ConfigurationManager.instance().isSeedFinishedTorrents()) {
            showSeedingDialog();
        } else {
            seedEm();
            if (context instanceof TimerObserver) {
                ((TimerObserver) context).onTime();
            }
            UIUtils.showTransfersOnDownloadStart(getContext());
        }
    }

    private void showNoWifiInformationDialog() {
        ShowNoWifiInformationDialog.newInstance().show(((Activity) getContext()).getFragmentManager());
    }

    private void showMobileDataProtectionInformationDialog() {
        ShowMobileDataProtectionInformationDialog.newInstance().show(((Activity) getContext()).getFragmentManager());
    }

    private void showBittorrentDisconnectedDialog() {
        YesNoDialog dlg = YesNoDialog.newInstance(
                DLG_TURN_BITTORRENT_BACK_ON,
                R.string.bittorrent_off,
                R.string.bittorrent_is_currently_disconnected_would_you_like_me_to_start_it_for_you,
                YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK);
        dlg.setOnDialogClickListener(this);
        dlg.show(((Activity) getContext()).getFragmentManager());
    }

    private void showSeedingDialog() {
        YesNoDialog dlg = YesNoDialog.newInstance(
                DLG_SEEDING_OFF_TAG,
                R.string.enable_seeding,
                R.string.seeding_is_currently_disabled_in_settings,
                YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK);
        dlg.setOnDialogClickListener(this);
        dlg.show(((Activity) getContext()).getFragmentManager());
    }

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(DLG_SEEDING_OFF_TAG)) {
            if (which == Dialog.BUTTON_NEGATIVE) {
                UIUtils.showLongMessage(getContext(),
                        R.string.the_file_could_not_be_seeded_enable_seeding);
            } else if (which == Dialog.BUTTON_POSITIVE) {
                onSeedingEnabled();
            }
        }
        if (tag.equals(DLG_TURN_BITTORRENT_BACK_ON)) {
            if (which == Dialog.BUTTON_NEGATIVE) {
                UIUtils.showLongMessage(getContext(),
                        R.string.the_file_could_not_be_seeded_bittorrent_will_remain_disconnected);
            } else if (which == Dialog.BUTTON_POSITIVE) {
                onBittorrentConnectRunnable.onBittorrentConnect(getContext());
            }
        }
    }

    private boolean seedingOnlyOnWifiButNoWifi() {
        ConfigurationManager CM = ConfigurationManager.instance();
        return CM.isSeedFinishedTorrents() &&
                CM.isSeedingEnabledOnlyForWifi() &&
                !NetworkManager.instance().isDataWIFIUp();
    }

    private void seedEm() {
        if (!TransferManager.instance().isMobileAndDataSavingsOn()) {
            if (fd == null && btDownload == null) {
                TransferManager.instance().seedFinishedTransfers();
            } else if (fd != null && btDownload == null) {
                seedFileDescriptor(fd);
            } else if (fd == null) {
                seedBTDownload();
            }
            if (transferToClear != null) {
                TransferManager.instance().remove(transferToClear);
            }
        }
    }

    private void seedFileDescriptor(FWFileDescriptor fd) {
        if (fd.filePath.endsWith(".torrent")) {
            try {
                BTEngine.getInstance().download(new File(fd.filePath), null, new boolean[]{true});
            } catch (Throwable e) {
                // TODO: better user notification
                LOG.error("Error starting download from file descriptor", e);
                // sometimes a file descriptor could be visible in the UI but does not exist
                // due to the android providers getting out of sync.
            }
        } else {
            async(this::buildTorrentAndSeedIt,fd);
        }
    }

    private void seedBTDownload() {
        btDownload.resume();
        final Object torrentHandle = BTEngine.getInstance().find(new Sha1Hash(btDownload.getInfoHash()));
        if (torrentHandle == null) {
            LOG.warn("seedBTDownload() could not find torrentHandle for existing torrent.");
        }
    }

    private void buildTorrentAndSeedIt(final FWFileDescriptor fd) {
        try {
            // TODO: Do this so it works with SD Card support / New BS File storage api from Android.
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
            libtorrent.set_piece_hashes_ex(ct, saveDir.getAbsolutePath(), new set_piece_hashes_listener(), ec);
            final byte[] torrent_bytes = new Entry(ct.generate()).bencode();
            final TorrentInfo tinfo = TorrentInfo.bdecode(torrent_bytes);
            // so the TorrentHandle object is created and added to the libtorrent session.
            BTEngine.getInstance().download(tinfo, saveDir, new boolean[]{true}, null, TransferManager.instance().isDeleteStartedTorrentEnabled());
        } catch (Throwable e) {
            // TODO: better handling of this error
            LOG.error("Error creating torrent for seed", e);
        }
    }

    private void onSeedingEnabled() {
        ConfigurationManager.instance().setSeedFinishedTorrents(true);
        seedEm();
        UIUtils.showTransfersOnDownloadStart(getContext());
    }

    // important to keep class public so it can be instantiated when the dialog is re-created on orientation changes.
    @SuppressWarnings("WeakerAccess")
    public final static class ShowNoWifiInformationDialog extends AbstractDialog {

        public static ShowNoWifiInformationDialog newInstance() {
            return new ShowNoWifiInformationDialog();
        }

        // Important to keep this guy 'public', even if IntelliJ thinks you shouldn't.
        // otherwise, the app crashes when you turn the screen and the dialog can't
        public ShowNoWifiInformationDialog() {
            super(R.layout.dialog_default_info);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            TextView title = findView(dlg, R.id.dialog_default_info_title);
            title.setText(R.string.wifi_network_unavailable);
            TextView text = findView(dlg, R.id.dialog_default_info_text);
            text.setText(R.string.according_to_settings_i_cant_seed_unless_wifi);
            Button okButton = findView(dlg, R.id.dialog_default_info_button_ok);
            okButton.setText(android.R.string.ok);
            okButton.setOnClickListener(new OkButtonOnClickListener(dlg));
        }
    }

    public final static class ShowMobileDataProtectionInformationDialog extends AbstractDialog {

        public static ShowMobileDataProtectionInformationDialog newInstance() {
            return new ShowMobileDataProtectionInformationDialog();
        }

        // Important to keep this guy 'public', even if IntelliJ thinks you shouldn't.
        // otherwise, the app crashes when you turn the screen and the dialog can't
        public ShowMobileDataProtectionInformationDialog() {
            super(R.layout.dialog_default_info);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            TextView title = findView(dlg, R.id.dialog_default_info_title);
            title.setText(R.string.mobile_data_saving);
            TextView text = findView(dlg, R.id.dialog_default_info_text);
            text.setText(R.string.according_to_settings_i_cant_seed_due_to_data_savings);
            Button okButton = findView(dlg, R.id.dialog_default_info_button_ok);
            okButton.setText(android.R.string.ok);
            okButton.setOnClickListener(new OkButtonOnClickListener(dlg));
        }
    }

    private final static class OkButtonOnClickListener implements View.OnClickListener {
        private final WeakReference<Dialog> newNoWifiInformationDialogRef;

        OkButtonOnClickListener(Dialog newNoWifiInformationDialog) {
            this.newNoWifiInformationDialogRef = Ref.weak(newNoWifiInformationDialog);
        }

        @Override
        public void onClick(View view) {
            if (Ref.alive(newNoWifiInformationDialogRef)) {
                newNoWifiInformationDialogRef.get().dismiss();
            }
        }
    }
}
