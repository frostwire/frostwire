/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
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
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
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
import com.frostwire.android.gui.adapters.menu.TransferDetailsMenuAction;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.TransferStateStrings;
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

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class TransferListAdapter extends RecyclerView.Adapter<TransferListAdapter.ViewHolder> {
    private static final Logger LOG = Logger.getLogger(TransferListAdapter.class);
    private final WeakReference<Context> context;

    /**
     * Keep track of all dialogs ever opened so we dismiss when we leave to avoid memory leaks
     */
    private final List<Dialog> dialogs;
    private List<Transfer> list;

    public TransferListAdapter(Context context, List<Transfer> list) {
        this.context = new WeakReference<>(context);
        this.dialogs = new ArrayList<>();
        this.list = list.equals(Collections.emptyList()) ? new ArrayList<>() : list;
        this.transferStateStrings = TransferStateStrings.getInstance(context);
    }

    // RecyclerView.Adapter methods
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int i) {
        LinearLayout convertView = (LinearLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_list_item, parent, false);
        return new ViewHolder(context.get(), this, convertView, new ViewOnClickListener(), new ViewOnLongClickListener(), new OpenOnClickListener(context.get()));
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        if (list == null || list.isEmpty()) {
            return;
        }
        viewHolder.updateView(i);
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    public void updateList(List<Transfer> g) {
        if (list != null) {
            list.clear();
            list.addAll(g);
        } else {
            list = g;
        }
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

        items.add(new ResumeDownloadMenuAction(context.get(), download, R.string.resume_torrent_menu_action));


        items.add(new TransferDetailsMenuAction(context.get(), R.string.show_torrent_details, download.getInfoHash()));

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
        private WeakReference<Context> contextRef;
        private WeakReference<TransferListAdapter> adapterRef;
        private OnClickListener viewOnClickListener;
        private ViewOnLongClickListener viewOnLongClickListener;
        private OpenOnClickListener playOnClickListener;
        private final TransferStateStrings transferStateStrings;

        public ViewHolder(Context context,
                          TransferListAdapter adapter,
                          View itemView,
                          ViewOnClickListener viewOnClickListener,
                          ViewOnLongClickListener viewOnLongClickListener,
                          OpenOnClickListener openOnClickListener) {
            super(itemView);
            this.contextRef = Ref.weak(context);
            this.adapterRef = Ref.weak(adapter);
            this.viewOnClickListener = viewOnClickListener;
            this.viewOnLongClickListener = viewOnLongClickListener;
            this.playOnClickListener = openOnClickListener;
            this.transferStateStrings = TransferStateStrings.getInstance(context);
        }

        public void updateView(int position) {
            if (Ref.alive(adapterRef) && Ref.alive(adapterRef)) {
                TransferListAdapter transferListAdapter = adapterRef.get();
                if (transferListAdapter.list != null && !transferListAdapter.list.isEmpty()) {
                    Transfer transfer = transferListAdapter.list.get(position);

                    LinearLayout listItemLinearLayoutHolder = (LinearLayout) itemView;
                    listItemLinearLayoutHolder.setOnClickListener(viewOnClickListener);
                    listItemLinearLayoutHolder.setOnLongClickListener(viewOnLongClickListener);
                    listItemLinearLayoutHolder.setClickable(true);
                    listItemLinearLayoutHolder.setLongClickable(true);
                    listItemLinearLayoutHolder.setTag(transfer);

                    try {
                        if (transfer instanceof BittorrentDownload) {
                            populateBittorrentDownload(listItemLinearLayoutHolder, (BittorrentDownload) transfer);
                        } else if (transfer instanceof HttpDownload) {
                            populateHttpDownload(listItemLinearLayoutHolder, (HttpDownload) transfer);
                        } else if (transfer instanceof YouTubeDownload) {
                            populateCloudDownload(listItemLinearLayoutHolder, transfer);
                        } else if (transfer instanceof SoundcloudDownload) {
                            populateCloudDownload(listItemLinearLayoutHolder, transfer);
                        }
                    } catch (Throwable e) {
                        LOG.error("Not able to populate group view in expandable list:" + e.getMessage());
                    }
                }
            }
        }

        private void populateHttpDownload(LinearLayout view, HttpDownload download) {
            // TODO: cache these component references to save on graph searches
            TextView title = view.findViewById(R.id.view_transfer_list_item_title);
            ProgressBar progress = view.findViewById(R.id.view_transfer_list_item_progress);
            TextView status = view.findViewById(R.id.view_transfer_list_item_status);
            TextView speed = view.findViewById(R.id.view_transfer_list_item_speed);
            TextView size = view.findViewById(R.id.view_transfer_list_item_size);
            TextView seeds = view.findViewById(R.id.view_transfer_list_item_seeds);
            TextView peers = view.findViewById(R.id.view_transfer_list_item_peers);
            ImageButton buttonPlay = view.findViewById(R.id.view_transfer_list_item_button_play);

            seeds.setText("");
            peers.setText("");
            title.setText(download.getDisplayName());
            title.setCompoundDrawables(null, null, null, null);
            setProgress(progress, download.getProgress());
            String downloadStatus = transferStateStrings.get(download.getState());
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

        private void populateBittorrentDownload(LinearLayout view, BittorrentDownload download) {
            // TODO: cache these component references to save on graph searches
            TextView title = view.findViewById(R.id.view_transfer_list_item_title);
            ProgressBar progress = view.findViewById(R.id.view_transfer_list_item_progress);
            TextView status = view.findViewById(R.id.view_transfer_list_item_status);
            TextView speed = view.findViewById(R.id.view_transfer_list_item_speed);
            TextView size = view.findViewById(R.id.view_transfer_list_item_size);

            TextView seeds = view.findViewById(R.id.view_transfer_list_item_seeds);
            TextView peers = view.findViewById(R.id.view_transfer_list_item_peers);

            ImageButton buttonPlay = view.findViewById(R.id.view_transfer_list_item_button_play);

            Context context = view.getContext();

            seeds.setText(context.getString(R.string.seeds_n, formatSeeds(download)));
            peers.setText(context.getString(R.string.peers_n, formatPeers(download)));
            seeds.setVisibility(View.VISIBLE);
            peers.setVisibility(View.VISIBLE);

            title.setText(download.getDisplayName());
            setProgress(progress, download.getProgress());
            title.setCompoundDrawables(null, null, null, null);

            final String downloadStatus = transferStateStrings.get(download.getState());
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

        private void populateCloudDownload(LinearLayout view, Transfer download) {
            // TODO: cache these component references to save on graph searches
            TextView title = view.findViewById(R.id.view_transfer_list_item_title);
            ProgressBar progress = view.findViewById(R.id.view_transfer_list_item_progress);
            TextView status = view.findViewById(R.id.view_transfer_list_item_status);
            TextView speed = view.findViewById(R.id.view_transfer_list_item_speed);
            TextView size = view.findViewById(R.id.view_transfer_list_item_size);
            TextView seeds = view.findViewById(R.id.view_transfer_list_item_seeds);
            TextView peers = view.findViewById(R.id.view_transfer_list_item_peers);
            ImageButton buttonPlay = view.findViewById(R.id.view_transfer_list_item_button_play);

            seeds.setText("");
            peers.setText("");
            title.setText(download.getDisplayName());
            title.setCompoundDrawables(null, null, null, null);
            setProgress(progress, download.getProgress());
            status.setText(transferStateStrings.get(download.getState()));
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
                    status.setText(transferStateStrings.get(download.getState()) + " (" + yt.demuxingProgress() + "%)");
                }
            }
        }

        private void setPaymentOptionDrawable(UIBittorrentDownload download, TextView title) {
            if (Ref.alive(contextRef)) {
                return;
            }
            final PaymentOptions paymentOptions = download.getPaymentOptions();
            final Resources r = contextRef.get().getResources();
            Drawable tipDrawable = (paymentOptions.bitcoin != null) ? r.getDrawable(R.drawable.contextmenu_icon_donation_bitcoin) : r.getDrawable(R.drawable.contextmenu_icon_donation_fiat);
            if (tipDrawable != null) {
                final int iconHeightInPixels = r.getDimensionPixelSize(R.dimen.view_transfer_list_item_title_left_drawable);
                tipDrawable.setBounds(0, 0, iconHeightInPixels, iconHeightInPixels);
                title.setCompoundDrawables(tipDrawable, null, null, null);
            }
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

        // TODO: figure out when/where to plug this
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
}




