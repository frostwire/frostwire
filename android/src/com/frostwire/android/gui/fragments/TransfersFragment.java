/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.SettingsActivity;
import com.frostwire.android.gui.activities.VPNStatusDetailActivity;
import com.frostwire.android.gui.adapters.TransferListAdapter;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.dialogs.MenuDialog;
import com.frostwire.android.gui.dialogs.MenuDialog.MenuItem;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.tasks.DownloadSoundcloudFromUrlTask;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener;
import com.frostwire.android.gui.views.*;
import com.frostwire.android.gui.views.ClearableEditTextView.OnActionListener;
import com.frostwire.transfers.*;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;

import java.io.File;
import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class TransfersFragment extends AbstractFragment implements TimerObserver, MainFragment, OnDialogClickListener {
    private static final Logger LOG = Logger.getLogger(TransfersFragment.class);
    private static final String SELECTED_STATUS_STATE_KEY = "selected_status";
    private final Comparator<Transfer> transferComparator;
    private final ButtonAddTransferListener buttonAddTransferListener;
    private final ButtonMenuListener buttonMenuListener;
    private Button buttonSelectAll;
    private Button buttonSelectDownloading;
    private Button buttonSelectCompleted;
    private ExpandableListView list;
    private TextView textDHTPeers;
    private TextView textDownloads;
    private TextView textUploads;
    private TextView vpnRichToast;
    private ClearableEditTextView addTransferUrlTextView;
    private TransferListAdapter adapter;
    private TransferStatus selectedStatus;
    private TimerSubscription subscription;
    private boolean isVPNactive;
    private static boolean firstTimeShown = true;
    private Handler vpnRichToastHandler;

    private boolean showTorrentSettingsOnClick;

    public TransfersFragment() {
        super(R.layout.fragment_transfers);
        this.transferComparator = new TransferComparator();
        this.buttonAddTransferListener = new ButtonAddTransferListener(this);
        this.buttonMenuListener = new ButtonMenuListener(this);
        selectedStatus = TransferStatus.ALL;
        vpnRichToastHandler = new Handler();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            selectedStatus = TransferStatus.valueOf(savedInstanceState.getString(SELECTED_STATUS_STATE_KEY, TransferStatus.ALL.name()));
        }
        addTransferUrlTextView = findView(getView(), R.id.fragment_transfers_add_transfer_text_input);
        addTransferUrlTextView.replaceSearchIconDrawable(R.drawable.clearable_edittext_add_icon);
        addTransferUrlTextView.setFocusable(true);
        addTransferUrlTextView.setFocusableInTouchMode(true);
        addTransferUrlTextView.setOnKeyListener(new AddTransferTextListener(this));
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        subscription = TimerService.subscribe(this, 2);
    }

    @Override
    public void onResume() {
        super.onResume();
        initStorageRelatedRichNotifications(getView());
        onTime();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        subscription.unsubscribe();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SELECTED_STATUS_STATE_KEY, selectedStatus.name());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adapter != null) {
            adapter.dismissDialogs();
        }
    }

    @Override
    public void onTime() {
        if (adapter != null) {
            List<Transfer> transfers = filter(TransferManager.instance().getTransfers(), selectedStatus);
            Collections.sort(transfers, transferComparator);
            adapter.updateList(transfers);
        } else if (this.getActivity() != null) {
            setupAdapter();
        }

        //  format strings
        String sDown = UIUtils.rate2speed(TransferManager.instance().getDownloadsBandwidth() / 1024);
        String sUp = UIUtils.rate2speed(TransferManager.instance().getUploadsBandwidth() / 1024);

        // number of uploads (seeding) and downloads
        int downloads = TransferManager.instance().getActiveDownloads();
        int uploads = TransferManager.instance().getActiveUploads();

        updateStatusBar(sDown, sUp, downloads, uploads);
    }

    private void updateStatusBar(String sDown, String sUp, int downloads, int uploads) {
        textDownloads.setText(downloads + " @ " + sDown);
        textUploads.setText(uploads + " @ " + sUp);
        updateVPNButtonIfStatusChanged(NetworkManager.instance().isTunnelUp());
    }

    private void updateVPNButtonIfStatusChanged(boolean vpnActive) {
        boolean wasActiveBefore = isVPNactive && !vpnActive;

        isVPNactive = vpnActive;
        final ImageView view = findView(getView(), R.id.fragment_transfers_status_vpn_icon);
        if (view != null) {
            view.setImageResource(vpnActive ? R.drawable.notification_vpn_on : R.drawable.notification_vpn_off);
        }

        if (wasActiveBefore) {
            showVPNRichToast();
        }
    }

    private void onCheckDHT(final boolean dhtEnabled, final int dhtPeers) {
        if (textDHTPeers == null || !TransfersFragment.this.isAdded()) {
            return;
        }

        textDHTPeers.setVisibility(View.VISIBLE);
        showTorrentSettingsOnClick = true;

        // No Internet
        if (NetworkManager.instance().isInternetDown()) {
            textDHTPeers.setText(R.string.check_internet_connection);
            return;
        }

        // Saving Data on Mobile
        if (TransferManager.instance().isMobileAndDataSavingsOn()) {
            textDHTPeers.setText(R.string.bittorrent_off_data_saver_on);
            return;
        }

        // BitTorrent Turned off
        if (Engine.instance().isStopped() || Engine.instance().isDisconnected()) {
            // takes you to main settings screen so you can turn it back on.
            showTorrentSettingsOnClick = false;
            textDHTPeers.setText(R.string.bittorrent_off);
            return;
        }

        // No DHT
        if (!dhtEnabled) {
            textDHTPeers.setVisibility(View.INVISIBLE);
            return;
        }

        // DHT On.
        textDHTPeers.setText(dhtPeers + " " + TransfersFragment.this.getString(R.string.dht_contacts));
    }

    @Override
    public View getHeader(Activity activity) {
        LayoutInflater inflater = LayoutInflater.from(activity);

        View header = inflater.inflate(R.layout.view_transfers_header, null);

        TextView text = (TextView) header.findViewById(R.id.view_transfers_header_text_title);
        text.setText(R.string.transfers);

        ImageButton buttonMenu = (ImageButton) header.findViewById(R.id.view_transfers_header_button_menu);
        buttonMenu.setOnClickListener(buttonMenuListener);

        ImageButton buttonAddTransfer = (ImageButton) header.findViewById(R.id.view_transfers_header_button_add_transfer);
        buttonAddTransfer.setOnClickListener(buttonAddTransferListener);

        return header;
    }

    public void selectStatusTabToThe(boolean right) {
        final TransferStatus[] allStatusesArray = TransferStatus.getAllStatusesArray();
        int currentStatusIndex = -1;
        for (int i = 0; i < allStatusesArray.length; i++) {
            if (selectedStatus == allStatusesArray[i]) {
                currentStatusIndex = i;
                break;
            }
        }
        if (currentStatusIndex != -1) {
            int increment = (right) ? 1 : -1;
            currentStatusIndex = (currentStatusIndex + increment) % allStatusesArray.length;
            if (currentStatusIndex < 0) {
                currentStatusIndex = allStatusesArray.length - 1;
            }
            selectStatusTab(allStatusesArray[currentStatusIndex]);
        }
    }

    public void selectStatusTab(TransferStatus status) {
        selectedStatus = status;
        switch (selectedStatus) {
            case ALL:
                buttonSelectAll.performClick();
                break;
            case DOWNLOADING:
                buttonSelectDownloading.performClick();
                break;
            case COMPLETED:
                buttonSelectCompleted.performClick();
                break;
        }
    }

    @Override
    public void onShow() {
        if (firstTimeShown) {
            firstTimeShown = false;
            if (!NetworkManager.instance().isTunnelUp()) {
                showVPNRichToast();
            }
        }
    }

    private void showVPNRichToast() {
        vpnRichToast.setVisibility(View.VISIBLE);
        long VPN_NOTIFICATION_DURATION = 10000;
        vpnRichToastHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                vpnRichToast.setVisibility(View.GONE);
            }
        }, VPN_NOTIFICATION_DURATION);
    }

    @Override
    protected void initComponents(View v) {
        initStorageRelatedRichNotifications(v);

        buttonSelectAll = findView(v, R.id.fragment_transfers_button_select_all);
        buttonSelectAll.setOnClickListener(new ButtonTabListener(this, TransferStatus.ALL));

        buttonSelectDownloading = findView(v, R.id.fragment_transfers_button_select_downloading);
        buttonSelectDownloading.setOnClickListener(new ButtonTabListener(this, TransferStatus.DOWNLOADING));

        buttonSelectCompleted = findView(v, R.id.fragment_transfers_button_select_completed);
        buttonSelectCompleted.setOnClickListener(new ButtonTabListener(this, TransferStatus.COMPLETED));

        list = findView(v, R.id.fragment_transfers_list);
        SwipeLayout swipe = findView(v, R.id.fragment_transfers_swipe);
        swipe.setOnSwipeListener(new SwipeLayout.OnSwipeListener() {
            @Override
            public void onSwipeLeft() {
                selectStatusTabToThe(true);
            }

            @Override
            public void onSwipeRight() {
                selectStatusTabToThe(false);
            }
        });

        textDHTPeers = findView(v, R.id.fragment_transfers_dht_peers);
        textDHTPeers.setVisibility(View.INVISIBLE);
        textDHTPeers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context ctx = v.getContext();
                Intent i = new Intent(ctx, SettingsActivity.class);
                if (showTorrentSettingsOnClick) {
                    i.setAction(Constants.ACTION_SETTINGS_OPEN_TORRENT_SETTINGS);
                }
                ctx.startActivity(i);
            }
        });
        textDownloads = findView(v, R.id.fragment_transfers_text_downloads);
        textUploads = findView(v, R.id.fragment_transfers_text_uploads);

        vpnRichToast = findView(v, R.id.fragment_transfers_vpn_notification);
        vpnRichToast.setVisibility(View.GONE);
        vpnRichToast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vpnRichToast.setVisibility(View.GONE);
            }
        });
        initVPNStatusButton(v);
    }

    private void initVPNStatusButton(View v) {
        final ImageView vpnStatusButton = findView(v, R.id.fragment_transfers_status_vpn_icon);
        vpnStatusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context ctx = v.getContext();
                Intent i = new Intent(ctx, VPNStatusDetailActivity.class);
                i.setAction(isVPNactive ?
                        Constants.ACTION_SHOW_VPN_STATUS_PROTECTED :
                        Constants.ACTION_SHOW_VPN_STATUS_UNPROTECTED).
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                i.putExtra("from", "transfers");
                ctx.startActivity(i);
            }
        });
    }

    public void initStorageRelatedRichNotifications(View v) {
        if (v == null) {
            v = getView();
        }
        RichNotification sdCardNotification = findView(v, R.id.fragment_transfers_sd_card_notification);
        sdCardNotification.setVisibility(View.GONE);

        RichNotification internalMemoryNotification = findView(v, R.id.fragment_transfers_internal_memory_notification);
        internalMemoryNotification.setVisibility(View.GONE);

        if (TransferManager.isUsingSDCardPrivateStorage() && !sdCardNotification.wasDismissed()) {
            String currentPath = ConfigurationManager.instance().getStoragePath();
            boolean inPrivateFolder = currentPath.contains("Android/data");

            if (inPrivateFolder) {
                sdCardNotification.setVisibility(View.VISIBLE);
                sdCardNotification.setOnClickListener(new SDCardNotificationListener(this));
            }
        }

        //if you do have an SD Card mounted and you're using internal memory, we'll let you know
        //that you now can use the SD Card. We'll keep this for a few releases.
        File sdCardDir = getBiggestSDCardDir(getActivity());
        if (sdCardDir != null && com.frostwire.android.util.SystemUtils.isSecondaryExternalStorageMounted(sdCardDir) &&
                !TransferManager.isUsingSDCardPrivateStorage() &&
                !internalMemoryNotification.wasDismissed()) {
            String bytesAvailableInHuman = UIUtils.getBytesInHuman(com.frostwire.android.util.SystemUtils.getAvailableStorageSize(sdCardDir));
            String internalMemoryNotificationDescription = getString(R.string.saving_to_internal_memory_description, bytesAvailableInHuman);
            internalMemoryNotification.setDescription(internalMemoryNotificationDescription);
            internalMemoryNotification.setVisibility(View.VISIBLE);
            internalMemoryNotification.setOnClickListener(new SDCardNotificationListener(this));
        }
    }

    private void setupAdapter() {
        List<Transfer> transfers = filter(TransferManager.instance().getTransfers(), selectedStatus);
        Collections.sort(transfers, transferComparator);
        adapter = new TransferListAdapter(TransfersFragment.this.getActivity(), transfers);
        list.setAdapter(adapter);
    }

    private List<Transfer> filter(List<Transfer> transfers, TransferStatus status) {
        Iterator<Transfer> it;

        switch (status) { // replace this filter by a more functional style
            case DOWNLOADING:
                it = transfers.iterator();
                while (it.hasNext()) {
                    if (it.next().isComplete()) {
                        it.remove();
                    }
                }
                return transfers;
            case COMPLETED:
                it = transfers.iterator();
                while (it.hasNext()) {
                    if (!it.next().isComplete()) {
                        it.remove();
                    }
                }
                return transfers;
            default:
                return transfers;
        }
    }

    private static final String TRANSFERS_DIALOG_ID = "transfers_dialog";

    private static final int CLEAR_MENU_DIALOG_ID = 0;
    private static final int PAUSE_MENU_DIALOG_ID = 1;
    private static final int RESUME_MENU_DIALOG_ID = 2;

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(TRANSFERS_DIALOG_ID)) {
            switch (which) {
                case CLEAR_MENU_DIALOG_ID:
                    TransferManager.instance().clearComplete();
                    break;
                case PAUSE_MENU_DIALOG_ID:
                    TransferManager.instance().stopHttpTransfers();
                    TransferManager.instance().pauseTorrents();
                    break;
                case RESUME_MENU_DIALOG_ID:
                    boolean bittorrentDisconnected = TransferManager.instance().isBittorrentDisconnected();
                    if (bittorrentDisconnected) {
                        UIUtils.showLongMessage(getActivity(), R.string.cant_resume_torrent_transfers);
                    } else {
                        if (NetworkManager.instance().isDataUp()) {
                            TransferManager.instance().resumeResumableTransfers();
                        } else {
                            UIUtils.showShortMessage(getActivity(), R.string.please_check_connection_status_before_resuming_download);
                        }
                    }
                    break;
            }
            setupAdapter();
        }
    }

    private void showContextMenu() {
        MenuItem clear = new MenuItem(CLEAR_MENU_DIALOG_ID, R.string.transfers_context_menu_clear_finished, R.drawable.contextmenu_icon_remove_transfer);
        MenuItem pause = new MenuItem(PAUSE_MENU_DIALOG_ID, R.string.transfers_context_menu_pause_stop_all_transfers, R.drawable.contextmenu_icon_pause_transfer);
        MenuItem resume = new MenuItem(RESUME_MENU_DIALOG_ID, R.string.transfers_context_resume_all_torrent_transfers, R.drawable.contextmenu_icon_play);

        List<MenuItem> dlgActions = new ArrayList<>();

        TransferManager tm = TransferManager.instance();
        boolean bittorrentDisconnected = tm.isBittorrentDisconnected();
        final List<Transfer> transfers = tm.getTransfers();

        if (transfers != null && transfers.size() > 0) {
            if (someTransfersComplete(transfers)) {
                dlgActions.add(clear);
            }

            if (!bittorrentDisconnected) {
                if (someTransfersActive(transfers)) {
                    dlgActions.add(pause);
                }
            }

            //let's show it even if bittorrent is disconnected
            //user should get a message telling them to check why they can't resume.
            //Preferences > Connectivity is disconnected.
            if (someTransfersInactive(transfers)) {
                dlgActions.add(resume);
            }
        }

        if (dlgActions.size() > 0) {
            MenuDialog dlg = MenuDialog.newInstance(TRANSFERS_DIALOG_ID, dlgActions);
            dlg.show(getFragmentManager());
        }
    }

    private boolean someTransfersInactive(List<Transfer> transfers) {
        for (Transfer t : transfers) {
            if (t instanceof BittorrentDownload) {
                BittorrentDownload bt = (BittorrentDownload) t;
                if (!bt.isDownloading() && !bt.isSeeding()) {
                    return true;
                }
            } else if (t instanceof HttpDownload) {
                HttpDownload ht = (HttpDownload) t;
                if (ht.isComplete() || !ht.isDownloading()) {
                    return true;
                }
            } else if (t instanceof YouTubeDownload) {
                YouTubeDownload yt = (YouTubeDownload) t;
                if (yt.isComplete() || !yt.isDownloading()) {
                    return true;
                }

            } else if (t instanceof SoundcloudDownload) {
                SoundcloudDownload sd = (SoundcloudDownload) t;
                if (sd.isComplete() || !sd.isDownloading()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean someTransfersComplete(List<Transfer> transfers) {
        for (Transfer t : transfers) {
            if (t.isComplete()) {
                return true;
            }
        }
        return false;
    }

    private boolean someTransfersActive(List<Transfer> transfers) {
        for (Transfer t : transfers) {
            if (t instanceof BittorrentDownload) {
                BittorrentDownload bt = (BittorrentDownload) t;
                if (bt.isDownloading() || bt.isSeeding()) {
                    return true;
                }
            } else if (t instanceof HttpDownload) {
                HttpDownload ht = (HttpDownload) t;
                if (ht.isDownloading()) {
                    return true;
                }
            } else if (t instanceof YouTubeDownload) {
                YouTubeDownload yt = (YouTubeDownload) t;
                if (yt.isDownloading()) {
                    return true;
                }
            } else if (t instanceof SoundcloudDownload) {
                SoundcloudDownload sd = (SoundcloudDownload) t;
                if (sd.isDownloading()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startTransferFromURL() {
        String url = addTransferUrlTextView.getText();
        if (!StringUtils.isNullOrEmpty(url) && (url.startsWith("magnet") || url.startsWith("http"))) {
            toggleAddTransferControls();
            if (url.startsWith("http") && (url.contains("soundcloud.com/") || url.contains("youtube.com/"))) {
                startCloudTransfer(url);
            } else if (url.startsWith("http")) { //magnets are automatically started if found on the clipboard by autoPasteMagnetOrURL
                TransferManager.instance().downloadTorrent(url.trim(),
                        new HandpickedTorrentDownloadDialogOnFetch(getActivity()));
                UIUtils.showLongMessage(getActivity(), R.string.torrent_url_added);
            }
            addTransferUrlTextView.setText("");
        } else {
            UIUtils.showLongMessage(getActivity(), R.string.please_enter_valid_url);
        }
    }

    private void startCloudTransfer(String text) {
        if (text.contains("soundcloud.com/")) {
            new DownloadSoundcloudFromUrlTask(getActivity(), text.trim()).execute();
        } else if (text.contains("youtube.com/")) {
            startYouTubeSearchFromUrl(text.trim());
        } else {
            UIUtils.showLongMessage(getActivity(), R.string.cloud_downloads_coming);
        }
    }

    private void startYouTubeSearchFromUrl(String ytUrl) {
        //fragments are not supposed to communicate directly so I'll let my activity know
        //(NOTE: This is a poor implementation of fragment to fragment communication
        // despite what the android documentation says http://developer.android.com/training/basics/fragments/communicating.html
        // as this could not scale if you wanted to reuse fragments on other activities)
        MainActivity activity = (MainActivity) getActivity();
        activity.performYTSearch(ytUrl);
    }

    private void autoPasteMagnetOrURL() {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData primaryClip = clipboard.getPrimaryClip();
        if (primaryClip != null) {
            Item itemAt = primaryClip.getItemAt(0);
            try {
                CharSequence charSequence = itemAt.getText();

                if (charSequence != null) {
                    String text;

                    if (charSequence instanceof String) {
                        text = (String) charSequence;
                    } else {
                        text = charSequence.toString();
                    }

                    if (!StringUtils.isNullOrEmpty(text)) {
                        if (text.startsWith("http")) {
                            addTransferUrlTextView.setText(text.trim());
                        } else if (text.startsWith("magnet")) {
                            addTransferUrlTextView.setText(text.trim());
                            TransferManager.instance().downloadTorrent(text.trim(),
                                    new HandpickedTorrentDownloadDialogOnFetch(getActivity()));
                            UIUtils.showLongMessage(getActivity(), R.string.magnet_url_added);
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                            toggleAddTransferControls();
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void toggleAddTransferControls() {
        if (addTransferUrlTextView.getVisibility() == View.GONE) {
            addTransferUrlTextView.setVisibility(View.VISIBLE);
            autoPasteMagnetOrURL();
            showAddTransfersKeyboard();
        } else {
            addTransferUrlTextView.setVisibility(View.GONE);
            addTransferUrlTextView.setText("");
            hideAddTransfersKeyboard();
        }
    }

    private void showAddTransfersKeyboard() {
        if (addTransferUrlTextView.getVisibility() == View.VISIBLE && (addTransferUrlTextView.getText().startsWith("http") || addTransferUrlTextView.getText().isEmpty())) {
            UIUtils.showKeyboard(addTransferUrlTextView.getAutoCompleteTextView().getContext(), addTransferUrlTextView.getAutoCompleteTextView());
        }
    }

    private void hideAddTransfersKeyboard() {
        InputMethodManager imm = (InputMethodManager) addTransferUrlTextView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(addTransferUrlTextView.getWindowToken(), 0);
    }

    /**
     * Iterates over all the secondary external storage roots and returns the one with the most bytes available.
     */
    private static File getBiggestSDCardDir(Context context) {
        try {
            final File externalFilesDir = context.getExternalFilesDir(null);
            // this occurs on the android emulator
            if (externalFilesDir == null) {
                return null;
            }
            String primaryPath = externalFilesDir.getParent();

            long biggestBytesAvailable = -1;

            File result = null;

            for (File f : com.frostwire.android.util.SystemUtils.getExternalFilesDirs(context)) {
                if (!f.getAbsolutePath().startsWith(primaryPath)) {
                    long bytesAvailable = com.frostwire.android.util.SystemUtils.getAvailableStorageSize(f);
                    if (bytesAvailable > biggestBytesAvailable) {
                        biggestBytesAvailable = bytesAvailable;
                        result = f;
                    }
                }
            }
            //System.out.println("FW.SystemUtils.getSDCardDir() -> " + result.getAbsolutePath());
            // -> /storage/extSdCard/Android/data/com.frostwire.android/files
            return result;
        } catch (Throwable e) {
            // the context could be null due to a UI bad logic or context.getExternalFilesDir(null) could be null
            LOG.error("Error getting the biggest SD card", e);
        }

        return null;
    }

    private static final class TransferComparator implements Comparator<Transfer> {
        public int compare(Transfer lhs, Transfer rhs) {
            try {
                return -lhs.getCreated().compareTo(rhs.getCreated());
            } catch (Throwable e) {
                // ignore, not really super important
            }
            return 0;
        }
    }

    public enum TransferStatus {
        ALL, DOWNLOADING, COMPLETED;

        private static TransferStatus[] STATUS_ARRAY = new TransferStatus[]{
                ALL,
                DOWNLOADING,
                COMPLETED};

        public static TransferStatus[] getAllStatusesArray() {
            return STATUS_ARRAY;
        }
    }

    private static final class ButtonAddTransferListener extends ClickAdapter<TransfersFragment> {

        ButtonAddTransferListener(TransfersFragment f) {
            super(f);
        }

        @Override
        public void onClick(TransfersFragment f, View v) {
            f.toggleAddTransferControls();
        }
    }

    private static final class ButtonMenuListener extends ClickAdapter<TransfersFragment> {

        ButtonMenuListener(TransfersFragment f) {
            super(f);
        }

        @Override
        public void onClick(TransfersFragment f, View v) {
            f.showContextMenu();
        }
    }

    private static final class AddTransferTextListener extends ClickAdapter<TransfersFragment> implements OnItemClickListener, OnActionListener {

        AddTransferTextListener(TransfersFragment owner) {
            super(owner);
        }

        @Override
        public boolean onKey(TransfersFragment owner, View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                owner.startTransferFromURL();
                return true;
            }
            return false;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (Ref.alive(ownerRef)) {
                TransfersFragment owner = ownerRef.get();
                owner.startTransferFromURL();
            }
        }

        @Override
        public void onClear(View v) {
            if (Ref.alive(ownerRef)) {
                //TransfersFragment owner = ownerRef.get();
                //might clear.
                LOG.debug("onClear");
            }
        }

        @Override
        public void onTextChanged(View v, String str) {
        }
    }

    private static final class ButtonTabListener extends ClickAdapter<TransfersFragment> {

        private final TransferStatus status;

        ButtonTabListener(TransfersFragment f, TransferStatus status) {
            super(f);
            this.status = status;
        }

        @Override
        public void onClick(TransfersFragment f, View v) {
            f.selectedStatus = status;
            f.onTime();
        }
    }

    private static final class SDCardNotificationListener extends ClickAdapter<TransfersFragment> {

        SDCardNotificationListener(TransfersFragment owner) {
            super(owner);
        }

        @Override
        public void onClick(TransfersFragment owner, View v) {
            Intent i = new Intent(owner.getActivity(), SettingsActivity.class);
            i.setAction(Constants.ACTION_SETTINGS_SELECT_STORAGE);
            owner.getActivity().startActivity(i);
        }
    }
}
