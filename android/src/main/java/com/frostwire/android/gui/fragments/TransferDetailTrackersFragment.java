/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import androidx.fragment.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.gui.views.EditTextDialog;
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailTrackersFragment extends AbstractTransferDetailFragment {
    private static final Logger LOG = Logger.getLogger(TransferDetailTrackersFragment.class);
    public TransferDetailTrackersFragment() {
        super(R.layout.fragment_transfer_detail_trackers);
    }
    private TextView dhtStatus;
    private TextView lsdStatus;
    private RecyclerView recyclerView;
    private Button addTrackerButton;

    private TrackerListAdapter adapter;
    private AddTrackerButtonClickListener addTrackerButtonClickListener;
    private static final Pattern validTrackerUrlPattern = Pattern.compile("^(https?|udp)://[-a-zA-Z0-9+&@#/%?=~_|!:,\\.;]*[-a-zA-Z0-9+&@#/%=~_|]");

    @Override
    public void onResume() {
        super.onResume();
        if (uiBittorrentDownload == null) {
            return;
        }
        if (adapter == null && isAdded()) {
            adapter = new TrackerListAdapter(uiBittorrentDownload, getParentFragmentManager());
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
            adapter.refreshFromTorrentHandle();
        }
        boolean announcingToDht = uiBittorrentDownload.getDl().isAnnouncingToDht();
        boolean announcingToLSD = uiBittorrentDownload.getDl().isAnnouncingToLsd();

        dhtStatus.setText(announcingToDht ? R.string.working : R.string.disabled);
        lsdStatus.setText(announcingToLSD ? R.string.working : R.string.disabled);
    }

    private static final class AddTrackerButtonClickListener implements View.OnClickListener, EditTextDialog.TextViewInputDialogCallback {
        private final TorrentHandle torrentHandle;
        private final WeakReference<TrackerListAdapter> adapterRef;
        private WeakReference<View> clickedViewRef;

        AddTrackerButtonClickListener(TorrentHandle torrentHandle, TrackerListAdapter adapter) {
            this.torrentHandle = torrentHandle;
            adapterRef = Ref.weak(adapter);
        }

        @Override
        public void onClick(View v) {
            clickedViewRef = Ref.weak(v);
            if (Ref.alive(adapterRef)) {
                TrackerListAdapter adapter = adapterRef.get();
                if (adapter != null && Ref.alive(adapter.fragmentManagerRef)) {
                    FragmentManager fm = adapter.fragmentManagerRef.get();
                    if (fm != null) {
                        UIUtils.showEditTextDialog(fm,
                                R.string.enter_valid_tracker_url_here,
                                R.string.add_tracker,
                                R.string.add,
                                true,
                                false,
                                null,
                                this);
                    }
                }
            }
        }

        @Override
        public void onDialogSubmitted(String value, boolean cancelled) {
            if (!cancelled && torrentHandle != null && value != null) {
                value = value.trim();
                Matcher matcher = validTrackerUrlPattern.matcher(value);
                if (matcher.matches()) {
                    torrentHandle.addTracker(new AnnounceEntry(value));
                    torrentHandle.saveResumeData();
                    torrentHandle.forceReannounce();
                    if (Ref.alive(adapterRef)) {
                        adapterRef.get().refreshFromTorrentHandle();
                    }
                } else if (Ref.alive(clickedViewRef)) {
                    UIUtils.showShortMessage(clickedViewRef.get(), R.string.invalid_tracker_url);
                    Ref.free(clickedViewRef);
                }
            }
        }
    }

    private static final class TrackerItemViewHolder extends RecyclerView.ViewHolder {
        private final WeakReference<TrackerListAdapter> adapterRef;
        private final TextView trackerTextView;
        private final ImageView editButton;
        private final ImageView removeButton;
        private final TorrentHandle torrentHandle;
        private int trackerOffset;
        private String trackerUrl;

        public TrackerItemViewHolder(final View itemView,
                                     final TrackerListAdapter adapter,
                                     final TorrentHandle torrentHandle) {
            super(itemView);
            adapterRef = Ref.weak(adapter);
            trackerTextView = itemView.findViewById(R.id.view_transfer_detail_tracker_address);
            editButton = itemView.findViewById(R.id.view_transfer_detail_tracker_edit_button);
            removeButton = itemView.findViewById(R.id.view_transfer_detail_tracker_remove_button);
            this.torrentHandle = torrentHandle;
            editButton.setOnClickListener(new OnEditTrackerClicked(this));
            removeButton.setOnClickListener(new OnRemoveTrackerClicked(this));
        }

        public void updateData(AnnounceEntry entry, int trackerOffset) {
            trackerTextView.setText(entry.url());
            this.trackerOffset = trackerOffset;
            this.trackerUrl = entry.url();
        }

        public FragmentManager getFragmentManager() {
            if (!Ref.alive(adapterRef)) {
                return null;
            }
            if (!Ref.alive(adapterRef.get().fragmentManagerRef)) {
                return null;
            }
            return adapterRef.get().fragmentManagerRef.get();
        }

        private static final class OnEditTrackerClicked implements View.OnClickListener, EditTextDialog.TextViewInputDialogCallback {

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
                FragmentManager fm;
                if (Ref.alive(vhRef) && (fm = vhRef.get().getFragmentManager()) != null) {
                    UIUtils.showEditTextDialog(fm,
                            R.string.enter_valid_tracker_url_here,
                            R.string.edit_tracker,
                            R.string.edit,
                            true,
                            false,
                            selectedTrackerURL,
                            this);

                }
            }

            @Override
            public void onDialogSubmitted(String value, boolean cancelled) {
                if (!Ref.alive(vhRef)) {
                    return;
                }
                TrackerItemViewHolder trackerViewHolder = vhRef.get();
                if (!cancelled && value != null) {
                    value = value.trim();
                    Matcher matcher = validTrackerUrlPattern.matcher(value);
                    if (matcher.matches()) {
                        AnnounceEntry newTracker = new AnnounceEntry(value);
                        TorrentHandle th = trackerViewHolder.torrentHandle;
                        List<AnnounceEntry> originalTrackers = th.trackers();
                        int trackerIndex = trackerViewHolder.findCurrentTrackerIndex();
                        if (trackerIndex < 0 || trackerIndex >= originalTrackers.size()) {
                            return;
                        }
                        originalTrackers.set(trackerIndex, newTracker);
                        th.replaceTrackers(originalTrackers);
                        th.saveResumeData();
                        th.forceReannounce();
                        if (Ref.alive(trackerViewHolder.adapterRef)) {
                            trackerViewHolder.adapterRef.get().refreshFromTorrentHandle();
                        }
                    } else {
                        UIUtils.showShortMessage(vhRef.get().itemView, R.string.invalid_tracker_url);
                    }
                }
            }

            private String getSelectedTrackerURL() {
                if (!Ref.alive(vhRef)) {
                    return null;
                }
                TrackerItemViewHolder viewHolder = vhRef.get();
                AnnounceEntry selectedTracker = viewHolder.getSelectedTracker();
                if (selectedTracker == null) {
                    return null;
                }
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
                FragmentManager fm;
                if (!Ref.alive(vhRef) || (fm = vhRef.get().getFragmentManager()) == null) {
                    return;
                }
                TrackerItemViewHolder viewHolder = vhRef.get();
                AnnounceEntry trackerToRemove = viewHolder.getSelectedTracker();
                if (trackerToRemove != null) {
                    UIUtils.showYesNoDialog(fm,
                            trackerToRemove.url(),
                            R.string.remove_tracker,
                            (dialog, which) -> {
                                if (Ref.alive(vhRef)) {
                                    TrackerItemViewHolder latestViewHolder = vhRef.get();
                                    List<AnnounceEntry> trackers1 = latestViewHolder.torrentHandle.trackers();
                                    int trackerIndex = latestViewHolder.findCurrentTrackerIndex();
                                    if (trackerIndex < 0 || trackerIndex >= trackers1.size()) {
                                        return;
                                    }
                                    trackers1.remove(trackerIndex);
                                    latestViewHolder.torrentHandle.replaceTrackers(trackers1);
                                    latestViewHolder.torrentHandle.saveResumeData();
                                    latestViewHolder.torrentHandle.forceReannounce();
                                    if (Ref.alive(latestViewHolder.adapterRef)) {
                                        latestViewHolder.adapterRef.get().refreshFromTorrentHandle();
                                    }
                                }
                            });
                }
            }
        }

        private AnnounceEntry getSelectedTracker() {
            List<AnnounceEntry> trackers = torrentHandle.trackers();
            int trackerIndex = findCurrentTrackerIndex(trackers);
            if (trackerIndex < 0 || trackerIndex >= trackers.size()) {
                return null;
            }
            return trackers.get(trackerIndex);
        }

        private int findCurrentTrackerIndex() {
            return findCurrentTrackerIndex(torrentHandle.trackers());
        }

        private int findCurrentTrackerIndex(List<AnnounceEntry> trackers) {
            if (trackers == null || trackers.isEmpty()) {
                return -1;
            }
            if (trackerUrl != null) {
                for (int i = 0; i < trackers.size(); i++) {
                    AnnounceEntry entry = trackers.get(i);
                    if (trackerUrl.equals(entry.url())) {
                        return i;
                    }
                }
            }
            return trackerOffset >= 0 && trackerOffset < trackers.size() ? trackerOffset : -1;
        }
    }

    private static final class TrackerListAdapter extends ListAdapter<AnnounceEntry, TrackerItemViewHolder> {
        private final UIBittorrentDownload uiBittorrentDownload;
        private final WeakReference<FragmentManager> fragmentManagerRef;

        public TrackerListAdapter(UIBittorrentDownload uiBittorrentDownload, FragmentManager fragmentManager) {
            super(new TrackerItemCallback());
            this.uiBittorrentDownload = uiBittorrentDownload;
            this.fragmentManagerRef = Ref.weak(fragmentManager);
        }

        public void refreshFromTorrentHandle() {
            TorrentHandle th = uiBittorrentDownload.getDl().getTorrentHandle();
            List<AnnounceEntry> trackers = th != null ? th.trackers() : null;
            submitList(trackers != null ? new ArrayList<>(trackers) : new ArrayList<>());
        }

        @Override
        public TrackerItemViewHolder onCreateViewHolder(ViewGroup parent, int i) {
            View inflatedView = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_detail_tracker_item, parent, false);
            return new TrackerItemViewHolder(inflatedView, this, uiBittorrentDownload.getDl().getTorrentHandle());
        }

        @Override
        public void onBindViewHolder(TrackerItemViewHolder trackerItemViewHolder, int i) {
            AnnounceEntry entry = getItem(i);
            trackerItemViewHolder.updateData(entry, i);
        }
    }

    private static final class TrackerItemCallback extends DiffUtil.ItemCallback<AnnounceEntry> {
        @Override
        public boolean areItemsTheSame(AnnounceEntry oldItem, AnnounceEntry newItem) {
            return oldItem.url().equals(newItem.url());
        }

        @Override
        public boolean areContentsTheSame(AnnounceEntry oldItem, AnnounceEntry newItem) {
            return true;
        }
    }
}
