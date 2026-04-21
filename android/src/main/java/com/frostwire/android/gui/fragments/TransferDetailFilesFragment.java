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

package com.frostwire.android.gui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import android.os.Handler;
import android.os.Looper;

import com.frostwire.android.R;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownloadItem;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.Logger;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailFilesFragment extends AbstractTransferDetailFragment {
    private static final Logger LOG = Logger.getLogger(TransferDetailFilesFragment.class);
    private static final int PAGE_SIZE = 500;  // Load 500 files at a time to prevent ANR
    private static final int LARGE_FILE_LIST_THRESHOLD = 1000;  // Trigger pagination for 1000+ files

    private TextView fileNumberTextView;
    private TextView totalSizeTextView;
    private RecyclerView recyclerView;
    private TransferDetailFilesRecyclerViewAdapter adapter;
    private String adapterInfoHash;

    public TransferDetailFilesFragment() {
        super(R.layout.fragment_transfer_detail_files);
    }

    @Override
    protected void initComponents(View v, Bundle savedInstanceState) {
        super.initComponents(v, savedInstanceState);
        fileNumberTextView.setText("");
        totalSizeTextView.setText("");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (uiBittorrentDownload == null) {
            return;
        }
        updateComponents();
    }

    @Override
    protected int getTabTitleStringId() {
        return R.string.files;
    }

    @Override
    public void ensureComponentsReferenced(View rootView) {
        fileNumberTextView = findView(rootView, R.id.fragment_transfer_detail_files_file_number);
        totalSizeTextView = findView(rootView, R.id.fragment_transfer_detail_files_size_all);
        recyclerView = findView(rootView, R.id.fragment_transfer_detail_files_recycler_view);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
            if (recyclerView.getItemAnimator() instanceof SimpleItemAnimator) {
                ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
            }
        }
    }

    @Override
    protected void updateComponents() {
        if (uiBittorrentDownload == null) {
            return;
        }
        List<TransferItem> items = uiBittorrentDownload.getItems();
        if (items == null) {
            return;
        }
        String currentInfoHash = uiBittorrentDownload.getInfoHash();
        if (adapter == null || adapterInfoHash == null || !adapterInfoHash.equals(currentInfoHash)) {
            boolean usePagination = items.size() >= LARGE_FILE_LIST_THRESHOLD;
            adapter = new TransferDetailFilesRecyclerViewAdapter(items, usePagination ? PAGE_SIZE : Integer.MAX_VALUE);
            adapterInfoHash = currentInfoHash;
            if (recyclerView != null) {
                recyclerView.setAdapter(adapter);
            }
        }
        fileNumberTextView.setText(getString(R.string.n_files, items.size()));
        totalSizeTextView.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getSize()));
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(adapter);
        }
        adapter.updateTransferItems(items);
    }

    @Override
    protected void onTransferChanged() {
        adapterInfoHash = null;
        adapter = null;
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
    }

    private final static class TransferDetailFilesTransferItemViewHolder extends RecyclerView.ViewHolder {

        private ImageView fileTypeImageView;
        private TextView fileNameTextView;
        private ProgressBar fileProgressBar;
        private TextView fileProgressTextView;
        private TextView fileSizeTextView;
        private ImageButton playButtonImageView;
        private TransferItem currentTransferItem;
        private String currentTransferItemKey;

        TransferDetailFilesTransferItemViewHolder(RelativeLayout itemView) {
            super(itemView);
        }

        void updateTransferItem(TransferItem transferItem) {
            if (playButtonImageView == null) {
                initComponents();
            }
            currentTransferItem = transferItem;
            currentTransferItemKey = transferItemKey(transferItem);
            if (fileNameTextView != null) {
                fileNameTextView.setText(transferItem.getName());
            }
            if (fileTypeImageView != null && transferItem.getFile() != null) {
                fileTypeImageView.setImageResource(MediaType.getFileTypeIconId(FilenameUtils.getExtension(transferItem.getFile().getAbsolutePath())));
            }
            if (playButtonImageView != null) {
                playButtonImageView.setVisibility(View.GONE);
            }
            final Bundle bundle = new Bundle();
            final String transferItemKey = currentTransferItemKey;
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
                updateTransferDataTask(transferItem, bundle);
                SystemUtils.postToUIThread(() -> updateTransferDataPost(this, transferItem, transferItemKey, bundle));
            });
        }

        private static void updateTransferDataTask(final TransferItem transferItem,
                                                   final Bundle bundleResult) {
            SystemUtils.ensureBackgroundThreadOrCrash("TransferDetailFilesTransferItemViewHolder::updateTransferDataTask");
            Thread.currentThread().setName("updateTransferDataTask");
            bundleResult.putInt("fileTypeIconId", MediaType.getFileTypeIconId(FilenameUtils.getExtension(transferItem.getFile().getAbsolutePath())));
            bundleResult.putInt("progress", transferItem.getProgress());
            bundleResult.putString("downloadedPercentage", UIUtils.getBytesInHuman(transferItem.getDownloaded()) + "/" + UIUtils.getBytesInHuman(transferItem.getSize()));
            bundleResult.putBoolean("isComplete", transferItem.isComplete());
            // Don't serialize File objects to Bundle to prevent TransactionTooLargeException.
            // PreviewFile will be recalculated in updateTransferDataPost if needed.
        }

        private static void updateTransferDataPost(TransferDetailFilesTransferItemViewHolder holder,
                                                    TransferItem transferItem,
                                                    String transferItemKey,
                                                    Bundle bundle) {
            if (!holder.isStillBoundTo(transferItem, transferItemKey)) {
                return;
            }
            if (holder.fileTypeImageView != null) {
                holder.fileTypeImageView.setImageResource(bundle.getInt("fileTypeIconId"));
            }
            if (holder.fileNameTextView != null) {
                holder.fileNameTextView.setText(transferItem.getName());
            }
            int progress = bundle.getInt("progress");
            if (holder.fileProgressBar != null) {
                holder.fileProgressBar.setProgress(progress);
            }
            if (holder.fileProgressTextView != null) {
                holder.fileProgressTextView.setText(MessageFormat.format("{0}%", progress));
            }
            if (holder.fileSizeTextView != null) {
                holder.fileSizeTextView.setText(bundle.getString("downloadedPercentage"));
            }
            boolean isComplete = bundle.getBoolean("isComplete");
            File previewFile = isComplete ? (transferItem instanceof BTDownloadItem ?
                    previewFile((BTDownloadItem) transferItem) : null) : null;
            holder.updatePlayButtonVisibility(isComplete, previewFile);
        }

        private boolean isStillBoundTo(TransferItem transferItem, String transferItemKey) {
            return currentTransferItem == transferItem || transferItemKey.equals(currentTransferItemKey);
        }

        private void initComponents() {
            fileTypeImageView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_type_icon);
            fileNameTextView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_name);
            fileProgressBar = itemView.findViewById(R.id.fragment_transfer_detail_files_file_progressbar);
            fileProgressTextView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_percentage);
            fileSizeTextView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_download_size);
            playButtonImageView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_play_icon);
            if (playButtonImageView != null) {
                playButtonImageView.setOnClickListener(new OpenOnClickListener(this));
            }
        }

        private void updatePlayButtonVisibility(boolean isComplete, File previewFile) {
            if (playButtonImageView == null) {
                return;
            }
            if (isComplete) {
                playButtonImageView.setVisibility(View.VISIBLE);
            } else {
                playButtonImageView.setVisibility(previewFile != null ? View.VISIBLE : View.GONE);
            }
        }

        TransferItem getCurrentTransferItem() {
            return currentTransferItem;
        }

        private static String transferItemKey(TransferItem transferItem) {
            File file = transferItem.getFile();
            if (file != null) {
                return file.getAbsolutePath();
            }
            return transferItem.getName() + ":" + transferItem.getSize();
        }
    }

    private final static class TransferDetailFilesRecyclerViewAdapter extends ListAdapter<TransferItem, TransferDetailFilesTransferItemViewHolder> {

        private final List<TransferItem> combinedItems;
        private final int pageSize;
        private int currentPage = 0;
        private List<TransferItem> allItems;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private Runnable pendingLoadMoreRunnable;

        TransferDetailFilesRecyclerViewAdapter(List<TransferItem> items, int pageSize) {
            super(new TransferItemDiffCallback());
            this.allItems = new LinkedList<>(items);
            this.pageSize = pageSize;
            this.combinedItems = new LinkedList<>();
            loadNextPage();
        }

        private void loadNextPage() {
            if (allItems == null || allItems.isEmpty()) {
                return;
            }
            int startIndex = currentPage * pageSize;
            int endIndex = Math.min(startIndex + pageSize, allItems.size());
            if (startIndex < allItems.size()) {
                combinedItems.addAll(allItems.subList(startIndex, endIndex));
                currentPage++;
            }
        }

        boolean hasMorePages() {
            return allItems != null && (currentPage * pageSize) < allItems.size();
        }

        void loadMorePages() {
            if (hasMorePages() && pendingLoadMoreRunnable == null) {
                int startIndex = combinedItems.size();
                loadNextPage();
                submitList(new LinkedList<>(combinedItems));
            }
        }

        @Override
        public TransferDetailFilesTransferItemViewHolder onCreateViewHolder(ViewGroup parent, int i) {
            TransferDetailFilesTransferItemViewHolder holder = new TransferDetailFilesTransferItemViewHolder((RelativeLayout) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_transfer_detail_files_recyclerview_item, parent, false));
            holder.initComponents();
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull TransferDetailFilesTransferItemViewHolder viewHolder, int i) {
            TransferItem transferItem = getItem(i);
            if (transferItem == null) {
                return;
            }
            if (hasMorePages() && (i >= combinedItems.size() - 100)) {
                if (pendingLoadMoreRunnable == null) {
                    pendingLoadMoreRunnable = () -> {
                        loadMorePages();
                        pendingLoadMoreRunnable = null;
                    };
                    mainHandler.post(pendingLoadMoreRunnable);
                }
            }
            if (viewHolder.playButtonImageView != null) {
                viewHolder.playButtonImageView.setTag(transferItem);
            }
            viewHolder.updateTransferItem(transferItem);
        }

        void updateTransferItems(List<TransferItem> freshItems) {
            if (freshItems == null) {
                return;
            }
            // Sort calls getProgress() which does blocking JNI (fileProgress).
            // Move sort to background thread to avoid ANR on UI thread.
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
                List<TransferItem> sortedFresh = new LinkedList<>(freshItems);
                try {
                    if (sortedFresh.size() > 1) {
                        Collections.sort(sortedFresh, (o1, o2) -> -Integer.compare(o1.getProgress(), o2.getProgress()));
                    }
                } catch (Throwable ignored) {
                }
                final List<TransferItem> finalSorted = sortedFresh;
                SystemUtils.postToUIThread(() -> {
                    allItems = finalSorted;
                    currentPage = 0;
                    combinedItems.clear();
                    loadNextPage();
                    submitList(new LinkedList<>(combinedItems));
                });
            });
        }
    }

    private static final class TransferItemDiffCallback extends DiffUtil.ItemCallback<TransferItem> {
        @Override
        public boolean areItemsTheSame(TransferItem oldItem, TransferItem newItem) {
            return oldItem.getFile() != null && newItem.getFile() != null &&
                    oldItem.getFile().getAbsolutePath().equals(newItem.getFile().getAbsolutePath());
        }

        @Override
        public boolean areContentsTheSame(TransferItem oldItem, TransferItem newItem) {
            return oldItem.getProgress() == newItem.getProgress() &&
                    oldItem.isComplete() == newItem.isComplete();
        }
    }

    private static final class OpenOnClickListener extends ClickAdapter<Context> {
        private final TransferDetailFilesTransferItemViewHolder holderRef;

        OpenOnClickListener(TransferDetailFilesTransferItemViewHolder holder) {
            super(holder.itemView.getContext());
            this.holderRef = holder;
        }

        public void onClick(Context ctx, View v) {
            TransferItem item = holderRef.getCurrentTransferItem();
            if (item != null) {
                File path = item.isComplete() ? item.getFile() : null;
                if (path == null && item instanceof BTDownloadItem) {
                    path = previewFile((BTDownloadItem) item);
                }
                if (path != null) {
                    // Use MusicUtils.playFile() for audio files - same code path as My Music
                    // This ensures consistent, fast playback without ephemeral playlist delays
                    if (UIUtils.isAudioFile(path.getAbsolutePath())) {
                        com.andrew.apollo.utils.MusicUtils.playFileFromUserItemClick(ctx, path);
                    } else {
                        UIUtils.openFile(ctx, path);
                    }
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
}
