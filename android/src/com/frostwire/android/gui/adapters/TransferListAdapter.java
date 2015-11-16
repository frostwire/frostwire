/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.*;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.adapters.menu.*;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.*;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.android.gui.views.MenuBuilder;
import com.frostwire.bittorrent.BTDownloadItem;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.logging.Logger;
import com.frostwire.search.WebSearchPerformer;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.*;

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
     * Keep track of all dialogs ever opened so we dismiss when we leave to avoid memleaks
     */
    private final List<Dialog> dialogs;
    private List<Transfer> list;
    private final Map<String, String> TRANSFER_STATE_STRING_MAP = new Hashtable<>();

    public TransferListAdapter(Context context, List<Transfer> list) {
        this.context = new WeakReference<>(context);
        this.viewOnClickListener = new ViewOnClickListener();
        this.viewOnLongClickListener = new ViewOnLongClickListener();
        this.playOnClickListener = new OpenOnClickListener(context);
        this.dialogs = new ArrayList<>();
        this.list = list.equals(Collections.emptyList()) ? new ArrayList<Transfer>() : list;
        initTransferStateStringMap();
    }

    private void initTransferStateStringMap() {
        Context c = context.get();
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.QUEUED_FOR_CHECKING), c.getString(R.string.queued_for_checking));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.CHECKING), c.getString(R.string.checking_ellipsis));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.DOWNLOADING_METADATA), c.getString(R.string.downloading_metadata));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.DOWNLOADING_TORRENT), c.getString(R.string.torrent_fetcher_download_status_downloading_torrent));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.DOWNLOADING), c.getString(R.string.azureus_manager_item_downloading));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.FINISHED), c.getString(R.string.azureus_peer_manager_status_finished));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.SEEDING), c.getString(R.string.azureus_manager_item_seeding));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.ALLOCATING), c.getString(R.string.azureus_manager_item_allocating));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.PAUSED), c.getString(R.string.azureus_manager_item_paused));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.ERROR), c.getString(R.string.azureus_manager_item_error));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.ERROR_MOVING_INCOMPLETE), c.getString(R.string.error_moving_incomplete));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.ERROR_HASH_MD5), c.getString(R.string.error_wrong_md5_hash));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.ERROR_SIGNATURE), c.getString(R.string.error_wrong_signature));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.ERROR_NOT_ENOUGH_PEERS), c.getString(R.string.error_not_enough_peers));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.STOPPED), c.getString(R.string.azureus_manager_item_stopped));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.PAUSING), c.getString(R.string.pausing));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.CANCELING), c.getString(R.string.canceling));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.CANCELED), c.getString(R.string.torrent_fetcher_download_status_canceled));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.WAITING), c.getString(R.string.waiting));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.COMPLETE), c.getString(R.string.peer_http_download_status_complete));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.UPLOADING), c.getString(R.string.peer_http_upload_status_uploading));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.UNCOMPRESSING), c.getString(R.string.http_download_status_uncompressing));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.DEMUXING), c.getString(R.string.transfer_status_demuxing));
        TRANSFER_STATE_STRING_MAP.put(String.valueOf(TransferState.ERROR_DISK_FULL), c.getString(R.string.error_no_space_left_on_device));
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
        return list.get(groupPosition);
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
        } else if (transfer instanceof HttpDownload) {
            populateHttpDownload(view, (HttpDownload) transfer);
        } else if (transfer instanceof YouTubeDownload) {
            populateYouTubeDownload(view, (YouTubeDownload) transfer);
        } else if (transfer instanceof SoundcloudDownload) {
            populateSoundcloudDownload(view, (SoundcloudDownload) transfer);
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
            BittorrentDownload download = (BittorrentDownload) tag;
            title = download.getDisplayName();

            //If it's a torrent download with a single file, we should be able to open it.
            if (download.isComplete()) {
                TransferItem transferItem = download.getItems().get(0);
                String path = transferItem.getFile().getAbsolutePath();
                String mimeType = UIUtils.getMimeType(path);
                items.add(new OpenMenuAction(context.get(), path, mimeType));
            }

            if (!download.isComplete() || ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS)) {

                if (download.isPausable() && !download.isPaused()) {
                    items.add(new PauseDownloadMenuAction(context.get(), download));
                } else if (download.isResumable()) {
                    boolean wifiIsUp = NetworkManager.instance().isDataWIFIUp();
                    boolean bittorrentOnMobileData = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA);
                    boolean bittorrentOff = Engine.instance().isStopped() || Engine.instance().isDisconnected();

                    if (wifiIsUp || bittorrentOnMobileData) {
                        if (!download.isComplete() || bittorrentOff) {
                            items.add(new ResumeDownloadMenuAction(context.get(), download, R.string.resume_torrent_menu_action));
                        } else {
                            //let's see if we can seed...
                            boolean seedTorrents = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS);
                            boolean seedTorrentsOnWifiOnly = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY);
                            if ((seedTorrents && seedTorrentsOnWifiOnly && wifiIsUp) || (seedTorrents && !seedTorrentsOnWifiOnly)) {
                                items.add(new ResumeDownloadMenuAction(context.get(), download, R.string.seed));
                            }
                        }
                    }
                }
            }

            items.add(new CancelMenuAction(context.get(), download, !download.isComplete()));

            items.add(new CopyToClipboardMenuAction(context.get(),
                    R.drawable.contextmenu_icon_magnet,
                    R.string.transfers_context_menu_copy_magnet,
                    R.string.transfers_context_menu_copy_magnet_copied,
                    download.makeMagnetUri()
            ));

            items.add(new CopyToClipboardMenuAction(context.get(),
                    R.drawable.contextmenu_icon_copy,
                    R.string.transfers_context_menu_copy_infohash,
                    R.string.transfers_context_menu_copy_infohash_copied,
                    download.getHash()
            ));

            if (download.isComplete()) {
                // Remove Torrent and Data action.
                items.add(new CancelMenuAction(context.get(), download, true, true));
            }

            if (download.hasPaymentOptions()) {
                PaymentOptions po = download.getPaymentOptions();
                if (po.bitcoin != null) {
                    items.add(new SendBitcoinTipAction(context.get(), po));
                }

                if (po.paypalUrl != null) {
                    items.add(new SendFiatTipAction(context.get(), po));
                }
            }
        } else if (tag instanceof DownloadTransfer) {
            DownloadTransfer download = (DownloadTransfer) tag;
            title = download.getDisplayName();

            boolean errored = download.getStatus() != null && getStatusFromResId(download.getStatus()).contains("Error");

            boolean openMenu = !errored && download.isComplete() && (tag instanceof HttpDownload || tag instanceof YouTubeDownload || tag instanceof SoundcloudDownload);
            if (openMenu) {
                items.add(new OpenMenuAction(context.get(), download.getDisplayName(), download.getSavePath().getAbsolutePath(), extractMime(download)));
            }

            items.add(new CancelMenuAction(context.get(), download, !openMenu));

        }

        return items.size() > 0 ? new MenuAdapter(context.get(), title, items) : null;
    }

    private String extractMime(DownloadTransfer download) {
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
        final int totalItems = item.getItems().size();
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
            } else if (item instanceof DownloadTransfer) {
                DownloadTransfer transferItem = (DownloadTransfer) item;
                if (transferItem.getSavePath() != null) {
                    path = transferItem.getSavePath().getAbsolutePath();
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
                    groupIndicator.setImageResource(R.drawable.browse_peer_application_icon_selector_menu);
                } catch (Throwable e) {
                    groupIndicator.setImageResource(R.drawable.browse_peer_application_icon_selector_menu);
                }
            } else {
                groupIndicator.setImageResource(getFileTypeIconId(extension));
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

        seeds.setText(context.get().getString(R.string.seeds_n, download.getSeeds()));
        peers.setText(context.get().getString(R.string.peers_n, download.getPeers()));

        title.setText(download.getDisplayName());
        progress.setProgress(download.getProgress());
        title.setCompoundDrawables(null, null, null, null);

        status.setText(TRANSFER_STATE_STRING_MAP.get(download.getStatus()));

        speed.setText(UIUtils.getBytesInHuman(download.getDownloadSpeed()) + "/s");
        size.setText(UIUtils.getBytesInHuman(download.getSize()));

        if (download.hasPaymentOptions()) {
            setPaymentOptionDrawable(download, title);
        }

        List<TransferItem> items = download.getItems();
        if (items != null && items.size() == 1) {
            TransferItem item = items.get(0);
            buttonPlay.setTag(item);
            if (item.isComplete()) {
                buttonPlay.setVisibility(View.VISIBLE);
            } else {
                if (item instanceof BTDownloadItem) {
                    buttonPlay.setVisibility(previewFile((BTDownloadItem) item) != null ? View.VISIBLE : View.GONE);
                } else {
                    buttonPlay.setVisibility(View.GONE);
                }
            }
            buttonPlay.setOnClickListener(playOnClickListener);
        } else {
            buttonPlay.setVisibility(View.GONE);
        }
    }

    private void setPaymentOptionDrawable(BittorrentDownload download, TextView title) {
        final PaymentOptions paymentOptions = download.getPaymentOptions();
        final Resources r = context.get().getResources();
        Drawable tipDrawable = (paymentOptions.bitcoin != null) ? r.getDrawable(R.drawable.contextmenu_icon_donation_bitcoin) : r.getDrawable(R.drawable.contextmenu_icon_donation_fiat);
        if (tipDrawable  != null) {
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
        progress.setProgress(download.getProgress());
        status.setText(getStatusFromResId(download.getStatus()));
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

        icon.setImageResource(getFileTypeIconId(FilenameUtils.getExtension(item.getFile().getAbsolutePath())));
        title.setText(item.getDisplayName());
        progress.setProgress(item.getProgress());
        size.setText(UIUtils.getBytesInHuman(item.getSize()));

        buttonPlay.setTag(item);
        if (item.isComplete()) {
            buttonPlay.setVisibility(View.VISIBLE);
        } else {
            if (item instanceof BTDownloadItem) {
                buttonPlay.setVisibility(previewFile((BTDownloadItem) item) != null ? View.VISIBLE : View.GONE);
            } else {
                buttonPlay.setVisibility(View.GONE);
            }
        }
        buttonPlay.setOnClickListener(playOnClickListener);
    }

    private void populateYouTubeDownload(View view, YouTubeDownload download) {
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
        progress.setProgress(download.getProgress());
        status.setText(getStatusFromResId(download.getStatus()));
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
    }

    private void populateSoundcloudDownload(View view, SoundcloudDownload download) {
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
        progress.setProgress(download.getProgress());
        status.setText(getStatusFromResId(download.getStatus()));
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
    }

    private String getStatusFromResId(String str) {
        String s = "";
        try {
            s = context.get().getString(Integer.parseInt(str));
        } catch (Throwable e) {
            // ignore
        }
        return s;
    }

    private static int getFileTypeIconId(String ext) {
        MediaType mt = MediaType.getMediaTypeForExtension(ext);
        if (mt == null) {
            return R.drawable.question_mark;
        }
        if (mt.equals(MediaType.getApplicationsMediaType())) {
            return R.drawable.browse_peer_application_icon_selector_menu;
        } else if (mt.equals(MediaType.getAudioMediaType())) {
            return R.drawable.browse_peer_audio_icon_selector_menu;
        } else if (mt.equals(MediaType.getDocumentMediaType())) {
            return R.drawable.browse_peer_document_icon_selector_menu;
        } else if (mt.equals(MediaType.getImageMediaType())) {
            return R.drawable.browse_peer_picture_icon_selector_menu;
        } else if (mt.equals(MediaType.getVideoMediaType())) {
            return R.drawable.browse_peer_video_icon_selector_menu;
        } else if (mt.equals(MediaType.getTorrentMediaType())) {
            return R.drawable.browse_peer_torrent_icon_selector_menu;
        } else {
            return R.drawable.question_mark;
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
                System.out.println(path);

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
