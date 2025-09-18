/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.adapters.menu;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.util.Ref;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class CancelMenuAction extends MenuAction {

    private final Transfer transfer;
    private final boolean deleteData;
    private final boolean deleteTorrent;

    public CancelMenuAction(Context context, Transfer transfer, boolean deleteData) {
        super(context,
                deleteData ? R.drawable.contextmenu_icon_trash : R.drawable.contextmenu_icon_stop_transfer,
                deleteData ? R.string.cancel_delete_menu_action : (transfer.isComplete()) ? R.string.clear_complete : R.string.cancel_menu_action,
                UIUtils.getAppIconPrimaryColor(context));
        this.transfer = transfer;
        this.deleteData = deleteData;
        this.deleteTorrent = deleteData;
    }

    public CancelMenuAction(Context context, BittorrentDownload transfer, boolean deleteTorrent, boolean deleteData) {
        super(context,
                deleteData ? R.drawable.contextmenu_icon_trash : R.drawable.contextmenu_icon_stop_transfer,
                R.string.remove_torrent_and_data,
                UIUtils.getAppIconPrimaryColor(context));
        this.transfer = transfer;
        this.deleteTorrent = deleteTorrent;
        this.deleteData = deleteData;
    }

    @Override
    public void onClick(final Context context) {
        CancelMenuActionDialog.newInstance(
                        transfer,
                        deleteData, deleteTorrent).
                show(getFragmentManager());
    }

    private static void removeTransfer(Context context, Transfer transfer, boolean deleteTorrent,
                                       boolean deleteData) {
        if (transfer instanceof UIBittorrentDownload) {
            ((UIBittorrentDownload) transfer).remove(Ref.weak(context), deleteTorrent, deleteData);
        } else {
            transfer.remove(deleteData);
        }
        if (context != null) {
            MainActivity.refreshTransfers(context);
        }
    }

    public static class CancelMenuActionDialog extends AbstractDialog {
        private static Transfer transfer;
        private static boolean deleteData;
        private static boolean deleteTorrent;


        public static CancelMenuActionDialog newInstance(Transfer t,
                                                         boolean delete_data,
                                                         boolean delete_torrent) {
            transfer = t;
            deleteData = delete_data;
            deleteTorrent = delete_torrent;
            return new CancelMenuActionDialog();
        }

        public CancelMenuActionDialog() {
            super(R.layout.dialog_default);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {

            int yes_no_cancel_transfer_id = R.string.yes_no_cancel_transfer_question;
            if (transfer instanceof HttpDownload) {
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
            yesButton.setOnClickListener(new PositiveButtonOnClickListener(transfer, deleteTorrent, deleteData, dlg));
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
            if (dlg.getContext() instanceof TimerObserver) {
                ((TimerObserver) dlg.getContext()).onTime();
            }
        }
    }

    private static class PositiveButtonOnClickListener implements View.OnClickListener {
        private final Transfer transfer;
        private final boolean deleteTorrent;
        private final boolean deleteData;
        private final Dialog dlg;

        PositiveButtonOnClickListener(Transfer transfer,
                                      boolean deleteTorrent,
                                      boolean deleteData,
                                      Dialog dialog) {
            this.transfer = transfer;
            this.deleteTorrent = deleteTorrent;
            this.deleteData = deleteData;
            this.dlg = dialog;
        }

        @Override
        public void onClick(View view) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> removeTransfer(dlg.getContext(), transfer, deleteTorrent, deleteData));
            dlg.dismiss();
            if (dlg.getContext() instanceof TimerObserver) {
                ((TimerObserver) dlg.getContext()).onTime();
            }
        }
    }
}
