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

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailTrackersFragment extends AbstractTransferDetailFragment {
    public TransferDetailTrackersFragment() {
        super(R.layout.fragment_transfer_detail_trackers);
    }

    private TextView dhtStatus;
    private TextView lsdStatus;
    private RecyclerView recyclerView;
    private Button addTrackerButton;

    private TrackerRecyclerViewAdapter adapter;
    private AddTrackerButtonClickListener addTrackerButtonClickListener;

    @Override
    public void onResume() {
        super.onResume();
        if (uiBittorrentDownload == null) {
            return;
        }
        if (adapter == null && isAdded()) {
            adapter = new TrackerRecyclerViewAdapter(uiBittorrentDownload);
        }
        //ensureComponentsReferenced();
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(adapter);
        }
        if (addTrackerButtonClickListener == null && adapter != null) {
            TorrentHandle torrentHandle = uiBittorrentDownload.getDl().getTorrentHandle();
            addTrackerButtonClickListener = new AddTrackerButtonClickListener(torrentHandle, adapter);
            addTrackerButton.setOnClickListener(addTrackerButtonClickListener);
        }
        onTime();
    }

    @Override
    protected int getTabTitleStringId() {
        return R.string.trackers;
    }

    @Override
    public void ensureComponentsReferenced(View rootView) {
        dhtStatus = findView(rootView, R.id.fragment_transfer_detail_trackers_dht_status);
        lsdStatus = findView(rootView, R.id.fragment_transfer_detail_trackers_lsd_status);
        recyclerView = findView(rootView, R.id.fragment_transfer_detail_trackers_address_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        addTrackerButton = findView(rootView, R.id.fragment_transfer_detail_trackers_add_tracker_button);
    }

    @Override
    protected void updateComponents() {
        if (uiBittorrentDownload == null) {
            return;
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        TorrentStatus status = uiBittorrentDownload.getDl().getTorrentHandle().status();
        boolean announcingToDht = status.announcingToDht();
        boolean announcingToLSD = status.announcingToLsd();

        dhtStatus.setText(announcingToDht ? R.string.working : R.string.disabled);
        lsdStatus.setText(announcingToLSD ? R.string.working : R.string.disabled);
    }

    private static final class AddTrackerButtonClickListener implements View.OnClickListener, UIUtils.TextViewInputDialogCallback {
        private final TorrentHandle torrentHandle;
        private final WeakReference<TrackerRecyclerViewAdapter> adapterRef;


        AddTrackerButtonClickListener(TorrentHandle torrentHandle, TrackerRecyclerViewAdapter adapter) {
            this.torrentHandle = torrentHandle;
            adapterRef = Ref.weak(adapter);
        }

        @Override
        public void onClick(View v) {
            UIUtils.showEditTextDialog(v.getContext(),
                    R.drawable.contextmenu_icon_seed,
                    R.string.enter_valid_tracker_url_here,
                    R.string.add_tracker,
                    R.string.add,
                    false,
                    null,
                    this);
        }

        @Override
        public void onDialogSubmitted(String value, boolean cancelled) {
            if (!cancelled && torrentHandle != null && value != null) {
                value = value.trim();
                if (value.startsWith("udp://") || value.startsWith("http://") || value.startsWith("https://")) {
                    torrentHandle.addTracker(new AnnounceEntry(value));
                    torrentHandle.saveResumeData();
                    torrentHandle.forceReannounce();
                    if (Ref.alive(adapterRef)) {
                        adapterRef.get().notifyDataSetChanged();
                    }
                }
            }
        }
    }

    private static final class TrackerItemViewHolder extends RecyclerView.ViewHolder {
        private final WeakReference<TrackerRecyclerViewAdapter> adapterRef; // so we can notify it when we've changed its underlying data
        private final TextView trackerTextView;
        private final ImageView editButton;
        private final ImageView removeButton;
        private final TorrentHandle torrentHandle;
        private int trackerOffset;

        public TrackerItemViewHolder(final View itemView,
                                     final TrackerRecyclerViewAdapter adapter,
                                     final TorrentHandle torrentHandle,
                                     int trackerOffset) {
            super(itemView);
            adapterRef = Ref.weak(adapter);
            trackerTextView = itemView.findViewById(R.id.view_transfer_detail_tracker_address);
            editButton = itemView.findViewById(R.id.view_transfer_detail_tracker_edit_button);
            removeButton = itemView.findViewById(R.id.view_transfer_detail_tracker_remove_button);
            this.torrentHandle = torrentHandle;
            this.trackerOffset = trackerOffset;
            editButton.setOnClickListener(new OnEditTrackerClicked(this));
            removeButton.setOnClickListener(new OnRemoveTrackerClicked(this));
        }

        public void updateData(int trackerOffset) {
            List<AnnounceEntry> trackers = torrentHandle.trackers();
            if (trackers == null || trackers.isEmpty() || trackerOffset >= trackers.size()) {
                return;
            }
            AnnounceEntry trackerEntry = trackers.get(trackerOffset);
            trackerTextView.setText(trackerEntry.url());
            this.trackerOffset = trackerOffset;
        }

        private static final class OnEditTrackerClicked implements View.OnClickListener, UIUtils.TextViewInputDialogCallback {

            private final WeakReference<TrackerItemViewHolder> vhRef;

            OnEditTrackerClicked(TrackerItemViewHolder vh) {
                vhRef = Ref.weak(vh);
            }

            @Override
            public void onClick(View v) {
                String selectedTrackerURL = getSelectedTrackerURL();
                if (selectedTrackerURL == null) {
                    return;
                }
                UIUtils.showEditTextDialog(v.getContext(),
                        R.drawable.contextmenu_icon_rename,
                        R.string.enter_valid_tracker_url_here,
                        R.string.edit_tracker,
                        R.string.edit,
                        false,
                        selectedTrackerURL,
                        this);
            }

            @Override
            public void onDialogSubmitted(String value, boolean cancelled) {
                if (!Ref.alive(vhRef)) {
                    return;
                }
                TrackerItemViewHolder trackerViewHolder = vhRef.get();
                if (!cancelled && value != null) {
                    value = value.trim();
                    if (value.startsWith("udp://") || value.startsWith("http://") || value.startsWith("https://")) {
                        AnnounceEntry newTracker = new AnnounceEntry(value);
                        TorrentHandle th = trackerViewHolder.torrentHandle;
                        List<AnnounceEntry> originalTrackers = th.trackers();
                        originalTrackers.set(trackerViewHolder.trackerOffset, newTracker);
                        th.replaceTrackers(originalTrackers);
                        th.saveResumeData();
                        th.forceReannounce();
                        if (Ref.alive(trackerViewHolder.adapterRef)) {
                            trackerViewHolder.adapterRef.get().notifyDataSetChanged();
                        }
                    }
                }
            }

            private String getSelectedTrackerURL() {
                if (!Ref.alive(vhRef)) {
                    return null;
                }
                TrackerItemViewHolder viewHolder = vhRef.get();
                int trackerOffset = viewHolder.trackerOffset;
                List<AnnounceEntry> trackers = viewHolder.torrentHandle.trackers();
                AnnounceEntry selectedTracker = trackers.get(trackerOffset);
                return selectedTracker.url();
            }
        }

        private static final class OnRemoveTrackerClicked implements View.OnClickListener {
            private final WeakReference<TrackerItemViewHolder> vhRef;

            OnRemoveTrackerClicked(TrackerItemViewHolder vh) {
                vhRef = Ref.weak(vh);
            }

            @Override
            public void onClick(View v) {
                if (!Ref.alive(vhRef)) {
                    return;
                }
                int trackerOffset = vhRef.get().trackerOffset;
                List<AnnounceEntry> trackers = vhRef.get().torrentHandle.trackers();
                AnnounceEntry trackerToRemove = trackers.get(trackerOffset);
                UIUtils.showYesNoDialog(v.getContext(),
                        R.drawable.contextmenu_icon_seed,
                        trackerToRemove.url(),
                        R.string.remove_tracker,
                        (dialog, which) -> {
                            TrackerItemViewHolder viewHolder = vhRef.get();
                            List<AnnounceEntry> trackers1 = viewHolder.torrentHandle.trackers();
                            trackers1.remove(viewHolder.trackerOffset);
                            viewHolder.torrentHandle.replaceTrackers(trackers1);
                            viewHolder.torrentHandle.saveResumeData();
                            viewHolder.torrentHandle.forceReannounce();
                            if (Ref.alive(viewHolder.adapterRef)) {
                                viewHolder.adapterRef.get().notifyDataSetChanged();
                            }
                        });

            }
        }
    }

    private static final class TrackerRecyclerViewAdapter extends RecyclerView.Adapter<TrackerItemViewHolder> {
        private final UIBittorrentDownload uiBittorrentDownload;

        public TrackerRecyclerViewAdapter(final UIBittorrentDownload uiBittorrentDownload) {
            this.uiBittorrentDownload = uiBittorrentDownload;
        }

        @Override
        public TrackerItemViewHolder onCreateViewHolder(ViewGroup parent, int i) {
            View inflatedView = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_detail_tracker_item, parent, false);
            return new TrackerItemViewHolder(inflatedView, this, uiBittorrentDownload.getDl().getTorrentHandle(), i);
        }

        @Override
        public void onBindViewHolder(TrackerItemViewHolder trackerItemViewHolder, int i) {
            trackerItemViewHolder.updateData(i);
        }

        @Override
        public int getItemCount() {
            if (uiBittorrentDownload == null ||
                    uiBittorrentDownload.getDl() == null ||
                    uiBittorrentDownload.getDl().getTorrentHandle() == null) {
                return 0;
            }
            TorrentHandle torrentHandle = uiBittorrentDownload.getDl().getTorrentHandle();
            return torrentHandle.trackers() == null ? 0 : torrentHandle.trackers().size();
        }
    }
}
