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
import android.support.design.widget.TabLayout;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.StoragePicker;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.SettingsActivity;
import com.frostwire.android.gui.activities.VPNStatusDetailActivity;
import com.frostwire.android.gui.adapters.TransferListAdapter;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.fragments.preference.ApplicationFragment;
import com.frostwire.android.gui.fragments.preference.TorrentFragment;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.tasks.DownloadSoundcloudFromUrlTask;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.ClearableEditTextView;
import com.frostwire.android.gui.views.ClearableEditTextView.OnActionListener;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.RichNotification;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.SoundcloudDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.YouTubeDownload;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class TransfersFragment extends AbstractFragment implements TimerObserver, MainFragment {
    private static final Logger LOG = Logger.getLogger(TransfersFragment.class);
    private static final String SELECTED_STATUS_STATE_KEY = "selected_status";
    private final Comparator<Transfer> transferComparator;
    private final TransferStatus[] tabPositionToTransferStatus;
    private TabLayout tabLayout;
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
        setHasOptionsMenu(true);
        selectedStatus = TransferStatus.ALL;
        vpnRichToastHandler = new Handler();
        tabPositionToTransferStatus = new TransferStatus[]{TransferStatus.ALL, TransferStatus.DOWNLOADING, TransferStatus.COMPLETED};
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_transfers_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.fragment_transfers_menu_pause_stop_all).setVisible(false);
        menu.findItem(R.id.fragment_transfers_menu_clear_all).setVisible(false);
        menu.findItem(R.id.fragment_transfers_menu_resume_all).setVisible(false);
        updateMenuItemVisibility(menu);
        super.onPrepareOptionsMenu(menu);
    }

    private void updateMenuItemVisibility(Menu menu) {
        TransferManager tm = TransferManager.instance();
        boolean bittorrentDisconnected = tm.isBittorrentDisconnected();
        final List<Transfer> transfers = tm.getTransfers();

        if (transfers != null && transfers.size() > 0) {
            if (someTransfersComplete(transfers)) {
                menu.findItem(R.id.fragment_transfers_menu_clear_all).setVisible(true);
            }

            if (!bittorrentDisconnected) {
                if (someTransfersActive(transfers)) {
                    menu.findItem(R.id.fragment_transfers_menu_pause_stop_all).setVisible(true);
                }
            }

            //let's show it even if bittorrent is disconnected
            //user should get a message telling them to check why they can't resume.
            //Preferences > Connectivity is disconnected.
            if (someTransfersInactive(transfers)) {
                menu.findItem(R.id.fragment_transfers_menu_resume_all).setVisible(true);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        // Handle item selection
        setupAdapter();

        switch (item.getItemId()) {
            case R.id.fragment_transfers_menu_add_transfer:
                toggleAddTransferControls();
                getActivity().invalidateOptionsMenu();
                return true;
            case R.id.fragment_transfers_menu_clear_all:
                TransferManager.instance().clearComplete();
                getActivity().invalidateOptionsMenu();
                return true;
            case R.id.fragment_transfers_menu_pause_stop_all:
                TransferManager.instance().stopHttpTransfers();
                TransferManager.instance().pauseTorrents();
                return true;
            case R.id.fragment_transfers_menu_resume_all:
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
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

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
        onCheckDHT();
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

    private void onCheckDHT() {
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
        boolean dhtEnabled = BTEngine.getInstance().isDhtRunning();
        long dhtPeers = BTEngine.getInstance().stats().dhtNodes();
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
        View header = inflater.inflate(R.layout.view_transfers_header, null, false);
        TextView text = findView(header, R.id.view_transfers_header_text_title);
        text.setText(R.string.transfers);
        return header;
    }

    public void selectStatusTabToThe(boolean right) {
        int currentTabPosition = tabLayout.getSelectedTabPosition();
        int nextTabPosition = (right ? ++currentTabPosition : --currentTabPosition) % tabPositionToTransferStatus.length;
        if (nextTabPosition == -1) {
            nextTabPosition = tabPositionToTransferStatus.length - 1;
        }
        tabLayout.getTabAt(nextTabPosition).select();
    }

    public void selectStatusTab(TransferStatus status) {
        selectedStatus = status;
        onTime();
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
    protected void initComponents(View v, Bundle savedInstanceState) {
        initStorageRelatedRichNotifications(v);
        tabLayout = findView(v, R.id.fragment_transfers_layout_tab_layout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectStatusTab(tabPositionToTransferStatus[tab.getPosition()]);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                selectStatusTab(tabPositionToTransferStatus[tab.getPosition()]);
            }
        });
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
                    i.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, TorrentFragment.class.getName());
                    i.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_TITLE, getString(R.string.torrent_preferences_header));
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
                sdCardNotification.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showStoragePreference();
                    }
                });
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
            internalMemoryNotification.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showStoragePreference();
                }
            });
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
    }


    public void onClick(TransfersFragment f, View v) {
        f.toggleAddTransferControls();
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

    private void showStoragePreference() {
        Activity activity = getActivity();
        if (activity == null) {
            return; // quick return
        }
        if (AndroidPlatform.saf()) {
            StoragePicker.show(activity);
        } else {
            Intent i = new Intent(activity, SettingsActivity.class);
            i.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, ApplicationFragment.class.getName());
            activity.startActivity(i);
        }
    }
}
