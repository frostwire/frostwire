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

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.transfers.*;
import com.frostwire.util.Ref;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

/**
 * @author gubatron
 * @author aldenml
 */
public class CancelMenuAction extends MenuAction {

    private final Transfer transfer;
    private final boolean deleteData;
    private final boolean deleteTorrent;
    private Context context;

    public CancelMenuAction(Context context, Transfer transfer, boolean deleteData) {
        super(context, R.drawable.contextmenu_icon_stop_transfer, (deleteData) ? R.string.cancel_delete_menu_action : (transfer.isComplete()) ? R.string.clear_complete : R.string.cancel_menu_action);
        this.context = context;
        this.transfer = transfer;
        this.deleteData = deleteData;
        this.deleteTorrent = deleteData;
    }

    public CancelMenuAction(Context context, BittorrentDownload transfer, boolean deleteTorrent, boolean deleteData) {
        super(context, R.drawable.contextmenu_icon_stop_transfer, R.string.remove_torrent_and_data);
        this.context = context;
        this.transfer = transfer;
        this.deleteTorrent = deleteTorrent;
        this.deleteData = deleteData;
    }

    @Override
    protected void onClick(final Context context) {

        final Dialog newCancelMenuActionDialog = new Dialog(getContext(), R.style.DefaultDialogTheme);
        newCancelMenuActionDialog.setContentView(R.layout.dialog_default);

        int yes_no_cancel_transfer_id = R.string.yes_no_cancel_transfer_question;
        if (transfer instanceof HttpDownload || transfer instanceof YouTubeDownload || transfer instanceof SoundcloudDownload) {
            yes_no_cancel_transfer_id = R.string.yes_no_cancel_transfer_question_cloud;
        }

        TextView dialogTitle = (TextView) newCancelMenuActionDialog.findViewById(R.id.dialog_default_title);
        dialogTitle.setText(R.string.cancel_transfer);

        TextView dialogText = (TextView) newCancelMenuActionDialog.findViewById(R.id.dialog_default_text);
        dialogText.setText((deleteData) ? R.string.yes_no_cancel_delete_transfer_question : yes_no_cancel_transfer_id);


        // Set the save button action
        Button noButton = (Button) newCancelMenuActionDialog.findViewById(R.id.dialog_default_button_no);
        noButton.setText(R.string.cancel);
        Button yesButton = (Button) newCancelMenuActionDialog.findViewById(R.id.dialog_default_button_yes);
        yesButton.setText(android.R.string.ok);

        noButton.setOnClickListener(new NegativeButtonOnClickListener(this, newCancelMenuActionDialog));
        yesButton.setOnClickListener(new PositiveButtonOnClickListener(this, newCancelMenuActionDialog));

        newCancelMenuActionDialog.show();
    }

    private class NegativeButtonOnClickListener implements View.OnClickListener {
        private final Dialog newCancelMenuActionDialog;
        private final CancelMenuAction cancelMenuAction;

        NegativeButtonOnClickListener(CancelMenuAction cancelMenuAction, Dialog newCancelMenuActionDialog) {
            this.newCancelMenuActionDialog = newCancelMenuActionDialog;
            this.cancelMenuAction = cancelMenuAction;
        }

        @Override
        public void onClick(View view) {
            newCancelMenuActionDialog.cancel();
        }
    }

    private class PositiveButtonOnClickListener implements View.OnClickListener {
        private final Dialog newCancelMenuActionDialog;
        private final CancelMenuAction cancelMenuAction;

        PositiveButtonOnClickListener(CancelMenuAction cancelMenuAction, Dialog newCancelMenuActionDialog) {
            this.newCancelMenuActionDialog = newCancelMenuActionDialog;
            this.cancelMenuAction = cancelMenuAction;
        }

        @Override
        public void onClick(View view) {
            Thread t = new Thread("Delete files - " + transfer.getDisplayName()) {
                @Override
                public void run() {
                    if (transfer instanceof UIBittorrentDownload) {
                        ((UIBittorrentDownload) transfer).remove(Ref.weak(context), deleteTorrent, deleteData);
                    } else if (transfer instanceof Transfer) {
                        transfer.remove(deleteData);
                    } else {
                        transfer.remove(false);
                    }
                    UIUtils.broadcastAction(context, Constants.ACTION_FILE_ADDED_OR_REMOVED);
                    UXStats.instance().log(UXAction.DOWNLOAD_REMOVE);
                }
            };
            Engine.instance().getThreadPool().execute(t);
            newCancelMenuActionDialog.dismiss();
        }
    }
}
