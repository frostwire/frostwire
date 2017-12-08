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

package com.frostwire.android.gui.adapters;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.adapters.menu.CancelMenuAction;
import com.frostwire.android.gui.adapters.menu.CopyToClipboardMenuAction;
import com.frostwire.android.gui.adapters.menu.OpenMenuAction;
import com.frostwire.android.gui.adapters.menu.PauseDownloadMenuAction;
import com.frostwire.android.gui.adapters.menu.ResumeDownloadMenuAction;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.adapters.menu.SendBitcoinTipAction;
import com.frostwire.android.gui.adapters.menu.SendFiatTipAction;
import com.frostwire.android.gui.adapters.menu.StopSeedingAction;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.android.gui.views.MenuBuilder;
import com.frostwire.bittorrent.BTDownloadItem;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.search.WebSearchPerformer;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.SoundcloudDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import com.frostwire.transfers.YouTubeDownload;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class TransferListAdapter extends BaseExpandableListAdapter {
    private static final Logger LOG = Logger.getLogger(TransferListAdapter.class);
    private final WeakReference<Context> context;
    private final OnClickListener viewOnClickListener;
    private final ViewOnLongClickListener viewOnLongClickListener;
    private final OpenOnClickListener playOnClickListener;

    /**
     * Keep track of all dialogs ever opened so we dismiss when we leave to avoid memory leaks
     */
    private final List<Dialog> dialogs;
    private List<Transfer> list;
    private final Map<TransferState, String> TRANSFER_STATE_STRING_MAP = new HashMap<>();

    public TransferListAdapter(Context context, List<Transfer> list) {
        this.context = new WeakReference<>(context);
        this.viewOnClickListener = new ViewOnClickListener();
        this.viewOnLongClickListener = new ViewOnLongClickListener();
        this.playOnClickListener = new OpenOnClickListener(context);
        this.dialogs = new ArrayList<>();
        this.list = list.equals(Collections.emptyList()) ? new ArrayList<>() : list;
        initTransferStateStringMap();
    }

    private void initTransferStateStringMap() {
        Context c = context.get();
        TRANSFER_STATE_STRING_MAP.put(TransferState.FINISHING, c.getString(R.string.finishing));
        TRANSFER_STATE_STRING_MAP.put(TransferState.CHECKING, c.getString(R.string.checking_ellipsis));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING_METADATA, c.getString(R.string.downloading_metadata));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING_TORRENT, c.getString(R.string.torrent_fetcher_download_status_downloading_torrent));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING, c.getString(R.string.azureus_manager_item_downloading));
        TRANSFER_STATE_STRING_MAP.put(TransferState.FINISHED, c.getString(R.string.azureus_peer_manager_status_finished));
        TRANSFER_STATE_STRING_MAP.put(TransferState.SEEDING, c.getString(R.string.azureus_manager_item_seeding));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ALLOCATING, c.getString(R.string.azureus_manager_item_allocating));
        TRANSFER_STATE_STRING_MAP.put(TransferState.PAUSED, c.getString(R.string.azureus_manager_item_paused));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR, c.getString(R.string.azureus_manager_item_error));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_MOVING_INCOMPLETE, c.getString(R.string.error_moving_incomplete));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_HASH_MD5, c.getString(R.string.error_wrong_md5_hash));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_SIGNATURE, c.getString(R.string.error_wrong_signature));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_NOT_ENOUGH_PEERS, c.getString(R.string.error_not_enough_peers));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_NO_INTERNET, c.getString(R.string.error_no_internet_connection));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_SAVE_DIR, c.getString(R.string.http_download_status_save_dir_error));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_TEMP_DIR, c.getString(R.string.http_download_status_temp_dir_error));
        TRANSFER_STATE_STRING_MAP.put(TransferState.STOPPED, c.getString(R.string.azureus_manager_item_stopped));
        TRANSFER_STATE_STRING_MAP.put(TransferState.PAUSING, c.getString(R.string.pausing));
        TRANSFER_STATE_STRING_MAP.put(TransferState.CANCELING, c.getString(R.string.canceling));
        TRANSFER_STATE_STRING_MAP.put(TransferState.CANCELED, c.getString(R.string.torrent_fetcher_download_status_canceled));
        TRANSFER_STATE_STRING_MAP.put(TransferState.WAITING, c.getString(R.string.waiting));
        TRANSFER_STATE_STRING_MAP.put(TransferState.COMPLETE, c.getString(R.string.peer_http_download_status_complete));
        TRANSFER_STATE_STRING_MAP.put(TransferState.UPLOADING, c.getString(R.string.peer_http_upload_status_uploading));
        TRANSFER_STATE_STRING_MAP.put(TransferState.UNCOMPRESSING, c.getString(R.string.http_download_status_uncompressing));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DEMUXING, c.getString(R.string.transfer_status_demuxing));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_DISK_FULL, c.getString(R.string.error_no_space_left_on_device));
        TRANSFER_STATE_STRING_MAP.put(TransferState.SCANNING, c.getString(R.string.scanning));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_CONNECTION_TIMED_OUT, c.getString(R.string.error_connection_timed_out));
        TRANSFER_STATE_STRING_MAP.put(TransferState.UNKNOWN, "");
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return list.get(groupPosition).getItems().get(childPosition);
    }

    private TransferItem getChildItem(int groupPosition, int childPosition) {
        return list.get(groupPosition).getItems().get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

        TransferItem item = getChildItem(groupPosition, childPosition);

        if (convertView == null) {
            convertView = View.inflate(context.get(), R.layout.view_transfer_item_list_item, null);
            convertView.setOnClickListener(viewOnClickListener);
            convertView.setOnLongClickListener(viewOnLongClickListener);
        }

        try {
            initTouchFeedback(convertView, item);
            populateChildView(convertView, item);
        } catch (Throwable e) {
            LOG.error("Fatal error getting view: " + e.getMessage(), e);
        }

        return convertView;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        try {
            final Transfer transfer = list.get(groupPosition);
            int size = transfer.getItems().size();
            return size <= 1 ? 0 : size;
        } catch (IndexOutOfBoundsException e) {
            return 0;
        }
    }

    @Override
    public Object getGroup(int groupPosition) {
        return list.get(groupPosition);
    }

    private Transfer getGroupItem(int groupPosition) {
        try {
            return list.get(groupPosition);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public int getGroupCount() {
        return list.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        Transfer item = getGroupItem(groupPosition);
        ExpandableListView expandableListView = (ExpandableListView) parent;
        LinearLayout listItemLinearLayoutHolder = (LinearLayout) convertView;
        if (convertView == null) { //if we don't have it yet, we inflate it ourselves.
            convertView = View.inflate(context.get(), R.layout.view_transfer_list_item, null);
            if (convertView instanceof LinearLayout) {
                listItemLinearLayoutHolder = (LinearLayout) convertView;
            }
        }

        listItemLinearLayoutHolder.setOnClickListener(viewOnClickListener);
        listItemLinearLayoutHolder.setOnLongClickListener(viewOnLongClickListener);
        listItemLinearLayoutHolder.setClickable(true);
        listItemLinearLayoutHolder.setLongClickable(true);
        listItemLinearLayoutHolder.setTag(item);

        try {
            populateGroupView(listItemLinearLayoutHolder, item);
        } catch (Throwable e) {
            LOG.error("Not able to populate group view in expandable list:" + e.getMessage());
        }

        try {
            setupGroupIndicator(listItemLinearLayoutHolder, expandableListView, isExpanded, item, groupPosition);
        } catch (Throwable e) {
            LOG.error("Not able to setup touch handlers for group indicator ImageView: " + e.getMessage());
        }

        return listItemLinearLayoutHolder;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }

    public void updateList(List<Transfer> g) {
        list = g;
        notifyDataSetChanged();
    }

    public void dismissDialogs() {
        for (Dialog dialog : dialogs) {
            try {
                dialog.dismiss();
            } catch (Throwable e) {
                LOG.warn("Error dismissing dialog", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <TView extends View> TView findView(View view, int id) {
        return (TView) view.findViewById(id);
    }

    private void populateGroupView(View view, Transfer transfer) {
        if (transfer instanceof BittorrentDownload) {
            populateBittorrentDownload(view, (BittorrentDownload) transfer);
        } else if (transfer instanceof YouTubeDownload ||
                transfer instanceof SoundcloudDownload) {
            populateCloudDownload(view, transfer);
        } else if (transfer instanceof HttpDownload) {
            populateHttpDownload(view, (HttpDownload) transfer);
        }
    }

    private void populateChildView(View view, TransferItem item) {
        populateBittorrentDownloadItem(view, item);
    }

    private MenuAdapter getMenuAdapter(View view) {
        Object tag = view.getTag();
        String title = "";
        List<MenuAction> items = new ArrayList<>();
        if (tag instanceof BittorrentDownload) {
            title = populateBittorrentDownloadMenuActions((BittorrentDownload) tag, items);
        } else if (tag instanceof Transfer) {
            title = populateCloudDownloadMenuActions(tag, items);
        }
        return items.size() > 0 ? new MenuAdapter(context.get(), title, items) : null;
    }

    private String populateCloudDownloadMenuActions(Object tag, List<MenuAction> items) {
        Transfer download = (Transfer) tag;
        String title = download.getDisplayName();
        boolean errored = download.getState().name().contains("ERROR");
        boolean finishedSuccessfully = !errored && download.isComplete() && isCloudDownload(tag);
        if (finishedSuccessfully && Ref.alive(context)) {
            final List<FileDescriptor> files = Librarian.instance().getFiles(context.get(), download.getSavePath().getAbsolutePath(), true);
            boolean singleFile = files != null && files.size() == 1;
            if (singleFile && !AndroidPlatform.saf(new File(files.get(0).filePath))) {
                items.add(new SeedAction(context.get(), files.get(0), download));
            }
            if (singleFile && files.get(0).fileType == Constants.FILE_TYPE_PICTURES) {
                items.add(new OpenMenuAction(context.get(), download.getDisplayName(), files.get(0)));
            } else {
                items.add(new OpenMenuAction(context.get(), download.getDisplayName(), download.getSavePath().getAbsolutePath(), extractMime(download)));
            }
        }
        if (Ref.alive(context)) {
            items.add(new CancelMenuAction(context.get(), download, !finishedSuccessfully));
        }
        return title;
    }

    private boolean isCloudDownload(Object tag) {
        return tag instanceof HttpDownload || tag instanceof YouTubeDownload;
    }

    private String populateBittorrentDownloadMenuActions(BittorrentDownload download, List<MenuAction> items) {
        String title;
        title = download.getDisplayName();

        //If it's a torrent download with a single file, we should be able to open it.
        if (download.isComplete() && download.getItems().size() > 0) {
            TransferItem transferItem = download.getItems().get(0);
            String path = transferItem.getFile().getAbsolutePath();
            String mimeType = UIUtils.getMimeType(path);
            items.add(new OpenMenuAction(context.get(), path, mimeType));
        }

        if (!download.isComplete() && !download.isSeeding()) {
            if (!download.isPaused()) {
                items.add(new PauseDownloadMenuAction(context.get(), download));
            } else {
                NetworkManager networkManager = NetworkManager.instance();
                boolean wifiIsUp = networkManager.isDataWIFIUp(networkManager.getConnectivityManager());
                boolean bittorrentOnMobileData = !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);

                if (wifiIsUp || bittorrentOnMobileData) {
                    if (!download.isComplete()) {
                        items.add(new ResumeDownloadMenuAction(context.get(), download, R.string.resume_torrent_menu_action));
                    }
                }
            }
        }

        if (download.getState() == TransferState.FINISHED) {
            items.add(new SeedAction(context.get(), download));
        }

        if (download.getState() == TransferState.SEEDING) {
            items.add(new StopSeedingAction(context.get(), download));
        }

        items.add(new CancelMenuAction(context.get(), download, !download.isComplete()));

        items.add(new CopyToClipboardMenuAction(context.get(),
                R.drawable.contextmenu_icon_magnet,
                R.string.transfers_context_menu_copy_magnet,
                R.string.transfers_context_menu_copy_magnet_copied,
                download.magnetUri() + BTEngine.getInstance().magnetPeers()
        ));

        items.add(new CopyToClipboardMenuAction(context.get(),
                R.drawable.contextmenu_icon_copy,
                R.string.transfers_context_menu_copy_infohash,
                R.string.transfers_context_menu_copy_infohash_copied,
                download.getInfoHash()
        ));

        if (download.isComplete()) {
            // Remove Torrent and Data action.
            items.add(new CancelMenuAction(context.get(), download, true, true));
        }

        if (download instanceof UIBittorrentDownload) {
            UIBittorrentDownload uidl = (UIBittorrentDownload) download;
            if (uidl.hasPaymentOptions()) {
                PaymentOptions po = uidl.getPaymentOptions();
                if (po.bitcoin != null) {
                    items.add(new SendBitcoinTipAction(context.get(), po.bitcoin));
                }

                if (po.paypalUrl != null) {
                    items.add(new SendFiatTipAction(context.get(), po.paypalUrl));
                }
            }
        }
        return title;
    }

    private String extractMime(Transfer download) {
        return UIUtils.getMimeType(download.getSavePath().getAbsolutePath());
    }

    private void trackDialog(Dialog dialog) {
        dialogs.add(dialog);
    }

    private void setupGroupIndicator(final LinearLayout listItemMainLayout,
                                     final ExpandableListView expandableListView,
                                     final boolean expanded,
                                     final Transfer item,
                                     final int groupPosition) {
        final ImageView groupIndicator = findView(listItemMainLayout, R.id.view_transfer_list_item_group_indicator);
        groupIndicator.setClickable(true);
        final int totalItems = item != null ? item.getItems().size() : 0;
        prepareGroupIndicatorDrawable(item, groupIndicator, totalItems > 1, expanded);

        if (totalItems > 1) {
            groupIndicator.setOnClickListener(new GroupIndicatorClickAdapter(expandableListView, groupPosition));
        }
    }

    private void prepareGroupIndicatorDrawable(final Transfer item,
                                               final ImageView groupIndicator,
                                               final boolean hasMultipleFiles,
                                               final boolean expanded) {
        if (hasMultipleFiles) {
            groupIndicator.setImageResource(expanded ? R.drawable.transfer_menuitem_minus : R.drawable.transfer_menuitem_plus);
        } else {
            String path = null;
            if (item instanceof BittorrentDownload) {
                BittorrentDownload bItem = (BittorrentDownload) item;
                if (bItem.getItems().size() > 0) {
                    TransferItem transferItem = bItem.getItems().get(0);
                    path = transferItem.getFile().getAbsolutePath();
                }
            } else if (item != null) {
                if (item.getSavePath() != null) {
                    path = item.getSavePath().getAbsolutePath();
                }
            }

            String extension = null;
            if (path != null) {
                extension = FilenameUtils.getExtension(path);
            }

            if (extension != null && extension.equals("apk")) {
                try {
                    //Apk apk = new Apk(context,path);

                    //TODO: Get the APK Icon so we can show the APK icon on the transfer manager once
                    //it's finished downloading, or as it's uploading to another peer.
                    //apk.getDrawable(id);

                    //in the meantime, just hardcode it
                    groupIndicator.setImageResource(R.drawable.my_files_application_icon_selector_menu);
                } catch (Throwable e) {
                    groupIndicator.setImageResource(R.drawable.my_files_application_icon_selector_menu);
                }
            } else {
                groupIndicator.setImageResource(MediaType.getFileTypeIconId(extension));
            }
        }
    }

    private void initTouchFeedback(View v, TransferItem item) {
        v.setOnClickListener(viewOnClickListener);
        v.setOnLongClickListener(viewOnLongClickListener);
        v.setTag(item);

        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;

            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = vg.getChildAt(i);
                initTouchFeedback(child, item);
            }
        }
    }

    private void populateBittorrentDownload(View view, BittorrentDownload download) {
        TextView title = findView(view, R.id.view_transfer_list_item_title);
        ProgressBar progress = findView(view, R.id.view_transfer_list_item_progress);
        TextView status = findView(view, R.id.view_transfer_list_item_status);
        TextView speed = findView(view, R.id.view_transfer_list_item_speed);
        TextView size = findView(view, R.id.view_transfer_list_item_size);

        TextView seeds = findView(view, R.id.view_transfer_list_item_seeds);
        TextView peers = findView(view, R.id.view_transfer_list_item_peers);

        ImageButton buttonPlay = findView(view, R.id.view_transfer_list_item_button_play);

        seeds.setText(context.get().getString(R.string.seeds_n, formatSeeds(download)));
        peers.setText(context.get().getString(R.string.peers_n, formatPeers(download)));
        seeds.setVisibility(View.VISIBLE);
        peers.setVisibility(View.VISIBLE);


        title.setText(download.getDisplayName());
        setProgress(progress, download.getProgress());
        title.setCompoundDrawables(null, null, null, null);

        final String downloadStatus = TRANSFER_STATE_STRING_MAP.get(download.getState());
        status.setText(downloadStatus);
        NetworkManager networkManager = NetworkManager.instance();
        if (!networkManager.isDataUp(networkManager.getConnectivityManager())) {
            status.setText(downloadStatus + " (" + view.getResources().getText(R.string.check_internet_connection) + ")");
            seeds.setText("");
            peers.setText("");
        }

        speed.setText(UIUtils.getBytesInHuman(download.getDownloadSpeed()) + "/s");
        size.setText(UIUtils.getBytesInHuman(download.getSize()));

        if (download instanceof UIBittorrentDownload) {
            UIBittorrentDownload uidl = (UIBittorrentDownload) download;
            if (uidl.hasPaymentOptions()) {
                setPaymentOptionDrawable(uidl, title);
            }
        }

        List<TransferItem> items = download.getItems();
        if (items != null && items.size() == 1) {
            TransferItem item = items.get(0);
            buttonPlay.setTag(item);
            updatePlayButtonVisibility(item, buttonPlay);
            buttonPlay.setOnClickListener(playOnClickListener);
        } else {
            buttonPlay.setVisibility(View.GONE);
        }
    }

    private static String formatPeers(BittorrentDownload dl) {
        int connectedPeers = dl.getConnectedPeers();
        int peers = dl.getTotalPeers();

        String tmp = connectedPeers > peers ? "%1" : "%1 " + "/" + " %2";

        tmp = tmp.replaceAll("%1", String.valueOf(connectedPeers));
        tmp = tmp.replaceAll("%2", String.valueOf(peers));

        return tmp;
    }

    private static String formatSeeds(BittorrentDownload dl) {
        int connectedSeeds = dl.getConnectedSeeds();
        int seeds = dl.getTotalSeeds();

        String tmp = connectedSeeds > seeds ? "%1" : "%1 " + "/" + " %2";

        tmp = tmp.replaceAll("%1", String.valueOf(connectedSeeds));
        String param2 = "?";
        if (seeds != -1) {
            param2 = String.valueOf(seeds);
        }
        tmp = tmp.replaceAll("%2", param2);

        return tmp;
    }

    private void setPaymentOptionDrawable(UIBittorrentDownload download, TextView title) {
        final PaymentOptions paymentOptions = download.getPaymentOptions();
        final Resources r = context.get().getResources();
        Drawable tipDrawable = (paymentOptions.bitcoin != null) ? r.getDrawable(R.drawable.contextmenu_icon_donation_bitcoin) : r.getDrawable(R.drawable.contextmenu_icon_donation_fiat);
        if (tipDrawable != null) {
            final int iconHeightInPixels = r.getDimensionPixelSize(R.dimen.view_transfer_list_item_title_left_drawable);
            tipDrawable.setBounds(0, 0, iconHeightInPixels, iconHeightInPixels);
            title.setCompoundDrawables(tipDrawable, null, null, null);
        }
    }

    private void populateHttpDownload(View view, HttpDownload download) {
        TextView title = findView(view, R.id.view_transfer_list_item_title);
        ProgressBar progress = findView(view, R.id.view_transfer_list_item_progress);
        TextView status = findView(view, R.id.view_transfer_list_item_status);
        TextView speed = findView(view, R.id.view_transfer_list_item_speed);
        TextView size = findView(view, R.id.view_transfer_list_item_size);
        TextView seeds = findView(view, R.id.view_transfer_list_item_seeds);
        TextView peers = findView(view, R.id.view_transfer_list_item_peers);
        ImageButton buttonPlay = findView(view, R.id.view_transfer_list_item_button_play);

        seeds.setText("");
        peers.setText("");
        title.setText(download.getDisplayName());
        title.setCompoundDrawables(null, null, null, null);
        setProgress(progress, download.getProgress());
        String downloadStatus = TRANSFER_STATE_STRING_MAP.get(download.getState());
        status.setText(downloadStatus);
        speed.setText(UIUtils.getBytesInHuman(download.getDownloadSpeed()) + "/s");
        size.setText(UIUtils.getBytesInHuman(download.getSize()));

        File previewFile = download.previewFile();
        if (previewFile != null && WebSearchPerformer.isStreamable(previewFile.getName())) {
            buttonPlay.setTag(previewFile);
            buttonPlay.setVisibility(View.VISIBLE);
            buttonPlay.setOnClickListener(playOnClickListener);
        } else {
            buttonPlay.setVisibility(View.GONE);
        }
    }

    private void populateBittorrentDownloadItem(View view, TransferItem item) {
        ImageView icon = findView(view, R.id.view_transfer_item_list_item_icon);
        TextView title = findView(view, R.id.view_transfer_item_list_item_title);
        ProgressBar progress = findView(view, R.id.view_transfer_item_list_item_progress);
        TextView size = findView(view, R.id.view_transfer_item_list_item_size);
        ImageButton buttonPlay = findView(view, R.id.view_transfer_item_list_item_button_play);

        icon.setImageResource(MediaType.getFileTypeIconId(FilenameUtils.getExtension(item.getFile().getAbsolutePath())));
        title.setText(item.getDisplayName());
        setProgress(progress, item.getProgress());
        size.setText(UIUtils.getBytesInHuman(item.getSize()));

        buttonPlay.setTag(item);
        updatePlayButtonVisibility(item, buttonPlay);
        buttonPlay.setOnClickListener(playOnClickListener);
    }

    private void updatePlayButtonVisibility(TransferItem item, ImageButton buttonPlay) {
        if (item.isComplete()) {
            buttonPlay.setVisibility(View.VISIBLE);
        } else {
            if (item instanceof BTDownloadItem) {
                buttonPlay.setVisibility(previewFile((BTDownloadItem) item) != null ? View.VISIBLE : View.GONE);
            } else {
                buttonPlay.setVisibility(View.GONE);
            }
        }
    }

    private void populateCloudDownload(View view, Transfer download) {
        TextView title = findView(view, R.id.view_transfer_list_item_title);
        ProgressBar progress = findView(view, R.id.view_transfer_list_item_progress);
        TextView status = findView(view, R.id.view_transfer_list_item_status);
        TextView speed = findView(view, R.id.view_transfer_list_item_speed);
        TextView size = findView(view, R.id.view_transfer_list_item_size);
        TextView seeds = findView(view, R.id.view_transfer_list_item_seeds);
        TextView peers = findView(view, R.id.view_transfer_list_item_peers);
        ImageButton buttonPlay = findView(view, R.id.view_transfer_list_item_button_play);

        seeds.setText("");
        peers.setText("");
        title.setText(download.getDisplayName());
        title.setCompoundDrawables(null, null, null, null);
        setProgress(progress, download.getProgress());
        status.setText(TRANSFER_STATE_STRING_MAP.get(download.getState()));
        speed.setText(UIUtils.getBytesInHuman(download.getDownloadSpeed()) + "/s");
        size.setText(UIUtils.getBytesInHuman(download.getSize()));

        File previewFile = download.previewFile();
        if (previewFile != null) {
            buttonPlay.setTag(previewFile);
            buttonPlay.setVisibility(View.VISIBLE);
            buttonPlay.setOnClickListener(playOnClickListener);
        } else {
            buttonPlay.setVisibility(View.GONE);
        }

        // hack to fill the demuxing state
        if (download instanceof YouTubeDownload) {
            YouTubeDownload yt = (YouTubeDownload) download;
            if (yt.getState() == TransferState.DEMUXING) {
                status.setText(TRANSFER_STATE_STRING_MAP.get(download.getState()) + " (" + yt.demuxingProgress() + "%)");
            }
        }
    }

    private boolean showTransferItemMenu(View v) {
        try {
            MenuAdapter adapter = getMenuAdapter(v);
            if (adapter != null) {
                trackDialog(new MenuBuilder(adapter).show());
                return true;
            }
        } catch (Throwable e) {
            LOG.error("Failed to create the menu", e);
        }
        return false;
    }

    // at least one phone does not provide this trivial optimization
    // TODO: move this for a more framework like place, like a Views (utils) class
    private static void setProgress(ProgressBar v, int progress) {
        int old = v.getProgress();
        if (old != progress) {
            v.setProgress(progress);
        }
    }

    private final class ViewOnClickListener implements OnClickListener {
        public void onClick(View v) {
            showTransferItemMenu(v);
        }
    }

    private final class ViewOnLongClickListener implements OnLongClickListener {
        public boolean onLongClick(View v) {
            return showTransferItemMenu(v);
        }
    }

    private static final class OpenOnClickListener extends ClickAdapter<Context> {

        public OpenOnClickListener(Context ctx) {
            super(ctx);
        }

        public void onClick(Context ctx, View v) {
            Engine.instance().getVibrator().hapticFeedback();
            Object tag = v.getTag();
            if (tag instanceof TransferItem) {
                TransferItem item = (TransferItem) tag;

                File path = item.isComplete() ? item.getFile() : null;

                if (path == null && item instanceof BTDownloadItem) {
                    path = previewFile((BTDownloadItem) item);
                }

                if (path != null) {
                    if (path.exists()) {
                        UIUtils.openFile(ctx, path);
                    } else {
                        UIUtils.showShortMessage(ctx, R.string.cant_open_file_does_not_exist, path.getName());
                    }
                }
            } else if (tag instanceof File) {
                File path = (File) tag;

                if (path.exists()) {
                    UIUtils.openFile(ctx, path);
                } else {
                    UIUtils.showShortMessage(ctx, R.string.cant_open_file_does_not_exist, path.getName());
                }
            }
        }
    }

    private static final class GroupIndicatorClickAdapter extends ClickAdapter<ExpandableListView> {
        private final int groupPosition;

        public GroupIndicatorClickAdapter(ExpandableListView owner, int groupPosition) {
            super(owner);
            this.groupPosition = groupPosition;
        }

        @Override
        public void onClick(ExpandableListView owner, View v) {
            if (owner.isGroupExpanded(groupPosition)) {
                owner.collapseGroup(groupPosition);
            } else {
                owner.expandGroup(groupPosition);
            }
        }
    }

    private static File previewFile(BTDownloadItem item) {
        if (item != null) {
            long downloaded = item.getSequentialDownloaded();
            long size = item.getSize();

            //LOG.debug("Downloaded: " + downloaded + ", seq: " + dl.isSequentialDownload());

            if (size > 0) {

                long percent = (100 * downloaded) / size;

                if (percent > 30 || downloaded > 10 * 1024 * 1024) {
                    return item.getFile();
                } else {
                    return null;
                }
            }
        }

        return null;
    }
}
