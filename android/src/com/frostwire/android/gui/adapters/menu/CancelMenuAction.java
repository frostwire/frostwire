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
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.SoundcloudDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.YouTubeDownload;
import com.frostwire.util.Ref;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class CancelMenuAction extends MenuAction {

    private final Transfer transfer;
    private final boolean deleteData;
    private final boolean deleteTorrent;


    public CancelMenuAction(Context context, Transfer transfer, boolean deleteData) {
        super(context,
                deleteData ? R.drawable.contextmenu_icon_trash : R.drawable.contextmenu_icon_stop_transfer,
                deleteData ? R.string.cancel_delete_menu_action : (transfer.isComplete()) ? R.string.clear_complete : R.string.cancel_menu_action);
        this.transfer = transfer;
        this.deleteData = deleteData;
        this.deleteTorrent = deleteData;
    }

    public CancelMenuAction(Context context, BittorrentDownload transfer, boolean deleteTorrent, boolean deleteData) {
        super(context,
                deleteData ? R.drawable.contextmenu_icon_trash : R.drawable.contextmenu_icon_stop_transfer,
                R.string.remove_torrent_and_data);
        this.transfer = transfer;
        this.deleteTorrent = deleteTorrent;
        this.deleteData = deleteData;
    }

    @Override
    public void onClick(final Context context) {
        CancelMenuActionDialog.newInstance(
                transfer,
                deleteData, deleteTorrent, this).
                show(((Activity) getContext()).getFragmentManager());
    }

    public static class CancelMenuActionDialog extends AbstractDialog {
        private static Transfer transfer;
        private static boolean deleteData;
        private static boolean deleteTorrent;
        private static CancelMenuAction cancelMenuAction;

        public static CancelMenuActionDialog newInstance(Transfer t,
                                                         boolean delete_data,
                                                         boolean delete_torrent,
                                                         CancelMenuAction cancel_menu_action) {
            transfer = t;
            deleteData = delete_data;
            deleteTorrent = delete_torrent;
            cancelMenuAction = cancel_menu_action;
            return new CancelMenuActionDialog();
        }

        public CancelMenuActionDialog() {
            super(R.layout.dialog_default);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {

            int yes_no_cancel_transfer_id = R.string.yes_no_cancel_transfer_question;
            if (transfer instanceof HttpDownload || transfer instanceof YouTubeDownload || transfer instanceof SoundcloudDownload) {
                yes_no_cancel_transfer_id = R.string.yes_no_cancel_transfer_question_cloud;
            }

            TextView dialogTitle = findView(dlg, R.id.dialog_default_title);
            dialogTitle.setText(R.string.cancel_transfer);

            TextView dialogText = findView(dlg, R.id.dialog_default_text);
            dialogText.setText((deleteData) ? R.string.yes_no_cancel_delete_transfer_question : yes_no_cancel_transfer_id);


            // Set the save button action
            Button noButton = findView(dlg, R.id.dialog_default_button_no);
            noButton.setText(R.string.cancel);
            Button yesButton = findView(dlg, R.id.dialog_default_button_yes);
            yesButton.setText(android.R.string.ok);

            noButton.setOnClickListener(new NegativeButtonOnClickListener(dlg));
            yesButton.setOnClickListener(new PositiveButtonOnClickListener(transfer, deleteTorrent, deleteData, cancelMenuAction, dlg));
        }
    }

    private static class NegativeButtonOnClickListener implements View.OnClickListener {
        private final Dialog dlg;

        NegativeButtonOnClickListener(Dialog newCancelMenuActionDialog) {
            dlg = newCancelMenuActionDialog;
        }

        @Override
        public void onClick(View view) {
            dlg.cancel();
        }
    }

    private static class PositiveButtonOnClickListener implements View.OnClickListener {
        private final Transfer transfer;
        private final boolean deleteTorrent;
        private final boolean deleteData;
        private final Dialog dlg;
        @SuppressWarnings("unused")
        private final CancelMenuAction cancelMenuAction;

        PositiveButtonOnClickListener(Transfer transfer,
                                      boolean deleteTorrent,
                                      boolean deleteData,
                                      CancelMenuAction cancelMenuAction,
                                      Dialog dialog) {
            this.transfer = transfer;
            this.deleteTorrent = deleteTorrent;
            this.deleteData = deleteData;
            this.cancelMenuAction = cancelMenuAction;
            this.dlg = dialog;
        }

        @Override
        public void onClick(View view) {
            RemoveTransferTask task = new RemoveTransferTask(transfer, deleteTorrent,
                    deleteData, dlg.getContext());
            Engine.instance().getThreadPool().execute(task);
            dlg.dismiss();
        }
    }

    private static final class RemoveTransferTask implements Runnable {

        // don't hold a hard reference to the transfers, since
        // it could be a UIBittorrentDownload, and gui objects
        // indirectly hold references to a context
        private final WeakReference<Transfer> transferRef;
        private final boolean deleteTorrent;
        private final boolean deleteData;
        private final WeakReference<Context> context;

        RemoveTransferTask(Transfer transfer, boolean deleteTorrent,
                           boolean deleteData, Context context) {
            this.transferRef = Ref.weak(transfer);
            this.deleteTorrent = deleteTorrent;
            this.deleteData = deleteData;
            this.context = Ref.weak(context);
        }

        @Override
        public void run() {
            if (!Ref.alive(transferRef)) {
                // this should never happens (unless it's already removed),
                // since all transfer are keep in the TransferManager list
                return;
            }

            Transfer transfer = transferRef.get();

            if (transfer instanceof UIBittorrentDownload) {
                ((UIBittorrentDownload) transfer).remove(context, deleteTorrent, deleteData);
            } else {
                transfer.remove(deleteData);
            }

            if (Ref.alive(context)) {
                UIUtils.broadcastAction(context.get(), Constants.ACTION_FILE_ADDED_OR_REMOVED);
            }

            if (Ref.alive(context)) {
                MainActivity.refreshTransfers(context.get());
            }

            UXStats.instance().log(UXAction.DOWNLOAD_REMOVE);
        }
    }
}
