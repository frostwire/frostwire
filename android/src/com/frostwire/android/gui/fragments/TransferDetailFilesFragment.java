/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.transfers.TransferItem;

import java.util.List;

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
    private LinearLayoutManager layoutManager;

    public TransferDetailFilesFragment() {
        super(R.layout.fragment_transfer_detail_files);
    }

    @Override
    protected void initComponents(View v, Bundle savedInstanceState) {
        super.initComponents(v, savedInstanceState);

        fileNumberTextView = findView(v, R.id.fragment_transfer_detail_files_file_number);
        fileNumberTextView.setText("");

        totalSizeTextView = findView(v, R.id.fragment_transfer_detail_files_size_all);
        totalSizeTextView.setText("");

        recyclerView = findView(v, R.id.fragment_transfer_detail_files_recycler_view);
    }

    @Override
    public void onTime() {
        super.onTime();

        if (uiBittorrentDownload == null) {
            return;
        }

        List<TransferItem> items = uiBittorrentDownload.getItems();

        if (items == null) {
            return;
        }

        // since these transfer properties don't change, we'll only do this once
        if ("".equals(fileNumberTextView.getText())) {
            fileNumberTextView.setText(getString(R.string.n_files, items.size()));
            totalSizeTextView.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getSize()));
        }

        if (adapter == null) {
            adapter = new TransferDetailFilesRecyclerViewAdapter(items);
            layoutManager = new LinearLayoutManager(getActivity());
            recyclerView.setAdapter(adapter);
            // still don't understand this part below, I don't see a RelativeLayoutManager available anyways
            recyclerView.setLayoutManager(layoutManager);
        } else {
            adapter.updateTransferItems(items);
        }

    }

    private final static class TransferDetailFilesTransferItemViewHolder extends RecyclerView.ViewHolder {
        public TransferDetailFilesTransferItemViewHolder(RelativeLayout itemView) {
            super(itemView);
        }
    }

    private final static class TransferDetailFilesRecyclerViewAdapter extends RecyclerView.Adapter<TransferDetailFilesTransferItemViewHolder> {

        private List<TransferItem> items;

        public TransferDetailFilesRecyclerViewAdapter(List<TransferItem> items) {
            this.items = items;
        }

        @Override
        public TransferDetailFilesTransferItemViewHolder onCreateViewHolder(ViewGroup parent, int i) {
            return new TransferDetailFilesTransferItemViewHolder((RelativeLayout) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_transfer_detail_files_recyclerview_item, parent, false));
        }

        @Override
        public void onBindViewHolder(TransferDetailFilesTransferItemViewHolder viewHolder, int i) {
            if (items == null || items.isEmpty()) {
                return;
            }
            TransferItem transferItem = items.get(i);
            // file type icon

            // file name
            // TODO: cache this view if possible on the view holder to save up this query everytime
            // perhaps through a viewHolder.updateModel(transferItem) method.
            TextView fileNameTextView = viewHolder.itemView.findViewById(R.id.fragment_transfer_detail_files_file_name);
            fileNameTextView.setText(transferItem.getName());
        }

        @Override
        public int getItemCount() {
            return (items == null || items.isEmpty()) ? 0 : items.size();
        }

        public void updateTransferItems(List<TransferItem> freshItems) {
            items.clear();
            items.addAll(freshItems);
            notifyDataSetChanged();
        }
    }
}
