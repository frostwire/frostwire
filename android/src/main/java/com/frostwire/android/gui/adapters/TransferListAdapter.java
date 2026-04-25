/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.frostwire.concurrent.concurrent.ExecutorsHelper;

import com.frostwire.android.AndroidPaths;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.adapters.menu.CancelMenuAction;
import com.frostwire.android.gui.adapters.menu.CopyToClipboardMenuAction;
import com.frostwire.android.gui.adapters.menu.OpenMenuAction;
import com.frostwire.android.gui.adapters.menu.PauseDownloadMenuAction;
import com.frostwire.android.gui.adapters.menu.ResumeDownloadMenuAction;
import com.frostwire.android.gui.adapters.menu.RetryDownloadAction;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.adapters.menu.SendBitcoinTipAction;
import com.frostwire.android.gui.adapters.menu.SendFiatTipAction;
import com.frostwire.android.gui.adapters.menu.StopSeedingAction;
import com.frostwire.android.gui.adapters.menu.TransferDetailsMenuAction;
import com.frostwire.android.gui.transfers.InvalidTransfer;
import com.frostwire.android.gui.transfers.TorrentFetcherDownload;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.TransferStateStrings;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.android.gui.views.MenuBuilder;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownloadItem;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.search.StreamableUtils;
import com.frostwire.android.gui.transfers.UISoundcloudDownload;
import com.frostwire.transfers.BaseHttpDownload;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.SoundcloudDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import com.frostwire.transfers.TransferStateListener;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public class TransferListAdapter extends ListAdapter<Transfer, TransferListAdapter.ViewHolder> implements TransferStateListener {
    private static final Logger LOG = Logger.getLogger(TransferListAdapter.class);

    private final WeakReference<Context> contextRef;
    private final ViewOnClickListener viewOnClickListener;
    private final ViewOnLongClickListener viewOnLongClickListener;
    private final OpenOnClickListener openOnClickListener;
    private final TransferDetailsClickListener transferDetailsClickListener;

    /**
     * Keep track of all dialogs ever opened so we dismiss when we leave to avoid memory leaks
     */
    private final List<Dialog> dialogs;

    /**
     * Track IDs of removed transfers to prevent them from being re-added by submitList().
     * Unlike the old currentList approach, removedTransferIds is only used for filtering
     * during updateList() — ListAdapter is now the single source of truth for the list.
     */
    private final Set<String> removedTransferIds = Collections.synchronizedSet(new HashSet<>());

    public TransferListAdapter(Context context, List<Transfer> list) {
        super(new TransferItemCallback());
        this.contextRef = new WeakReference<>(context);
        this.dialogs = new ArrayList<>();
        viewOnClickListener = new ViewOnClickListener();
        viewOnLongClickListener = new ViewOnLongClickListener();
        openOnClickListener = new OpenOnClickListener(context);
        transferDetailsClickListener = new TransferDetailsClickListener(context);
        submitList(list == null ? Collections.emptyList() : new ArrayList<>(list));
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int i) {
        LinearLayout convertView =
                (LinearLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_list_item, parent, false);
        Context context = Ref.alive(contextRef) ? contextRef.get() : parent.getContext();
        return new ViewHolder(this,
                context,
                convertView,
                viewOnClickListener,
                viewOnLongClickListener,
                openOnClickListener,
                transferDetailsClickListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        Transfer item = getItem(i);
        if (item == null) {
            return;
        }
        // Subscribe to transfer state changes for event-driven updates
        subscribeToTransfer(item);
        viewHolder.updateView(item);
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        super.onViewRecycled(holder);
        // Note: We don't unsubscribe here because the same Transfer object might be bound to another ViewHolder
        // Listeners are cleaned up in updateList() when transfers are removed or when the adapter is destroyed
    }

    private void subscribeToTransfer(Transfer transfer) {
        if (transfer instanceof BaseHttpDownload) {
            ((BaseHttpDownload) transfer).addListener(this);
        } else if (transfer instanceof UIBittorrentDownload) {
            ((UIBittorrentDownload) transfer).addListener(this);
        }
    }

    private void unsubscribeFromTransfer(Transfer transfer) {
        if (transfer instanceof BaseHttpDownload) {
            ((BaseHttpDownload) transfer).removeListener(this);
        } else if (transfer instanceof UIBittorrentDownload) {
            ((UIBittorrentDownload) transfer).removeListener(this);
        }
    }

    @Override
    public void onTransferStateChanged(Transfer transfer, TransferState oldState, TransferState newState) {
        // Called on background thread - post to UI thread
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            int position = getPositionOfTransfer(transfer);
            if (position >= 0) {
                LOG.debug("onTransferStateChanged: " + transfer.getDisplayName() + " " + oldState + " -> " + newState + " at position " + position);
                notifyItemChanged(position);
            }
        });
    }

    @Override
    public void onTransferProgressChanged(Transfer transfer, int progress) {
        // Called frequently - only update if progress changed significantly (every 5%)
        // to avoid excessive updates
        // Not implemented yet - keeping submitList(null) for progress updates for now
    }

    private int getPositionOfTransfer(Transfer transfer) {
        List<Transfer> currentList = getCurrentList();
        String transferId = resolveTransferId(transfer);
        for (int i = 0; i < currentList.size(); i++) {
            if (resolveTransferId(currentList.get(i)).equals(transferId)) {
                return i;
            }
        }
        return -1;
    }

    public List<Transfer> getList() {
        return new ArrayList<>(getCurrentList());
    }

    public void updateList(List<Transfer> newList) {
        if (newList == null) {
            newList = Collections.emptyList();
        }
        newList = new ArrayList<>(newList);
        if (!removedTransferIds.isEmpty()) {
            newList.removeIf(t -> removedTransferIds.contains(resolveTransferId(t)));
        }
        LOG.debug("updateList() called with " + newList.size() + " transfers");
        
        // Unsubscribe from old transfers that are no longer in the list
        List<Transfer> oldList = getCurrentList();
        Set<String> newIds = new HashSet<>();
        for (Transfer t : newList) {
            newIds.add(resolveTransferId(t));
        }
        for (Transfer oldTransfer : oldList) {
            if (!newIds.contains(resolveTransferId(oldTransfer))) {
                unsubscribeFromTransfer(oldTransfer);
            }
        }
        
        submitList(newList, this::notifyDataSetChanged);
        LOG.debug("updateList() submitList called");
    }

    /**
     * DiffUtil.ItemCallback for Transfer objects.
     * ListAdapter uses this to compute diffs on a background thread and dispatch
     * granular updates to RecyclerView on the main thread — no manual notifyXxx() needed.
     */
    private static class TransferItemCallback extends DiffUtil.ItemCallback<Transfer> {
        @Override
        public boolean areItemsTheSame(Transfer oldItem, Transfer newItem) {
            return resolveTransferId(oldItem).equals(resolveTransferId(newItem));
        }

        @Override
        public boolean areContentsTheSame(Transfer oldItem, Transfer newItem) {
            TransferUiState oldState = new TransferUiState(oldItem);
            TransferUiState newState = new TransferUiState(newItem);
            boolean same = oldState.hasSameContent(newState);
            if (!same) {
                LOG.debug("areContentsTheSame: CHANGED for " + oldItem.getDisplayName() +
                    " oldState=(" + oldState.state + ", progress=" + oldState.progress + ", speed=" + oldState.downloadSpeed + ")" +
                    " newState=(" + newState.state + ", progress=" + newState.progress + ", speed=" + newState.downloadSpeed + ")");
            }
            return same;
        }
    }

    private static String resolveTransferId(Transfer transfer) {
        String id;
        if (transfer instanceof BittorrentDownload) {
            String hash = ((BittorrentDownload) transfer).getInfoHash();
            if (hash != null && !hash.isEmpty()) {
                id = "bt:" + hash;
                LOG.debug("resolveTransferId() BT: " + id + " for " + transfer.getDisplayName());
                return id;
            }
        }
        File savePath = transfer.getSavePath();
        if (savePath != null) {
            id = "path:" + savePath.getAbsolutePath();
            LOG.debug("resolveTransferId() PATH: " + id + " for " + transfer.getDisplayName());
            return id;
        }
        String displayName = transfer.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            id = "name:" + displayName;
            LOG.debug("resolveTransferId() NAME: " + id + " for " + displayName);
            return id;
        }
        id = "instance:" + System.identityHashCode(transfer);
        LOG.debug("resolveTransferId() INSTANCE: " + id + " for " + displayName);
        return id;
    }

    private static final class TransferUiState {
        private final String id;
        private final TransferState state;
        private final int progress;
        private final long downloadSpeed;
        private final int connectedSeeds;
        private final int totalSeeds;
        private final int connectedPeers;
        private final int totalPeers;

        private TransferUiState(Transfer transfer) {
            this.id = resolveTransferId(transfer);
            this.state = transfer.getState();
            this.progress = transfer.getProgress();
            this.downloadSpeed = transfer.getDownloadSpeed();
            if (transfer instanceof BittorrentDownload) {
                BittorrentDownload bt = (BittorrentDownload) transfer;
                this.connectedSeeds = bt.getConnectedSeeds();
                this.totalSeeds = bt.getTotalSeeds();
                this.connectedPeers = bt.getConnectedPeers();
                this.totalPeers = bt.getTotalPeers();
            } else {
                this.connectedSeeds = -1;
                this.totalSeeds = -1;
                this.connectedPeers = -1;
                this.totalPeers = -1;
            }
        }

        private boolean hasSameContent(TransferUiState other) {
            if (other == null) {
                return false;
            }
            return state == other.state &&
                    progress == other.progress &&
                    downloadSpeed == other.downloadSpeed &&
                    connectedSeeds == other.connectedSeeds &&
                    totalSeeds == other.totalSeeds &&
                    connectedPeers == other.connectedPeers &&
                    totalPeers == other.totalPeers;
        }
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

    public void cleanup() {
        // Unsubscribe from all transfers when adapter is destroyed
        List<Transfer> currentList = getCurrentList();
        for (Transfer transfer : currentList) {
            unsubscribeFromTransfer(transfer);
        }
        dismissDialogs();
    }

    private MenuAdapter getMenuAdapter(View view) {
        // Store context once to avoid repeated dereferencing
        if (!Ref.alive(contextRef)) {
            return null;
        }
        Context context = contextRef.get();

        Object tag = view.getTag();
        String title = "";
        List<MenuAction> items = new ArrayList<>();
        if (tag instanceof Transfer && ((Transfer) tag).getState().name().contains("ERROR")) {
            if (tag instanceof InvalidTransfer || tag instanceof TorrentFetcherDownload) {
                items.add(new RetryDownloadAction(context, (Transfer) tag));
            }
        }
        if (tag instanceof BittorrentDownload) {
            title = populateBittorrentDownloadMenuActions(context, (BittorrentDownload) tag, items);
        } else if (tag instanceof Transfer) {
            title = populateCloudDownloadMenuActions(context, tag, items);
        }
        return items.size() > 0 ? new MenuAdapter(context, title, items) : null;
    }

    private String populateCloudDownloadMenuActions(Context context, Object tag, List<MenuAction> items) {
        Transfer download = (Transfer) tag;
        String title = download.getDisplayName();
        boolean errored = download.getState().name().contains("ERROR");
        boolean finishedSuccessfully = !errored && download.isComplete() && isCloudDownload(tag);
        if (finishedSuccessfully) {
            File savePath = download.getSavePath();
            if (!AndroidPlatform.saf(savePath)) {
                items.add(new SeedAction(context));
            }
            String mime = extractMime(download);
            items.add(new OpenMenuAction(context, download.getDisplayName(), savePath.getAbsolutePath(), mime));
        }
        items.add(new CancelMenuAction(context, download, !finishedSuccessfully));
        return title;
    }

    private boolean isCloudDownload(Object tag) {
        return tag instanceof HttpDownload;
    }

    private String populateBittorrentDownloadMenuActions(Context context, BittorrentDownload bittorrentDownload, List<MenuAction> items) {
        String title;
        title = bittorrentDownload.getDisplayName();
        //If it's a torrent download with a single file, we should be able to open it.
        List<TransferItem> cachedItems = bittorrentDownload instanceof UIBittorrentDownload ?
                ((UIBittorrentDownload) bittorrentDownload).peekItems() : bittorrentDownload.getItems();
        if (bittorrentDownload.isComplete() && cachedItems != null && cachedItems.size() > 0) {
            TransferItem transferItem = cachedItems.get(0);
            String path = transferItem.getFile().getAbsolutePath();
            String mimeType = UIUtils.getMimeType(path);
            items.add(new OpenMenuAction(context, path, mimeType));
        }
        if (!bittorrentDownload.isComplete() && !bittorrentDownload.isSeeding()) {
            if (!bittorrentDownload.isPaused()) {
                items.add(new PauseDownloadMenuAction(context, bittorrentDownload));
            } else {
                boolean wifiIsUp = NetworkManager.instance().isDataWIFIUp();
                boolean bittorrentOnMobileData = !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
                if (wifiIsUp || bittorrentOnMobileData) {
                    if (!bittorrentDownload.isComplete()) {
                        items.add(new ResumeDownloadMenuAction(context, bittorrentDownload, R.string.resume_torrent_menu_action));
                    }
                }
            }
        }
        if (bittorrentDownload.getState() == TransferState.FINISHED) {
            items.add(new SeedAction(context, bittorrentDownload));
        }
        if (bittorrentDownload.getState() == TransferState.SEEDING) {
            items.add(new StopSeedingAction(context, bittorrentDownload));
        }
        items.add(new CancelMenuAction(context, bittorrentDownload, !bittorrentDownload.isComplete()));
        String magnetUri = bittorrentDownload instanceof UIBittorrentDownload ?
                ((UIBittorrentDownload) bittorrentDownload).getCachedMagnetUri() : bittorrentDownload.magnetUri();
        if (magnetUri != null && !"".equals(magnetUri)) {
            items.add(new CopyToClipboardMenuAction(context,
                    R.drawable.contextmenu_icon_magnet,
                    R.string.transfers_context_menu_copy_magnet,
                    R.string.transfers_context_menu_copy_magnet_copied,
                    magnetUri
            ));
        }
        items.add(new CopyToClipboardMenuAction(context,
                R.drawable.contextmenu_icon_copy,
                R.string.transfers_context_menu_copy_infohash,
                R.string.transfers_context_menu_copy_infohash_copied,
                bittorrentDownload.getInfoHash()
        ));
        if (bittorrentDownload.isComplete()) {
            // Remove Torrent and Data action.
            items.add(new CancelMenuAction(context, bittorrentDownload, true, true));
        }
        if (bittorrentDownload instanceof UIBittorrentDownload) {
            UIBittorrentDownload uidl = (UIBittorrentDownload) bittorrentDownload;
            if (uidl.hasPaymentOptions()) {
                PaymentOptions po = uidl.getPaymentOptions();
                if (po.bitcoin != null) {
                    items.add(new SendBitcoinTipAction(context, po.bitcoin));
                }
                if (po.paypalUrl != null) {
                    items.add(new SendFiatTipAction(context, po.paypalUrl));
                }
                if (po.bitcoin != null) {
                    items.add(new SendBitcoinTipAction(context, po.bitcoin));
                }
            }
            if (bittorrentDownload.getInfoHash() != null && !"".equals(bittorrentDownload.getInfoHash())) {
                items.add(new TransferDetailsMenuAction(context, R.string.show_torrent_details, bittorrentDownload.getInfoHash()));
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

    public static final class ViewHolder extends RecyclerView.ViewHolder {
        private WeakReference<Resources> resourcesRef;
        private WeakReference<TransferListAdapter> adapterRef;
        private OnClickListener viewOnClickListener;
        private ViewOnLongClickListener viewOnLongClickListener;
        private OpenOnClickListener playOnClickListener;
        private TransferDetailsClickListener transferDetailsClickListener;
        private final TransferStateStrings transferStateStrings;

        // Performance: Cache formatted strings to avoid repeated allocations
        private String cachedSpeedString = "";
        private long lastSpeedValue = -1;
        private String cachedSizeString = "";
        private long lastSizeValue = -1;

        private TextView title;
        private ProgressBar progress;
        private TextView status;
        private TextView speed;
        private TextView size;
        private TextView seeds;
        private TextView peers;
        private ImageButton buttonPlay;
        private ImageButton buttonDetails;
        private ImageView fileTypeIndicatorImageView;

        public ViewHolder(TransferListAdapter adapter,
                          Context context,
                          View itemView,
                          ViewOnClickListener viewOnClickListener,
                          ViewOnLongClickListener viewOnLongClickListener,
                          OpenOnClickListener openOnClickListener,
                          TransferDetailsClickListener transferDetailsClickListener) {
            super(itemView);
            this.resourcesRef = Ref.weak(context.getResources());
            this.adapterRef = Ref.weak(adapter);
            this.viewOnClickListener = viewOnClickListener;
            this.viewOnLongClickListener = viewOnLongClickListener;
            this.playOnClickListener = openOnClickListener;
            this.transferDetailsClickListener = transferDetailsClickListener;
            this.transferStateStrings = TransferStateStrings.getInstance(context);
        }

        public void updateView(Transfer transfer) {
            if (transfer == null) {
                return;
            }
            LinearLayout listItemLinearLayoutHolder = (LinearLayout) itemView;
            listItemLinearLayoutHolder.setOnClickListener(viewOnClickListener);
            listItemLinearLayoutHolder.setOnLongClickListener(viewOnLongClickListener);
            listItemLinearLayoutHolder.setClickable(true);
            listItemLinearLayoutHolder.setLongClickable(true);
            listItemLinearLayoutHolder.setTag(transfer);
            try {
                if (transfer instanceof BittorrentDownload) {
                    populateBittorrentDownload(listItemLinearLayoutHolder, (BittorrentDownload) transfer);
                } else if (transfer instanceof SoundcloudDownload) {
                    populateCloudDownload(listItemLinearLayoutHolder, transfer);
                } else if (transfer instanceof HttpDownload) {
                    populateHttpDownload(listItemLinearLayoutHolder, (HttpDownload) transfer);
                }
            } catch (Throwable e) {
                LOG.error("Not able to populate group view in expandable list:" + e.getMessage());
            }
        }

        /**
         * Performance optimization: Get cached speed string or format new one if speed changed.
         * Avoids repeated String.format() and UIUtils.getBytesInHuman() calls when speed unchanged.
         */
        private String getSpeedString(long speedBytes) {
            if (speedBytes != lastSpeedValue) {
                lastSpeedValue = speedBytes;
                cachedSpeedString = UIUtils.getBytesInHuman(speedBytes) + "/s";
            }
            return cachedSpeedString;
        }

        /**
         * Performance optimization: Get cached size string or format new one if size changed.
         * Avoids repeated UIUtils.getBytesInHuman() calls when size unchanged.
         */
        private String getSizeString(double sizeBytes) {
            long sizeLong = (long) sizeBytes;
            if (sizeLong != lastSizeValue) {
                lastSizeValue = sizeLong;
                cachedSizeString = UIUtils.getBytesInHuman(sizeLong);
            }
            return cachedSizeString;
        }

        private void ensureComponentsReferenced(View view) {
            if (title == null) {
                title = view.findViewById(R.id.view_transfer_list_item_title);
            }
            if (progress == null) {
                progress = view.findViewById(R.id.view_transfer_list_item_progress);
            }
            if (status == null) {
                status = view.findViewById(R.id.view_transfer_list_item_status);
            }
            if (speed == null) {
                speed = view.findViewById(R.id.view_transfer_list_item_speed);
            }
            if (size == null) {
                size = view.findViewById(R.id.view_transfer_list_item_size);
            }
            if (seeds == null) {
                seeds = view.findViewById(R.id.view_transfer_list_item_seeds);
            }
            if (peers == null) {
                peers = view.findViewById(R.id.view_transfer_list_item_peers);
            }
            if (buttonPlay == null) {
                buttonPlay = view.findViewById(R.id.view_transfer_list_item_button_play);
            }
            if (buttonDetails == null) {
                buttonDetails = view.findViewById(R.id.view_transfer_list_item_button_details);
            }
            if (fileTypeIndicatorImageView == null) {
                fileTypeIndicatorImageView = view.findViewById(R.id.view_transfer_list_item_download_type_indicator);
            }
        }

        private void populateHttpDownload(LinearLayout view, HttpDownload download) {
            ensureComponentsReferenced(view);
            seeds.setText("");
            peers.setText("");
            title.setText(download.getDisplayName());
            title.setCompoundDrawables(null, null, null, null);
            setProgress(progress, download.getProgress());
            String downloadStatus = transferStateStrings.get(download.getState());
            status.setText(downloadStatus);
            // Performance: Use cached string formatting
            speed.setText(getSpeedString(download.getDownloadSpeed()));
            size.setText(getSizeString(download.getSize()));
            buttonDetails.setVisibility(View.GONE);
            File previewFile = download.previewFile();
            if (previewFile != null && StreamableUtils.isStreamable(previewFile.getName())) {
                buttonPlay.setTag(previewFile);
                buttonPlay.setVisibility(View.VISIBLE);
                buttonPlay.setOnClickListener(playOnClickListener);
            } else {
                buttonPlay.setVisibility(View.GONE);
            }
            populateFileTypeIndicatorImageView(download);
        }

        private void populateBittorrentDownload(LinearLayout view, BittorrentDownload download) {
            ensureComponentsReferenced(view);
            Context context = view.getContext();
            seeds.setText(context.getString(R.string.seeds_n, formatSeeds(download)));
            peers.setText(context.getString(R.string.peers_n, formatPeers(download)));
            seeds.setVisibility(View.VISIBLE);
            peers.setVisibility(View.VISIBLE);
            title.setText(download.getDisplayName());
            setProgress(progress, download.getProgress());
            title.setCompoundDrawables(null, null, null, null);
            if (download instanceof UIBittorrentDownload && download.getInfoHash() != null && !"".equals(download.getInfoHash())) {
                buttonDetails.setTag(download);
                buttonDetails.setVisibility(View.VISIBLE);
                buttonDetails.setOnClickListener(transferDetailsClickListener);
            } else {
                buttonDetails.setVisibility(View.GONE);
                buttonDetails.setOnClickListener(null);
            }

            buttonPlay.setVisibility(View.GONE);
            buttonPlay.setOnClickListener(null);

            final String downloadStatus = transferStateStrings.get(download.getState());
            // Performance: Cache network status check result in NetworkManager to avoid repeated system calls
            if (!NetworkManager.instance().isInternetDataConnectionUp()) {
                // Only format this compound string when network is down (rare case)
                status.setText(downloadStatus + " (" + view.getResources().getText(R.string.check_internet_connection) + ")");
                seeds.setText("");
                peers.setText("");
            } else {
                status.setText(downloadStatus);
            }
            // Performance: Use cached string formatting
            speed.setText(getSpeedString(download.getDownloadSpeed()));
            size.setText(getSizeString(download.getSize()));
            if (download instanceof UIBittorrentDownload) {
                UIBittorrentDownload uidl = (UIBittorrentDownload) download;
                if (uidl.hasPaymentOptions()) {
                    setPaymentOptionDrawable(uidl, title);
                }
            }
            populateFileTypeIndicatorImageView(download);
        }

        private void populateCloudDownload(LinearLayout view, Transfer download) {
            ensureComponentsReferenced(view);
            seeds.setText("");
            peers.setText("");
            title.setText(download.getDisplayName());
            title.setCompoundDrawables(null, null, null, null);
            setProgress(progress, download.getProgress());
            status.setText(transferStateStrings.get(download.getState()));
            // Performance: Use cached string formatting
            speed.setText(getSpeedString(download.getDownloadSpeed()));
            size.setText(getSizeString(download.getSize()));
            buttonDetails.setVisibility(View.GONE);
            File previewFile = download.previewFile();
            if (previewFile != null) {
                buttonPlay.setTag(previewFile);
                buttonPlay.setVisibility(View.VISIBLE);
                buttonPlay.setOnClickListener(playOnClickListener);
            } else {
                buttonPlay.setVisibility(View.GONE);
            }
            populateFileTypeIndicatorImageView(download);
        }

        private void populateFileTypeIndicatorImageView(Transfer download) {
            File savePath = download.getSavePath();
            String ext = null;
            if (download instanceof BittorrentDownload) {
                ext = ((BittorrentDownload) download).getPredominantFileExtension();
            } else if (savePath != null) {
                ext = FilenameUtils.getExtension(savePath.getAbsolutePath());
            }
            fileTypeIndicatorImageView.setImageResource(MediaType.getFileTypeIconId(ext));
        }

        private void setPaymentOptionDrawable(UIBittorrentDownload download, TextView title) {
            // BUGFIX: Logic was inverted - should return when NOT alive, not when alive
            if (!Ref.alive(resourcesRef)) {
                return;
            }
            final PaymentOptions paymentOptions = download.getPaymentOptions();
            final Resources r = resourcesRef.get();
            Drawable tipDrawable = androidx.core.content.ContextCompat.getDrawable(itemView.getContext(),
                    paymentOptions.bitcoin != null ? R.drawable.contextmenu_icon_donation_bitcoin : R.drawable.contextmenu_icon_donation_fiat);
            if (tipDrawable != null) {
                final int iconHeightInPixels = r.getDimensionPixelSize(R.dimen.view_transfer_list_item_title_left_drawable);
                //noinspection SuspiciousNameCombination
                tipDrawable.setBounds(0, 0, iconHeightInPixels, iconHeightInPixels);
                title.setCompoundDrawables(tipDrawable, null, null, null);
            }
        }
    } // ViewHolder


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
            LOG.info("OpenOnClickListener.onClick() BEGIN");
            Object tag = v.getTag();
            if (tag instanceof TransferItem) {
                LOG.info("OpenOnClickListener.onClick() tag is TransferItem");
                TransferItem item = (TransferItem) tag;
                File path = item.isComplete() ? item.getFile() : null;
                LOG.info("OpenOnClickListener.onClick() isComplete=" + item.isComplete() + " path=" + (path != null ? path.getAbsolutePath() : "null"));
                if (path == null && item instanceof BTDownloadItem) {
                    path = previewFile((BTDownloadItem) item);
                    LOG.info("OpenOnClickListener.onClick() previewFile path=" + (path != null ? path.getAbsolutePath() : "null"));
                }
                if (path != null) {
                    // Use MusicUtils.playFile() for audio - same code path as My Music
                    // Avoids UIUtils.openFile() ephemeral playlist delays
                    if (UIUtils.isAudioFile(path.getAbsolutePath())) {
                        LOG.info("OpenOnClickListener.onClick() calling MusicUtils.playFileFromUserItemClick (audio): " + path.getAbsolutePath());
                        com.andrew.apollo.utils.MusicUtils.playFileFromUserItemClick(ctx, path);
                    } else {
                        LOG.info("OpenOnClickListener.onClick() calling UIUtils.openFile (non-audio): " + path.getAbsolutePath());
                        UIUtils.openFile(ctx, path);
                    }
                    LOG.info("OpenOnClickListener.onClick() play method returned");
                }
            } else if (tag instanceof File) {
                File file = (File) tag;
                LOG.info("OpenOnClickListener.onClick() tag is File: " + file.getAbsolutePath());
                // Use MusicUtils.playFile() for audio - same code path as My Music
                if (UIUtils.isAudioFile(file.getAbsolutePath())) {
                    LOG.info("OpenOnClickListener.onClick() calling MusicUtils.playFileFromUserItemClick (audio): " + file.getAbsolutePath());
                    com.andrew.apollo.utils.MusicUtils.playFileFromUserItemClick(ctx, file);
                } else {
                    LOG.info("OpenOnClickListener.onClick() calling UIUtils.openFile (non-audio): " + file.getAbsolutePath());
                    UIUtils.openFile(ctx, file);
                }
                LOG.info("OpenOnClickListener.onClick() play method returned");
            }
            LOG.info("OpenOnClickListener.onClick() END");
        }
    }

    private static final class TransferDetailsClickListener extends ClickAdapter<Context> {
        public TransferDetailsClickListener(Context owner) {
            super(owner);
        }

        @Override
        public void onClick(Context owner, View v) {
            Object transfer = v.getTag();
            if (transfer instanceof UIBittorrentDownload) {
                String infoHash = ((UIBittorrentDownload) transfer).getInfoHash();
                new TransferDetailsMenuAction(owner, R.string.show_torrent_details, infoHash).setClickedView(v).onClick();
            }
        }
    }

    private static File previewFile(BTDownloadItem item) {
        if (item != null) {
            long downloaded = item.getSequentialDownloaded();
            long size = item.getSize();
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

    /**
     * Removes a transfer item from the adapter.
     * Marks the transfer ID as removed to prevent it from being re-added by updateList()/submitList().
     * Uses submitList() to let ListAdapter compute the diff and dispatch granular notifications
     * (no more manual notifyItemRemoved() which caused IndexOutOfBoundsException crashes).
     */
    public void removeTransferItem(Transfer transfer) {
        if (transfer == null) {
            return;
        }
        String transferId = resolveTransferId(transfer);
        removedTransferIds.add(transferId);
        List<Transfer> current = getCurrentList();
        if (current.isEmpty()) {
            return;
        }
        List<Transfer> updated = new ArrayList<>(current);
        if (!updated.remove(transfer)) {
            return;
        }
        submitList(updated);
    }

    /**
     * Format peers count efficiently without excessive string allocations.
     * Optimized to avoid String.replaceAll() which creates multiple intermediate strings.
     */
    private static String formatPeers(BittorrentDownload dl) {
        int connectedPeers = dl.getConnectedPeers();
        int peers = dl.getTotalPeers();
        if (connectedPeers > peers) {
            return String.valueOf(connectedPeers);
        }
        return connectedPeers + " / " + peers;
    }

    /**
     * Format seeds count efficiently without excessive string allocations.
     * Optimized to avoid String.replaceAll() which creates multiple intermediate strings.
     */
    private static String formatSeeds(BittorrentDownload dl) {
        int connectedSeeds = dl.getConnectedSeeds();
        int seeds = dl.getTotalSeeds();
        if (connectedSeeds > seeds) {
            return String.valueOf(connectedSeeds);
        }
        String seedsStr = (seeds == -1) ? "?" : String.valueOf(seeds);
        return connectedSeeds + " / " + seedsStr;
    }
}

