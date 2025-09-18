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

package com.frostwire.android.gui.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.TransferStateStrings;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.util.Logger;
import com.frostwire.util.TaskThrottle;

import java.text.MessageFormat;
import java.util.List;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 10/10/17.
 */
public abstract class AbstractTransferDetailFragment extends AbstractFragment {
    private Logger LOG = Logger.getLogger(AbstractTransferDetailFragment.class);
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
            onAttach((Context) activity);
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
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> recoverUIBittorrentDownload(infoHash));
        }
        ensureCommonComponentsReferenced(rootView);
        updateCommonComponents();
        ensureComponentsReferenced(rootView);
    }

    @Override
    public void onResume() {
        super.onResume();
        ensureTorrentHandle(); // Purposefully not async.
        updateCommonComponents();
    }

    public void onTime() {
        if (uiBittorrentDownload == null) {
            Intent intent = getActivity().getIntent();
            if (intent != null) {
                String infoHash = intent.getStringExtra("infoHash");
                if (infoHash != null && !infoHash.isEmpty()) {
                    if (TaskThrottle.isReadyToSubmitTask("AbstractTransferDetailFragment::recoverUIBittorrentDownload", 1000)) {
                        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> recoverUIBittorrentDownload(infoHash));
                    } else {
                        System.err.println("AbstractTransferDetailFragment.onTime() Did not submit async task AbstractTransferDetailFragment::recoverUIBittorrentDownload, 1000 ms haven't passed");
                    }
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
            detailProgressDownSpeedTextView.setText(MessageFormat.format("{0}/s", UIUtils.getBytesInHuman(uiBittorrentDownload.getDownloadSpeed())));
        }
        if (detailProgressUpSpeedTextView != null) {
            detailProgressUpSpeedTextView.setText(MessageFormat.format("{0}/s", UIUtils.getBytesInHuman(uiBittorrentDownload.getUploadSpeed())));
        }
    }

    /**
     * This is a common section at the top of all the detail fragments
     * which contains the title of the transfer and the current progress
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
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, this::ensureTorrentHandle);
        } else {
            ensureTorrentHandle();
        }
    }

    private void ensureTorrentHandle() {
        if (uiBittorrentDownload != null) {
            TorrentHandle currentTorrentHandle = BTEngine.getInstance().find(new Sha1Hash(uiBittorrentDownload.getInfoHash()));
            // If the user restarts an existing partial transfer from a torrent in My Files
            // this makes sure we refresh the UI torrent
            if (currentTorrentHandle != null && torrentHandle != currentTorrentHandle) {
                torrentHandle = currentTorrentHandle;
                uiBittorrentDownload = (UIBittorrentDownload) TransferManager.instance().getBittorrentDownload(uiBittorrentDownload.getInfoHash());
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
            time.append(days);
            time.append(":");
            if (hours < 10)
                time.append("0");
        }
        if (days != 0 || hours != 0) {
            time.append(hours);
            time.append(":");
            if (minutes < 10)
                time.append("0");
        }
        time.append(minutes);
        time.append(":");
        if (seconds < 10)
            time.append("0");
        time.append(seconds);
        return time.toString();
    }

    public static String getShareRatio(UIBittorrentDownload dl) {
        long sent = dl.getBytesSent();
        long received = dl.getBytesReceived();
        if (received < 0) {
            return "0%";
        }
        return 100 * ((float) sent / (float) received) + "%";
    }

    public static  <T, TH extends RecyclerView.ViewHolder> void updateAdapterItems(
            RecyclerView.Adapter<TH> adapter,
            List<T> items,
            List<T> freshItems) {
        if (freshItems != null && !freshItems.isEmpty()) {
            if (items.isEmpty()) {
                items.addAll(freshItems);
                adapter.notifyDataSetChanged();
            } else {
                // Update existing items
                int maxSize = Math.min(items.size(), freshItems.size());
                for (int i = 0; i < maxSize; i++) {
                    try {
                        items.set(i, freshItems.get(i));
                        adapter.notifyItemChanged(i);
                    } catch (Throwable ignored) {}
                }
                if (items.size() < freshItems.size()) {
                    // New list is bigger, add new elements
                    int sizeDifference = freshItems.size() - items.size();
                    int start = items.size();
                    int end = freshItems.size();
                    for (int i = start; i < end; i++) {
                        items.add(freshItems.get(i));
                        adapter.notifyItemInserted(i);
                    }
                } else if (freshItems.size() < items.size()) {
                    // New list is smaller, shorten our list to match new list size
                    while ((items.size() - freshItems.size()) > 0) {
                        items.remove(items.size() - 1);
                        adapter.notifyItemRangeRemoved(items.size()-1,1);
                    }
                }
            }
        } else {
            items.clear();
            adapter.notifyDataSetChanged();
        }
    }
}
