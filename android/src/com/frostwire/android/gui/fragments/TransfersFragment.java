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

package com.frostwire.android.gui.fragments;

import static com.frostwire.android.util.SystemUtils.postToHandler;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.SettingsActivity;
import com.frostwire.android.gui.activities.VPNStatusDetailActivity;
import com.frostwire.android.gui.adapters.TransferListAdapter;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.fragments.preference.TorrentPreferenceFragment;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.tasks.AsyncDownloadSoundcloudFromUrl;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.ClearableEditTextView;
import com.frostwire.android.gui.views.ClearableEditTextView.OnActionListener;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;
import com.frostwire.android.gui.views.TransfersNoSeedsView;
import com.frostwire.android.offers.FWBannerView;
import com.frostwire.android.offers.HeaderBanner;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;
import com.frostwire.util.TaskThrottle;
import com.google.android.material.tabs.TabLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class TransfersFragment extends AbstractFragment implements TimerObserver, MainFragment {
    private static final Logger LOG = Logger.getLogger(TransfersFragment.class);
    private static final String SELECTED_STATUS_STATE_KEY = "selected_status";
    private static final int TRANSFERS_FRAGMENT_SUBSCRIPTION_INTERVAL_IN_SECS = 2;
    private final Comparator<Transfer> transferComparator;
    private final TransferStatus[] tabPositionToTransferStatus;
    private TabLayout tabLayout;
    private RecyclerView list;
    private TransferListAdapter adapter;
    private RecyclerView.LayoutManager recyclerViewLayoutManager;

    private TextView textDHTPeers;
    private TextView textDownloads;
    private TextView textUploads;
    private ImageView vpnStatusIcon;
    private TextView vpnRichToast;
    private ClearableEditTextView addTransferUrlTextView;
    private TransferStatus selectedStatus;
    private static TimerSubscription subscription;
    private boolean isVPNactive;
    private static boolean firstTimeShown = true;
    private final Handler vpnRichToastHandler;
    private boolean showTorrentSettingsOnClick;
    private TransfersNoSeedsView transfersNoSeedsView;
    private HeaderBanner headerBanner;
    private FWBannerView supportBanner;

    public TransfersFragment() {
        super(R.layout.fragment_transfers);
        this.transferComparator = new TransferComparator();
        setHasOptionsMenu(true);
        selectedStatus = TransferStatus.ALL;
        vpnRichToastHandler = new Handler();
        tabPositionToTransferStatus = new TransferStatus[]{TransferStatus.ALL, TransferStatus.DOWNLOADING, TransferStatus.SEEDING, TransferStatus.COMPLETED};
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
        initTimerServiceSubscription();
    }

    private void tryTimerServiceUnsubscribe() {
        if (subscription != null && subscription.isSubscribed()) {
            subscription.unsubscribe();
        }
    }

    private void initTimerServiceSubscription() {
        tryTimerServiceUnsubscribe();
        if (subscription != null) {
            TimerService.reSubscribe(this, subscription, TRANSFERS_FRAGMENT_SUBSCRIPTION_INTERVAL_IN_SECS);
        } else {
            subscription = TimerService.subscribe(this, TRANSFERS_FRAGMENT_SUBSCRIPTION_INTERVAL_IN_SECS);
        }
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
        menu.findItem(R.id.fragment_transfers_menu_seed_all).setVisible(false);
        menu.findItem(R.id.fragment_transfers_menu_stop_seeding_all).setVisible(false);
        updateMenuItemVisibility(menu);
        super.onPrepareOptionsMenu(menu);
    }

    private void updateMenuItemVisibility(Menu menu) {
        TransferManager tm = TransferManager.instance();
        boolean bittorrentDisconnected = tm.isBittorrentDisconnected();
        final List<Transfer> transfers = tm.getTransfers();
        if (transfers.size() > 0) {
            if (someTransfersComplete(transfers) || someTransfersErrored(transfers)) {
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
            if (!someTransfersSeeding(transfers) && someTransfersComplete(transfers)) {
                menu.findItem(R.id.fragment_transfers_menu_seed_all).setVisible(true);
            }
            if (someTransfersSeeding(transfers) && someTransfersComplete(transfers)) {
                menu.findItem(R.id.fragment_transfers_menu_seed_all).setVisible(true);
                menu.findItem(R.id.fragment_transfers_menu_stop_seeding_all).setVisible(true);
            }
            if (someTransfersSeeding(transfers)) {
                menu.findItem(R.id.fragment_transfers_menu_stop_seeding_all).setVisible(true);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean bittorrentDisconnected = TransferManager.instance().isBittorrentDisconnected();
        // Handle item selection
        setupAdapter(getActivity());
        int itemId = item.getItemId();
        if (itemId == R.id.fragment_transfers_menu_add_transfer) {
            toggleAddTransferControls();
            getActivity().invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.fragment_transfers_menu_clear_all) {
            TransferManager.instance().clearComplete();
            getActivity().invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.fragment_transfers_menu_pause_stop_all) {
            TransferManager.instance().stopHttpTransfers();
            TransferManager.instance().pauseTorrents();
            return true;
        } else if (itemId == R.id.fragment_transfers_menu_resume_all) {
            if (bittorrentDisconnected) {
                UIUtils.showLongMessage(getActivity(), R.string.cant_resume_torrent_transfers);
            } else {
                if (NetworkManager.instance().isInternetDataConnectionUp()) {
                    TransferManager.instance().resumeResumableTransfers();
                } else {
                    UIUtils.showShortMessage(getActivity(), R.string.please_check_connection_status_before_resuming_download);
                }
            }
            return true;
        } else if (itemId == R.id.fragment_transfers_menu_seed_all) {
            new SeedAction(getActivity()).onClick();
            return true;
        } else if (itemId == R.id.fragment_transfers_menu_stop_seeding_all) {
            TransferManager.instance().stopSeedingTorrents();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        HeaderBanner.onResumeHideOrUpdate(headerBanner);
        if (supportBanner != null) {
            supportBanner.setCompactMode(true);
            if (!supportBanner.isLoaded()) {
                supportBanner.loadMaxBanner();
            } else {
                supportBanner.setVisibility(View.VISIBLE);
            }
        }
        initTimerServiceSubscription();
        onTime();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (subscription != null) {
            subscription.unsubscribe();
        }
        adapter = null;
        HeaderBanner.destroy(headerBanner);
        if (supportBanner != null) {
            supportBanner.destroy();
            supportBanner = null;
        }
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
        tryTimerServiceUnsubscribe();
    }

    private static class TransfersHolder {
        final List<Transfer> allTransfers;
        final List<Transfer> sortedSelectedStatusTransfers;

        public TransfersHolder(List<Transfer> allTransfers, List<Transfer> selectedStatusTransfers) {
            this.allTransfers = allTransfers;
            this.sortedSelectedStatusTransfers = selectedStatusTransfers;
        }
    }

    private TransfersHolder sortSelectedStatusTransfersInBackground() {
        List<Transfer> allTransfers = TransferManager.instance().getTransfers();
        final List<Transfer> selectedStatusTransfers = filter(allTransfers, selectedStatus);
        selectedStatusTransfers.sort(transferComparator);
        return new TransfersHolder(allTransfers, selectedStatusTransfers);
    }

    public void updateTransferList(TransfersHolder transfersHolder) {
        if (isVisible() && isAdded()) {
            if (adapter != null) {
                adapter.updateList(transfersHolder.sortedSelectedStatusTransfers);
            }
            if (selectedStatus == TransferStatus.SEEDING) {
                TransfersNoSeedsView.Mode mode = handlePossibleSeedingSuggestions(transfersHolder.allTransfers);
                transfersNoSeedsView.setMode(mode);
            }
        }
    }

    @Override
    public void onTime() {
        if (!isVisible()) {
            return;
        }
        if (adapter != null) {
            if (TaskThrottle.isReadyToSubmitTask("TransfersFragment::sortSelectedStatusTransfersInBackground", (TRANSFERS_FRAGMENT_SUBSCRIPTION_INTERVAL_IN_SECS * 1000) - 100)) {
                WeakReference<TransfersFragment> contextRef = Ref.weak(this);
                postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER,
                        () -> {
                            if (!Ref.alive(contextRef)) {
                                Ref.free(contextRef);
                                return;
                            }
                            TransfersHolder transfersHolder;
                            try {
                                transfersHolder = contextRef.get().sortSelectedStatusTransfersInBackground();
                            } catch (Throwable t) {
                                LOG.error("onTime() " + t.getMessage(), t);
                                Ref.free(contextRef);
                                return;
                            }
                            if (!Ref.alive(contextRef)) {
                                Ref.free(contextRef);
                                return;
                            }
                            final TransfersHolder tfCopy = transfersHolder;

                            if (contextRef.get().getActivity() == null) {
                                return;
                            }

                            contextRef.get().getActivity().runOnUiThread(() -> {
                                if (!Ref.alive(contextRef)) {
                                    Ref.free(contextRef);
                                    return;
                                }
                                try {
                                    contextRef.get().updateTransferList(tfCopy);
                                } catch (Throwable t) {
                                    LOG.error("onTime() " + t.getMessage(), t);
                                    Ref.free(contextRef);
                                }
                            });

                        });
            } else {
                LOG.warn("onTime(): check your logic, TransfersFragment::sortSelectedStatusTransfersInBackground was not submitted, interval of " + TRANSFERS_FRAGMENT_SUBSCRIPTION_INTERVAL_IN_SECS * 1000 + " ms not enough");
            }
        } else if (this.getActivity() != null) {
            setupAdapter(this.getActivity());
        }
        // mark the selected tab
        int i = 0;
        for (TransferStatus transferStatus : tabPositionToTransferStatus) {
            if (transferStatus == selectedStatus) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null && !tab.isSelected()) {
                    if (SystemUtils.isUIThread()) {
                        tab.select();
                    } else {
                        try {
                            getActivity().runOnUiThread(() -> {
                                try {
                                    tab.select();
                                } catch (Throwable t) {
                                    if (BuildConfig.DEBUG) {
                                        throw t;
                                    }
                                    LOG.error("onTime() " + t.getMessage(), t);
                                }
                            });
                        } catch (Throwable ignored) {
                        }
                    }
                }
                break;
            }
            i++;
        }
        if (selectedStatus != TransferStatus.SEEDING) {
            transfersNoSeedsView.setMode(TransfersNoSeedsView.Mode.INACTIVE);
        }
        if (getActivity() != null && isVisible()) {
            getActivity().invalidateOptionsMenu();
        }
        if (BTEngine.ctx != null) {
            if (TaskThrottle.isReadyToSubmitTask("TransfersFragment::getStatusBarDataBackground", TRANSFERS_FRAGMENT_SUBSCRIPTION_INTERVAL_IN_SECS * 1000)) {
                SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> {
                    StatusBarData statusBarData = getStatusBarDataBackground();
                    SystemUtils.postToUIThread(() -> updateStatusBar(statusBarData));
                });
            }
            onCheckDHT();
        }
    }

    private static class StatusBarData {
        final String sDown;
        final String sUp;
        final int downloads;
        final int uploads;

        StatusBarData(String sDown, String sUp, int downloads, int uploads) {
            this.sDown = sDown;
            this.sUp = sUp;
            this.downloads = downloads;
            this.uploads = uploads;
        }
    }

    private StatusBarData getStatusBarDataBackground() {
        //  format strings
        return new StatusBarData(UIUtils.rate2speed(TransferManager.instance().getDownloadsBandwidth() >> 10),
                UIUtils.rate2speed(TransferManager.instance().getUploadsBandwidth() / 1024),
                // number of uploads (seeding) and downloads
                TransferManager.instance().getActiveDownloads(),
                TransferManager.instance().getActiveUploads());
    }

    private void updateStatusBar(StatusBarData statusBarData) {
        textDownloads.setText(statusBarData.downloads + " @ " + statusBarData.sDown);
        textUploads.setText(statusBarData.uploads + " @ " + statusBarData.sUp);
        // isTunnelUp is pre-calculated on some other thread, instant call
        updateVPNButtonIfStatusChanged(NetworkManager.instance().isTunnelUp());
    }

    private void updateVPNButtonIfStatusChanged(boolean vpnActive) {
        boolean wasActiveBefore = isVPNactive && !vpnActive;
        isVPNactive = vpnActive;
        if (vpnStatusIcon != null) {
            vpnStatusIcon.setImageResource(vpnActive ? R.drawable.notification_vpn_on : R.drawable.notification_vpn_off);
        }
        if (wasActiveBefore) {
            showVPNRichToast();
        }
    }

    private void onCheckDHT() {
        if (textDHTPeers == null || !TransfersFragment.this.isAdded() || BTEngine.ctx == null) {
            return;
        }
        textDHTPeers.setVisibility(View.VISIBLE);
        showTorrentSettingsOnClick = true;
        // No Internet
        if (!NetworkManager.instance().isInternetDataConnectionUp()) {
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
        BTEngine btEngine = BTEngine.getInstance();
        boolean dhtEnabled = btEngine.isDhtRunning();
        long dhtPeers = btEngine.stats().dhtNodes();
        // No DHT
        if (!dhtEnabled) {
            textDHTPeers.setVisibility(View.INVISIBLE);
            return;
        }
        // DHT On.
        textDHTPeers.setText(dhtPeers + " " + TransfersFragment.this.getString(R.string.dht_contacts));
    }

    @Override
    public View getHeader(FragmentActivity activity) {
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
        TabLayout.Tab tab = tabLayout.getTabAt(nextTabPosition);
        if (tab != null) {
            tab.select();
        }
    }

    public void selectStatusTab(TransferStatus status) {
        selectedStatus = status;
        onTime();
    }

    private TransfersNoSeedsView.Mode handlePossibleSeedingSuggestions(List<Transfer> transfers) {
        if (transfers.isEmpty()) {
            return TransfersNoSeedsView.Mode.INACTIVE;
        }
        boolean isNotSeeding = !ConfigurationManager.instance().isSeedFinishedTorrents();
        if (isNotSeeding) {
            return TransfersNoSeedsView.Mode.SEEDING_DISABLED;
        } else if (someTransfersFinished(transfers) && noTransfersSeeding(transfers)) {
            return TransfersNoSeedsView.Mode.SEED_ALL_FINISHED;
        } else {
            return TransfersNoSeedsView.Mode.INACTIVE;
        }
    }

    /**
     * NOTE: (Fragment) isVisible() will return false at this point in time. Use onHiddenChange(hidden)
     */
    @Override
    public void onShow() {
        if (firstTimeShown) {
            firstTimeShown = false;
            if (!NetworkManager.instance().isTunnelUp()) {
                showVPNRichToast();
            }
        }
    }

    /**
     * When onShown() is called the fragment is still not returning isVisible()==true
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
    }

    private void showVPNRichToast() {
        vpnRichToast.setVisibility(View.VISIBLE);
        long VPN_NOTIFICATION_DURATION = 10000;
        vpnRichToastHandler.postDelayed(() -> vpnRichToast.setVisibility(View.GONE), VPN_NOTIFICATION_DURATION);
    }

    @Override
    protected void initComponents(View v, Bundle savedInstanceState) {
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
        recyclerViewLayoutManager = new LinearLayoutManager(this.getActivity());
        // TODO: had to comment this out when I switched to RecyclerView
        //list.setOnScrollListener(new ScrollListeners.FastScrollDisabledWhenIdleOnScrollListener());
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
        textDHTPeers.setOnClickListener(v12 -> {
            Context ctx = v12.getContext();
            Intent i = new Intent(ctx, SettingsActivity.class);
            if (showTorrentSettingsOnClick) {
                i.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, TorrentPreferenceFragment.class.getName());
                i.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_TITLE, getString(R.string.torrent_preferences_header));
            }
            ctx.startActivity(i);
        });
        textDownloads = findView(v, R.id.fragment_transfers_text_downloads);
        textUploads = findView(v, R.id.fragment_transfers_text_uploads);
        vpnRichToast = findView(v, R.id.fragment_transfers_vpn_notification);
        vpnRichToast.setVisibility(View.GONE);
        vpnRichToast.setOnClickListener(v1 -> vpnRichToast.setVisibility(View.GONE));
        initVPNStatusButton(v);
        transfersNoSeedsView = findView(v, R.id.fragment_transfers_no_seeds_view);
        supportBanner = findView(v, R.id.fragment_transfers_support_banner);
        if (supportBanner != null) {
            supportBanner.setCompactMode(true);
            supportBanner.loadMaxBanner();
        }
        headerBanner = findView(v, R.id.fragment_header_banner);
    }

    private void initVPNStatusButton(View v) {
        vpnStatusIcon = findView(v, R.id.fragment_transfers_status_vpn_icon);
        vpnStatusIcon.setOnClickListener(v1 -> {
            Context ctx = v1.getContext();
            Intent i = new Intent(ctx, VPNStatusDetailActivity.class);
            i.setAction(isVPNactive ?
                            Constants.ACTION_SHOW_VPN_STATUS_PROTECTED :
                            Constants.ACTION_SHOW_VPN_STATUS_UNPROTECTED).
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.putExtra("from", "transfers");
            ctx.startActivity(i);
        });
    }

    private void setupAdapter(Context context) {
        if (context == null) {
            return;
        }
        List<Transfer> transfers = filter(TransferManager.instance().getTransfers(), selectedStatus);
        transfers.sort(transferComparator);
        adapter = new TransferListAdapter(context, transfers);
        list.setLayoutManager(recyclerViewLayoutManager);
        list.setAdapter(adapter);
    }

    private List<Transfer> filter(List<Transfer> transfers, TransferStatus status) {
        if (status == TransferStatus.ALL) {
            return transfers;
        }
        ArrayList<Transfer> filtered = new ArrayList<>(0);
        for (Transfer transfer : transfers) {
            if ((status == TransferStatus.DOWNLOADING && isDownloading(transfer)) ||
                    (status == TransferStatus.SEEDING && isSeeding(transfer) ||
                            (status == TransferStatus.COMPLETED && isCompleted(transfer)))) {
                filtered.add(transfer);
            }
        }
        return filtered;
    }

    private boolean isDownloading(Transfer transfer) {
        TransferState state = transfer.getState();
        return state == TransferState.CHECKING ||
                state == TransferState.DOWNLOADING ||
                state == TransferState.DEMUXING ||
                state == TransferState.DOWNLOADING_METADATA ||
                state == TransferState.DOWNLOADING_TORRENT ||
                state == TransferState.FINISHING ||
                state == TransferState.PAUSING ||
                state == TransferState.PAUSED;
    }

    private boolean isSeeding(Transfer transfer) {
        return transfer.getState() == TransferState.SEEDING;
    }

    private boolean isCompleted(Transfer transfer) {
        TransferState state = transfer.getState();
        return state == TransferState.FINISHED ||
                state == TransferState.COMPLETE;
    }

    private boolean someTransfersFinished(final List<Transfer> transfers) {
        if (transfers == null || transfers.isEmpty()) {
            return false;
        }
        for (Transfer t : transfers) {
            if (t.getState() == TransferState.FINISHED) {
                return true;
            }
        }
        return false;
    }

    private boolean noTransfersSeeding(final List<Transfer> transfers) {
        if (transfers == null || transfers.isEmpty()) {
            return true;
        }
        for (Transfer t : transfers) {
            if (t.getState() == TransferState.SEEDING) {
                return false;
            }
        }
        return true;
    }


    private boolean someTransfersInactive(final List<Transfer> transfers) {
        for (Transfer t : transfers) {
            TransferState state = t.getState();
            switch (state) {
                case PAUSED:
                case FINISHED:
                case ERROR:
                case CANCELED:
                case STOPPED:
                    return true;
            }
        }
        return false;
    }

    private boolean someTransfersComplete(final List<Transfer> transfers) {
        for (Transfer t : transfers) {
            if (t == null) {
                continue;
            }
            if (t.isComplete()) {
                return true;
            }
        }
        return false;
    }

    private boolean someTransfersSeeding(final List<Transfer> transfers) {
        for (Transfer t : transfers) {
            if (t == null) {
                continue;
            }
            if (t.getState() == TransferState.SEEDING) {
                return true;
            }
        }
        return false;
    }

    private boolean someTransfersActive(List<Transfer> transfers) {
        for (Transfer t : transfers) {
            if (t == null) {
                continue;
            }
            TransferState state = t.getState();
            if (state == TransferState.DOWNLOADING) {
                return true;
            }
        }
        return false;
    }

    private boolean someTransfersErrored(List<Transfer> transfers) {
        for (Transfer t : transfers) {
            if (t == null) {
                continue;
            }
            if (TransferState.isErrored(t.getState())) {
                return true;
            }
        }
        return false;
    }

    private void startTransferFromURL() {
        //magnets and urls
        // are automatically started if found on the clipboard by autoPasteMagnetOrURL
        String url = addTransferUrlTextView.getText();
        if (!StringUtils.isNullOrEmpty(url) && (url.startsWith("magnet") || url.startsWith("http"))) {
            toggleAddTransferControls();
            if (url.startsWith("http") && (url.contains("soundcloud.com/"))) {
                startCloudTransfer(url);
            } else if (url.startsWith("magnet:?xt=urn:btih:") ||
                    (url.startsWith("http") && url.endsWith(".torrent"))) {
                TransferManager.instance().downloadTorrent(url.trim(),
                        new HandpickedTorrentDownloadDialogOnFetch((AppCompatActivity) getActivity(), true));
                UIUtils.showLongMessage(getActivity(), R.string.torrent_url_added);
            } else if (url.startsWith("http") && !url.endsWith(".torrent")) {
                if (MainActivity.instance() != null && SearchFragment.instance() != null) {
                    MainActivity.instance().switchContent(SearchFragment.instance());
                    SearchFragment.instance().performTellurideSearch(url);
                }
            }
            addTransferUrlTextView.setText("");
        } else {
            UIUtils.showLongMessage(getActivity(), R.string.please_enter_valid_url);
        }
    }

    private void startCloudTransfer(String text) {
        if (text.contains("soundcloud.com/")) {
            new AsyncDownloadSoundcloudFromUrl(getActivity(), text.trim());
        } else {
            UIUtils.showLongMessage(getActivity(), R.string.cloud_downloads_coming);
        }
    }



    private void toggleAddTransferControls() {
        if (addTransferUrlTextView.getVisibility() == View.GONE) {
            addTransferUrlTextView.setVisibility(View.VISIBLE);
            UIUtils.autoPasteMagnetOrURL(getContext(), addTransferUrlTextView);
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
        if (imm != null) {
            imm.hideSoftInputFromWindow(addTransferUrlTextView.getWindowToken(), 0);
        }
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
        ALL, DOWNLOADING, COMPLETED, SEEDING
    }

    public void onClick(TransfersFragment f) {
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
    }
}
