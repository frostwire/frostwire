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
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.transfers.TransferState;

import static com.frostwire.android.gui.util.UIUtils.getBytesInHuman;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailStatusFragment extends AbstractTransferDetailFragment {

    private TextView completedTextView;
    private TextView timeLeftTextView;
    private TextView downloadedTextView;
    private TextView uploadedTextView;
    private TextView shareRatioTextView;
    private TextView peersTextView;
    private TextView seedsTextView;
    private TextView activeTimeTextView;
    private TextView seedingTimeTextView;

    public TransferDetailStatusFragment() {
        super(R.layout.fragment_transfer_detail_status);
    }

    @Override
    protected void initComponents(View v, Bundle savedInstanceState) {
        super.initComponents(v, savedInstanceState);
        completedTextView = findView(v, R.id.fragment_transfer_detail_status_completion);
        completedTextView.setText("");
        timeLeftTextView = findView(v, R.id.fragment_transfer_detail_status_time_left);
        timeLeftTextView.setText("");
        downloadedTextView = findView(v, R.id.fragment_transfer_detail_status_downloaded);
        downloadedTextView.setText("");
        uploadedTextView = findView(v, R.id.fragment_transfer_detail_status_uploaded);
        uploadedTextView.setText("");
        shareRatioTextView = findView(v, R.id.fragment_transfer_detail_status_share_ratio);
        shareRatioTextView.setText("");
        peersTextView = findView(v, R.id.fragment_transfer_detail_status_peers);
        peersTextView.setText("");
        seedsTextView = findView(v, R.id.fragment_transfer_detail_status_seeds);
        seedsTextView.setText("");
        activeTimeTextView = findView(v, R.id.fragment_transfer_detail_status_active_time);
        activeTimeTextView.setText("");
        seedingTimeTextView = findView(v, R.id.fragment_transfer_detail_status_seeding_time);
        seedingTimeTextView.setText("");
    }

    @Override
    public void onTime() {
        super.onTime();
        if (uiBittorrentDownload == null) {
            return;
        }
        completedTextView.setText(uiBittorrentDownload.getProgress() + "%");
        if (uiBittorrentDownload.getState() == TransferState.DOWNLOADING) {
            timeLeftTextView.setText(seconds2time(uiBittorrentDownload.getETA()));
        } else {
            timeLeftTextView.setText("");
        }
        downloadedTextView.setText(getString(R.string.m_of_n_strings,
                getBytesInHuman(uiBittorrentDownload.getDl().getTotalBytesReceived()),
                getBytesInHuman(uiBittorrentDownload.getSize())));
        uploadedTextView.setText(getBytesInHuman(uiBittorrentDownload.getBytesSent()));
        shareRatioTextView.setText(getShareRatio(uiBittorrentDownload));
        peersTextView.setText(getString(R.string.m_of_n_decimals, uiBittorrentDownload.getConnectedPeers(), uiBittorrentDownload.getTotalPeers()));
        seedsTextView.setText(getString(R.string.m_of_n_decimals, uiBittorrentDownload.getConnectedSeeds(), uiBittorrentDownload.getTotalSeeds()));
        if (torrentHandle != null) {
            activeTimeTextView.setText(seconds2time(torrentHandle.status().activeDuration()));
            seedingTimeTextView.setText(seconds2time(torrentHandle.status().seedingDuration()));
        }
    }
}
