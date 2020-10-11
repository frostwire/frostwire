/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

import com.frostwire.android.R;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.bittorrent.BTDownloadItem;
import com.frostwire.transfers.TransferItem;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailFilesFragment extends AbstractTransferDetailFragment {
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
            adapter = new TransferDetailFilesRecyclerViewAdapter(items);
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
            async(this,
                    TransferDetailFilesTransferItemViewHolder::updateTransferDataTask,
                    transferItem,
                    bundle,
                    TransferDetailFilesTransferItemViewHolder::updateTransferDataPost);
        }

        private static void updateTransferDataTask(TransferDetailFilesTransferItemViewHolder holder,
                                                   final TransferItem transferItem,
                                                   final Bundle bundleResult) {
            Thread.currentThread().setName("updateTransferDataTask");
            bundleResult.putInt("fileTypeIconId", MediaType.getFileTypeIconId(FilenameUtils.getExtension(transferItem.getFile().getAbsolutePath())));
            bundleResult.putInt("progress", transferItem.getProgress());
            bundleResult.putString("downloadedPercentage", UIUtils.getBytesInHuman(transferItem.getDownloaded()) + "/" + UIUtils.getBytesInHuman(transferItem.getSize()));
            bundleResult.putBoolean("isComplete", transferItem.isComplete());
            bundleResult.putSerializable("previewFile", previewFile((BTDownloadItem) transferItem));
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
            holder.updatePlayButtonVisibility(bundle.getBoolean("isComplete"), (File) bundle.getSerializable("previewFile"));
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

        TransferDetailFilesRecyclerViewAdapter(List<TransferItem> items) {
            this.items = new LinkedList<>(items);
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
            AbstractTransferDetailFragment.updateAdapterItems(this, items, freshItems);
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