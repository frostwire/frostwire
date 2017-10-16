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

package com.frostwire.android.gui.views;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.TransferStateStrings;
import com.frostwire.util.Logger;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 10/10/17.
 */


public abstract class AbstractTransferDetailFragment extends AbstractFragment implements TimerObserver {
    private static Logger LOG = Logger.getLogger(AbstractTransferDetailFragment.class);
    protected final TransferStateStrings transferStateStrings;

    private String tabTitle;
    protected UIBittorrentDownload uiBittorrentDownload;
    private TextView detailProgressTitleTextView;
    private ProgressBar detailProgressProgressBar;
    private TimerSubscription subscription;
    private TextView detailProgressStatusTextView;

    public AbstractTransferDetailFragment(int layoutId) {
        super(layoutId);
        setHasOptionsMenu(true);
        // we can pass null below since this map has already been initialized by TransferListAdapter
        transferStateStrings = TransferStateStrings.getInstance(null);
    }

    public String getTabTitle() {
        return tabTitle;
    }

    public AbstractTransferDetailFragment init(String tabTitle, UIBittorrentDownload uiBittorrentDownload) {
        this.tabTitle = tabTitle;
        this.uiBittorrentDownload = uiBittorrentDownload;
        return this;
    }

    @Override
    protected void initComponents(View rootView, Bundle savedInstanceState) {
        super.initComponents(rootView, savedInstanceState);
        initDetailProgress(rootView);
        updateDetailProgress(uiBittorrentDownload);
    }

    /**
     * This is a common section at the top of all the detail fragments
     * which contains the title of the transfer and the current progress
     * */
    protected void initDetailProgress(View rootView) {
        detailProgressTitleTextView = findView(rootView, R.id.view_transfer_detail_progress_title);
        detailProgressProgressBar = findView(rootView, R.id.view_transfer_detail_progress_progress);
        detailProgressStatusTextView = findView(rootView, R.id.view_transfer_detail_progress_status);
    }

    protected void updateDetailProgress(UIBittorrentDownload uiBittorrentDownload) {
        if (detailProgressTitleTextView != null) {
            detailProgressTitleTextView.setText(uiBittorrentDownload.getDisplayName());
        }

        if (detailProgressProgressBar != null) {
            detailProgressProgressBar.setProgress(uiBittorrentDownload.getProgress());
        }

        if (detailProgressStatusTextView != null) {
            detailProgressStatusTextView.setText(transferStateStrings.get(uiBittorrentDownload.getState()));
        }
    }

    @Override
    public void onTime() {
        LOG.info("onTime(): " + uiBittorrentDownload.getInfoHash() + " :: " + uiBittorrentDownload.getProgress() + " % :: " + uiBittorrentDownload.getDisplayName());
        updateDetailProgress(uiBittorrentDownload);
    }

    @Override
    public void onResume() {
        super.onResume();
        subscription = TimerService.subscribe(this, 2);
    }

    @Override
    public void onPause() {
        super.onPause();
        subscription.unsubscribe();
    }
}
