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

package com.frostwire.android.gui.views;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.TransferStateStrings;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.transfers.BittorrentDownload;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 10/10/17.
 */
public abstract class AbstractTransferDetailFragment extends AbstractFragment {

    private String infinity;
    private TransferStateStrings transferStateStrings;

    private String tabTitle;
    protected UIBittorrentDownload uiBittorrentDownload;
    protected TorrentHandle torrentHandle;
    private TextView detailProgressTitleTextView;
    private ProgressBar detailProgressProgressBar;
    private TextView detailProgressStatusTextView;
    private TextView detailProgressDownSpeedTextView;
    private TextView detailProgressUpSpeedTextView;

    public AbstractTransferDetailFragment(int layoutId) {
        super(layoutId);
        setHasOptionsMenu(true);
    }

    protected abstract int getTabTitleStringId();

    protected abstract void ensureComponentsReferenced(View rootView);

    protected abstract void updateComponents();

    public String getTabTitle() {
        return tabTitle;
    }

    public AbstractTransferDetailFragment init(final Activity activity, final SparseArray<String> tabTitles, final UIBittorrentDownload uiBittorrentDownload) {
        this.tabTitle = tabTitles.get(getTabTitleStringId());
        this.uiBittorrentDownload = uiBittorrentDownload;
        if (activity != null) {
            onAttach(activity);
        }
        ensureTorrentHandleAsync();
        return this;
    }

    @Override
    protected void initComponents(View rootView, Bundle savedInstanceState) {
        super.initComponents(rootView, savedInstanceState);

        infinity = rootView.getContext().getString(R.string.infinity);

        if (uiBittorrentDownload == null && savedInstanceState != null) {
            String infoHash = savedInstanceState.getString("infohash");
            async(this, AbstractTransferDetailFragment::recoverUIBittorrentDownload, infoHash);
        }
        ensureCommonComponentsReferenced(rootView);
        updateCommonComponents();
        ensureComponentsReferenced(rootView);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCommonComponents();
    }

    public void onTime() {
        if (uiBittorrentDownload == null) {
            Intent intent = getActivity().getIntent();
            if (intent != null) {
                String infoHash = intent.getStringExtra("infoHash");
                if (infoHash != null && !infoHash.isEmpty()) {
                    async(this, AbstractTransferDetailFragment::recoverUIBittorrentDownload, infoHash);
                }
            }
            if (uiBittorrentDownload == null) {
                return;
            }
        }
        updateCommonComponents();
        updateComponents();
    }

    // Fragment State serialization = onSaveInstanceState
    // Fragment State deserialization = onActivityCreated

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (uiBittorrentDownload != null) {
            outState.putString("infohash", uiBittorrentDownload.getInfoHash());
        }
    }

    private void updateCommonComponents() {
        if (uiBittorrentDownload == null) {
            return;
        }
        if (detailProgressTitleTextView != null) {
            detailProgressTitleTextView.setText(uiBittorrentDownload.getDisplayName());
        }
        if (detailProgressProgressBar != null) {
            detailProgressProgressBar.setProgress(uiBittorrentDownload.getProgress());
        }
        if (detailProgressStatusTextView != null) {
            if (transferStateStrings == null) {
                transferStateStrings = TransferStateStrings.getInstance(detailProgressStatusTextView.getContext());
            }
            detailProgressStatusTextView.setText(transferStateStrings.get(uiBittorrentDownload.getState()));
        }
        if (detailProgressDownSpeedTextView != null) {
            detailProgressDownSpeedTextView.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getDownloadSpeed()) + "/s");
        }
        if (detailProgressUpSpeedTextView != null) {
            detailProgressUpSpeedTextView.setText(UIUtils.getBytesInHuman(uiBittorrentDownload.getUploadSpeed()) + "/s");
        }
    }

    /**
     * This is a common section at the top of all the detail fragments
     * which contains the title of the transfer and the current progress
     *
     * @param rootView
     */
    private void ensureCommonComponentsReferenced(View rootView) {
        detailProgressTitleTextView = findView(rootView, R.id.view_transfer_detail_progress_title);
        detailProgressProgressBar = findView(rootView, R.id.view_transfer_detail_progress_progress);
        detailProgressStatusTextView = findView(rootView, R.id.view_transfer_detail_progress_status);
        detailProgressDownSpeedTextView = findView(rootView, R.id.view_transfer_detail_progress_down_speed);
        detailProgressUpSpeedTextView = findView(rootView, R.id.view_transfer_detail_progress_up_speed);
    }

    private void recoverUIBittorrentDownload(String infoHash) {
        if (infoHash != null) {
            BittorrentDownload bittorrentDownload = TransferManager.instance().getBittorrentDownload(infoHash);
            if (bittorrentDownload instanceof UIBittorrentDownload) {
                uiBittorrentDownload = (UIBittorrentDownload) bittorrentDownload;
                ensureTorrentHandleAsync();
            }
        }
    }

    protected void ensureTorrentHandleAsync() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            async(this, AbstractTransferDetailFragment::ensureTorrentHandle);
        } else {
            ensureTorrentHandle();
        }
    }

    private void ensureTorrentHandle() {
        if (torrentHandle == null && uiBittorrentDownload != null) {
            torrentHandle = uiBittorrentDownload.getDl().getTorrentHandle();
            if (torrentHandle == null) {
                torrentHandle = BTEngine.getInstance().find(new Sha1Hash(uiBittorrentDownload.getInfoHash()));
            }
        }
    }

    // All utility functions will be here for now

    /**
     * Converts a value in seconds to:
     * "d:hh:mm:ss" where d=days, hh=hours, mm=minutes, ss=seconds, or
     * "h:mm:ss" where h=hours<24, mm=minutes, ss=seconds, or
     * "m:ss" where m=minutes<60, ss=seconds
     */
    protected final String seconds2time(long seconds) {
        if (seconds == -1) {
            return infinity;
        }
        long minutes = seconds / 60;
        seconds = seconds - minutes * 60;
        long hours = minutes / 60;
        minutes = minutes - hours * 60;
        long days = hours / 24;
        hours = hours - days * 24;
        // build the numbers into a string
        StringBuilder time = new StringBuilder();
        if (days != 0) {
            time.append(Long.toString(days));
            time.append(":");
            if (hours < 10)
                time.append("0");
        }
        if (days != 0 || hours != 0) {
            time.append(Long.toString(hours));
            time.append(":");
            if (minutes < 10)
                time.append("0");
        }
        time.append(Long.toString(minutes));
        time.append(":");
        if (seconds < 10)
            time.append("0");
        time.append(Long.toString(seconds));
        return time.toString();
    }

    public static String getShareRatio(UIBittorrentDownload dl) {
        long sent = dl.getBytesSent();
        long received = dl.getBytesReceived();
        if (received < 0) {
            return "0%";
        }
        return String.valueOf(100 * ((float) sent / (float) received)) + "%";
    }
}
