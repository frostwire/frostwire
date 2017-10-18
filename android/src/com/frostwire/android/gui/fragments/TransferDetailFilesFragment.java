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
import android.view.View;
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
    }

    @Override
    public void onTime() {
        super.onTime();

        if (uiBittorrentDownload == null) {
            return;
        }

        List<TransferItem> items = uiBittorrentDownload.getItems();

        // since these transfer properties don't change, we'll only do this once
        if ("".equals(fileNumberTextView.getText()) && items != null) {
            fileNumberTextView.setText(getString(R.string.n_files, items.size()));
            totalSizeTextView.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getSize()));
        }
    }
}
