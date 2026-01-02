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

import androidx.fragment.app.FragmentActivity;

import com.frostwire.android.R;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.adapters.TransferListAdapter;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class CancelMenuAction extends MenuAction {
    private static final Logger LOG = Logger.getLogger(CancelMenuAction.class);

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
                        deleteData, deleteTorrent,
                        this).  // Pass 'this' to access FragmentManager and Activity
                show(getFragmentManager());
    }

    private static void removeTransfer(Context context, Transfer transfer, boolean deleteTorrent,
                                       boolean deleteData, TimerObserver timerObserver) {
        // Pause the transfer FIRST to provide immediate visual feedback that we're working on it
        // This makes the UI show a state change (to PAUSED) before removal, better UX
        if (transfer instanceof UIBittorrentDownload) {
            UIBittorrentDownload bt = ((UIBittorrentDownload) transfer);
            if (!bt.isPaused()) {
                bt.pause();
            }
        }

        // CRITICAL: Remove from manager SYNCHRONOUSLY (quick operation, ~1-10ms)
        // This prevents onTime() from seeing it in TransferManager and adding it back to adapter
        // The adapter removal already happened in onClick() for instant visual feedback
        if (transfer instanceof UIBittorrentDownload) {
            ((UIBittorrentDownload) transfer).remove(Ref.weak(context), deleteTorrent, deleteData);
        } else {
            transfer.remove(deleteData);
        }
    }

    public static class CancelMenuActionDialog extends AbstractDialog {
        private static Transfer transfer;
        private static boolean deleteData;
        private static boolean deleteTorrent;
        private static MenuAction menuAction;


        public static CancelMenuActionDialog newInstance(Transfer t,
                                                         boolean delete_data,
                                                         boolean delete_torrent,
                                                         MenuAction action) {
            transfer = t;
            deleteData = delete_data;
            deleteTorrent = delete_torrent;
            menuAction = action;
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
            yesButton.setOnClickListener(new PositiveButtonOnClickListener(transfer, deleteTorrent, deleteData, dlg, menuAction));
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
        private final MenuAction menuAction;

        PositiveButtonOnClickListener(Transfer transfer,
                                      boolean deleteTorrent,
                                      boolean deleteData,
                                      Dialog dialog,
                                      MenuAction action) {
            this.transfer = transfer;
            this.deleteTorrent = deleteTorrent;
            this.deleteData = deleteData;
            this.dlg = dialog;
            this.menuAction = action;
        }

        @Override
        public void onClick(View view) {
            // Get TransfersFragment reference on UI thread (where it's safe)
            TimerObserver timerObserver = null;
            if (menuAction != null && menuAction.getAppCompatActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) menuAction.getAppCompatActivity();
                if (activity != null) {
                    // Access the activity's direct reference to TransfersFragment
                    try {
                        // Try to get the transfers field directly from MainActivity
                        // This is the safest way to access the fragment from the UI thread
                        java.lang.reflect.Field transfersField = MainActivity.class.getDeclaredField("transfers");
                        transfersField.setAccessible(true);
                        Object transfersObj = transfersField.get(activity);
                        if (transfersObj instanceof TransfersFragment) {
                            timerObserver = (TransfersFragment) transfersObj;
                        }
                    } catch (Throwable ignored) {
                        // Fallback: try finding by fragment ID
                        try {
                            Object fragmentObj = activity.getSupportFragmentManager()
                                    .findFragmentById(R.id.activity_main_fragment_transfers);
                            if (fragmentObj instanceof TransfersFragment) {
                                timerObserver = (TransfersFragment) fragmentObj;
                            }
                        } catch (Throwable ignored2) {
                        }
                    }
                }
            }

            // CRITICAL: Remove from adapter IMMEDIATELY on UI thread for instant visual feedback
            // This happens before background removal, so user sees it disappear right away
            try {
                if (menuAction != null && menuAction.getAppCompatActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) menuAction.getAppCompatActivity();
                    if (activity != null) {
                        java.lang.reflect.Field transfersField = MainActivity.class.getDeclaredField("transfers");
                        transfersField.setAccessible(true);
                        Object transfersObj = transfersField.get(activity);
                        if (transfersObj instanceof TransfersFragment) {
                            java.lang.reflect.Field adapterField = TransfersFragment.class.getDeclaredField("adapter");
                            adapterField.setAccessible(true);
                            Object adapterObj = adapterField.get(transfersObj);
                            if (adapterObj instanceof TransferListAdapter) {
                                ((TransferListAdapter) adapterObj).removeTransferItem(transfer);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                LOG.error("Failed to remove transfer from adapter", e);
            }

            // Now remove from manager on background thread (synchronous, fast operation)
            // This must happen quickly before onTime() checks for the transfer
            TimerObserver finalTimerObserver = timerObserver;
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC,
                    () -> removeTransfer(dlg.getContext(), transfer, deleteTorrent, deleteData, finalTimerObserver));
            dlg.dismiss();
        }
    }
}
