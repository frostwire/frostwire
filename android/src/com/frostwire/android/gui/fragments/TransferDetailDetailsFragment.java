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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.adapters.menu.CopyToClipboardMenuAction;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.transfers.TransferItem;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailDetailsFragment extends AbstractTransferDetailFragment {
    private TextView storagePath;
    private CheckBox sequentialDownloadCheckBox;
    private CheckBox seedingOnCheckBox;
    private TextView totalSize;
    private TextView numberOfFiles;
    private TextView downloadSpeedLimit;
    private TextView uploadSpeedLimit;
    private TextView hash;
    private ImageButton hashCopyButton;
    private TextView magnet;
    private ImageButton magnetCopyButton;
    private TextView createdOn;
    private TextView comment;
    private CompoundButton.OnCheckedChangeListener onSequentialDownloadCheckboxCheckedListener;
    private View.OnClickListener onCopyToClipboardListener;

    public TransferDetailDetailsFragment() {
        super(R.layout.fragment_transfer_detail_details);
    }

    @Override
    protected void initComponents(View rv, Bundle savedInstanceState) {
        super.initComponents(rv, savedInstanceState);
        storagePath = findView(rv, R.id.fragment_transfer_detail_details_storage_path);
        sequentialDownloadCheckBox = findView(rv, R.id.fragment_transfer_detail_details_sequential_download_checkBox);
        seedingOnCheckBox = findView(rv, R.id.fragment_transfer_detail_details_seeding_on_checkBox);
        totalSize = findView(rv, R.id.fragment_transfer_detail_details_total_size);
        numberOfFiles = findView(rv, R.id.fragment_transfer_detail_details_files_number);
        downloadSpeedLimit = findView(rv, R.id.fragment_transfer_detail_details_speed_limit_download);
        uploadSpeedLimit = findView(rv, R.id.fragment_transfer_detail_details_speed_limit_upload);
        hash = findView(rv, R.id.fragment_transfer_detail_details_hash);
        hashCopyButton = findView(rv, R.id.fragment_transfer_detail_details_hash_copy_button);
        magnet = findView(rv, R.id.fragment_transfer_detail_details_magnet);
        magnetCopyButton = findView(rv, R.id.fragment_transfer_detail_details_magnet_copy_button);
        createdOn = findView(rv, R.id.fragment_transfer_detail_details_created_on);
        comment = findView(rv, R.id.fragment_transfer_detail_details_comment);
        storagePath.setText("");
        sequentialDownloadCheckBox.setChecked(false);
        seedingOnCheckBox.setChecked(false);
        seedingOnCheckBox.setEnabled(false);
        totalSize.setText("");
        numberOfFiles.setText("");
        downloadSpeedLimit.setText("");
        uploadSpeedLimit.setText("");
        hash.setText("");
        magnet.setText("");
        createdOn.setText("");
        comment.setText("");
        onSequentialDownloadCheckboxCheckedListener = null;
        onCopyToClipboardListener = null;
    }

    @Override
    public void onTime() {
        super.onTime();
        if (uiBittorrentDownload != null) {
            BTDownload btDL = uiBittorrentDownload.getDl();
            if (onCopyToClipboardListener == null) {
                onCopyToClipboardListener = new CopyToClipboardOnClickListener(uiBittorrentDownload);
            }
            // static data for this download is done only once
            if ("".equals(storagePath.getText())) {
                storagePath.setText(uiBittorrentDownload.getSavePath().getAbsolutePath());
            }
            if ("".equals(totalSize.getText())) {
                totalSize.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getSize()));
            }
            if ("".equals(numberOfFiles.getText())) {
                List<TransferItem> items = uiBittorrentDownload.getItems();
                int fileCount = items == null ? 0 : items.size();
                numberOfFiles.setText(fileCount + "");
            }
            if ("".equals(hash.getText())) {
                hash.setText(uiBittorrentDownload.getInfoHash());
                hash.setOnClickListener(onCopyToClipboardListener);
                hashCopyButton.setOnClickListener(onCopyToClipboardListener);
            }
            if ("".equals(magnet.getText())) {
                magnet.setText(uiBittorrentDownload.magnetUri());
                magnet.setOnClickListener(onCopyToClipboardListener);
                magnetCopyButton.setOnClickListener(onCopyToClipboardListener);
            }
            if ("".equals(createdOn.getText())) {
                createdOn.setText(DateUtils.formatDateTime(getActivity(), uiBittorrentDownload.getCreated().getTime(), DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
            }
            if ("".equals(comment.getText())) {
                // TODO: figure out hot to get the torrent descripton
                //comment.setText(extras.get("btdownload"));
            }
            if (onSequentialDownloadCheckboxCheckedListener == null) {
                onSequentialDownloadCheckboxCheckedListener = new SequentialDownloadCheckboxCheckedListener(uiBittorrentDownload);
            }
            sequentialDownloadCheckBox.setOnCheckedChangeListener(null);
            sequentialDownloadCheckBox.setChecked(btDL.isSequentialDownload());
            sequentialDownloadCheckBox.setOnCheckedChangeListener(onSequentialDownloadCheckboxCheckedListener);
            seedingOnCheckBox.setChecked(btDL.isSeeding());
            // TODO: add touch listener to this row and present a dialog with sliders
            // to control both speed limits.
            int downloadRateLimit = btDL.getDownloadRateLimit();
            int uploadRateLimit = btDL.getUploadRateLimit();
            if (downloadRateLimit != -1) {
                downloadSpeedLimit.setText(UIUtils.getBytesInHuman(downloadRateLimit) + "/s");
            } else {
                downloadSpeedLimit.setText(R.string.unlimited);
            }
            if (uploadRateLimit != -1) {
                uploadSpeedLimit.setText(UIUtils.getBytesInHuman(uploadRateLimit) + "/s");
            } else {
                uploadSpeedLimit.setText(R.string.unlimited);
            }
        }
    }

    private static final class SequentialDownloadCheckboxCheckedListener implements CompoundButton.OnCheckedChangeListener {
        private final UIBittorrentDownload uiBittorrentDownload;

        SequentialDownloadCheckboxCheckedListener(UIBittorrentDownload uiBittorrentDownload) {
            this.uiBittorrentDownload = uiBittorrentDownload;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (uiBittorrentDownload != null) {
                uiBittorrentDownload.getDl().setSequentialDownload(isChecked);
            }
        }
    }

    private static final class CopyToClipboardOnClickListener implements View.OnClickListener {

        private final UIBittorrentDownload uiBittorrentDownload;

        CopyToClipboardOnClickListener(UIBittorrentDownload uiBittorrentDownload) {
            this.uiBittorrentDownload = uiBittorrentDownload;
        }

        @Override
        public void onClick(View v) {
            String data = null;
            int drawableId = 0, actionNameId = 0, messageId = 0;
            if (v.getId() == R.id.fragment_transfer_detail_details_hash_copy_button ||
                    v.getId() == R.id.fragment_transfer_detail_details_hash) {
                drawableId = R.drawable.contextmenu_icon_copy;
                actionNameId = R.string.transfers_context_menu_copy_infohash;
                messageId = R.string.transfers_context_menu_copy_infohash_copied;
                data = uiBittorrentDownload.getInfoHash();
            } else if (v.getId() == R.id.fragment_transfer_detail_details_magnet_copy_button ||
                    v.getId() == R.id.fragment_transfer_detail_details_magnet) {
                drawableId = R.drawable.contextmenu_icon_magnet;
                actionNameId = R.string.transfers_context_menu_copy_magnet;
                messageId = R.string.transfers_context_menu_copy_magnet_copied;
                data = uiBittorrentDownload.magnetUri();
            }
            CopyToClipboardMenuAction action =
                    new CopyToClipboardMenuAction(v.getContext(),
                            drawableId,
                            actionNameId,
                            messageId,
                            data);
            action.onClick();
        }
    }
}
