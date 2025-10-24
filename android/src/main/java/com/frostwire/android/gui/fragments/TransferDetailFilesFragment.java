/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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
import androidx.recyclerview.widget.LinearLayoutManager;
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
    private static final int PAGE_SIZE = 500;  // Load 500 files at a time to prevent ANR
    private static final int LARGE_FILE_LIST_THRESHOLD = 1000;  // Trigger pagination for 1000+ files

    private TextView fileNumberTextView;
    private TextView totalSizeTextView;
    private RecyclerView recyclerView;
    private TransferDetailFilesRecyclerViewAdapter adapter;

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

        List<TransferItem> items = uiBittorrentDownload.getItems();
        if (items == null) {
            return;
        }
        if (adapter == null) {
            // Use pagination for large file lists (1000+) to prevent ANR
            boolean usePagination = items.size() >= LARGE_FILE_LIST_THRESHOLD;
            adapter = new TransferDetailFilesRecyclerViewAdapter(items, usePagination ? PAGE_SIZE : Integer.MAX_VALUE);
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
        if (uiBittorrentDownload == null || adapter == null) {
            return;
        }
        List<TransferItem> items = uiBittorrentDownload.getItems();
        if (items == null) {
            return;
        }
        fileNumberTextView.setText(getString(R.string.n_files, items.size()));
        totalSizeTextView.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getSize()));
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(adapter);
        }
        adapter.updateTransferItems(items);
    }

    private final static class TransferDetailFilesTransferItemViewHolder extends RecyclerView.ViewHolder {

        private ImageView fileTypeImageView;
        private TextView fileNameTextView;
        private ProgressBar fileProgressBar;
        private TextView fileProgressTextView;
        private TextView fileSizeTextView;
        private ImageButton playButtonImageView;

        TransferDetailFilesTransferItemViewHolder(RelativeLayout itemView) {
            super(itemView);
        }

        void updateTransferItem(TransferItem transferItem) {
            if (fileNameTextView == null) {
                initComponents();
            }
            final Bundle bundle = new Bundle();
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
                updateTransferDataTask(transferItem, bundle);
                SystemUtils.postToUIThread(() -> updateTransferDataPost(this, transferItem, bundle));
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
                                                   Bundle bundle) {
            holder.fileTypeImageView.setImageResource(bundle.getInt("fileTypeIconId"));
            holder.fileNameTextView.setText(transferItem.getName());
            int progress = bundle.getInt("progress");
            holder.fileProgressBar.setProgress(progress);
            holder.fileProgressTextView.setText(MessageFormat.format("{0}%", progress));
            holder.fileSizeTextView.setText(bundle.getString("downloadedPercentage"));
            holder.playButtonImageView.setTag(transferItem);
            // Only pass previewFile hint, recalculate to avoid serialization overhead
            boolean isComplete = bundle.getBoolean("isComplete");
            File previewFile = isComplete ? (transferItem instanceof BTDownloadItem ?
                    previewFile((BTDownloadItem) transferItem) : null) : null;
            holder.updatePlayButtonVisibility(isComplete, previewFile);
        }

        private void initComponents() {
            // file type icon
            fileTypeImageView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_type_icon);
            fileNameTextView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_name);
            fileProgressBar = itemView.findViewById(R.id.fragment_transfer_detail_files_file_progressbar);
            fileProgressTextView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_percentage);
            fileSizeTextView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_download_size);
            playButtonImageView = itemView.findViewById(R.id.fragment_transfer_detail_files_file_play_icon);
            playButtonImageView.setOnClickListener(new OpenOnClickListener(itemView.getContext()));
        }

        private void updatePlayButtonVisibility(boolean isComplete, File previewFile) {
            if (isComplete) {
                playButtonImageView.setVisibility(View.VISIBLE);
            } else {
                playButtonImageView.setVisibility(previewFile != null ? View.VISIBLE : View.GONE);
            }
        }
    }

    private final static class TransferDetailFilesRecyclerViewAdapter extends RecyclerView.Adapter<TransferDetailFilesTransferItemViewHolder> {

        private final List<TransferItem> items;
        private final int pageSize;
        private int currentPage = 0;
        private List<TransferItem> allItems;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private Runnable pendingLoadMoreRunnable;

        TransferDetailFilesRecyclerViewAdapter(List<TransferItem> items, int pageSize) {
            this.allItems = new LinkedList<>(items);
            this.pageSize = pageSize;
            this.items = new LinkedList<>();
            loadNextPage();
        }

        /**
         * Loads the next page of items. For large lists (1000+), only loads PAGE_SIZE items at a time.
         */
        private void loadNextPage() {
            if (allItems == null || allItems.isEmpty()) {
                return;
            }
            int startIndex = currentPage * pageSize;
            int endIndex = Math.min(startIndex + pageSize, allItems.size());

            if (startIndex < allItems.size()) {
                items.addAll(allItems.subList(startIndex, endIndex));
                currentPage++;
            }
        }

        /**
         * Checks if more pages are available to load.
         */
        boolean hasMorePages() {
            return allItems != null && (currentPage * pageSize) < allItems.size();
        }

        /**
         * Loads more pages when user scrolls near the end.
         */
        void loadMorePages() {
            if (hasMorePages()) {
                int startIndex = items.size();
                loadNextPage();
                notifyItemRangeInserted(startIndex, items.size() - startIndex);
            }
        }

        @Override
        public TransferDetailFilesTransferItemViewHolder onCreateViewHolder(ViewGroup parent, int i) {
            return new TransferDetailFilesTransferItemViewHolder((RelativeLayout) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_transfer_detail_files_recyclerview_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull TransferDetailFilesTransferItemViewHolder viewHolder, int i) {
            if (items.isEmpty()) {
                return;
            }
            // Load more pages when user is near the end (within 100 items)
            // Defer the load to prevent calling notify methods during layout computation
            if (hasMorePages() && (i >= items.size() - 100)) {
                if (pendingLoadMoreRunnable == null) {
                    pendingLoadMoreRunnable = () -> {
                        loadMorePages();
                        pendingLoadMoreRunnable = null;
                    };
                    mainHandler.post(pendingLoadMoreRunnable);
                }
            }

            TransferItem transferItem = items.get(i);
            if (transferItem != null) {
                viewHolder.updateTransferItem(transferItem);
            }
        }

        @Override
        public int getItemCount() {
            return items.isEmpty() ? 0 : items.size();
        }

        void updateTransferItems(List<TransferItem> freshItems) {
            try {
                if (items != null && items.size() > 1) {
                    Collections.sort(items, (o1, o2) -> -Integer.compare(o1.getProgress(), o2.getProgress()));
                }
            } catch (Throwable ignored) {
                //Fatal Exception: java.lang.IllegalArgumentException
                //Comparison method violates its general contract!
            }
            try {
                if (freshItems != null && freshItems.size() > 1) {
                    Collections.sort(freshItems, (o1, o2) -> -Integer.compare(o1.getProgress(), o2.getProgress()));
                }
            } catch (Throwable ignored) {
                //Fatal Exception: java.lang.IllegalArgumentException
                //Comparison method violates its general contract!
            }
            // For paginated lists, update allItems but only refresh current page items
            if (freshItems != null) {
                allItems = new LinkedList<>(freshItems);
                // Reset pagination when items are refreshed
                if (items.size() > 0) {
                    currentPage = 0;
                    items.clear();
                    loadNextPage();
                    notifyDataSetChanged();
                }
            }
        }
    }

    private static final class OpenOnClickListener extends ClickAdapter<Context> {

        OpenOnClickListener(Context ctx) {
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