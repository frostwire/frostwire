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

import android.content.res.Resources;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.jlibtorrent.PeerInfo;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.peer_info;

import java.util.ArrayList;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailPeersFragment extends AbstractTransferDetailFragment {

    private TextView peerNumberTextView;
    private RecyclerView recyclerView;
    private PeersAdapter adapter;

    public TransferDetailPeersFragment() {
        super(R.layout.fragment_transfer_detail_peers);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (uiBittorrentDownload == null) {
            return;
        }
        if (adapter == null && isAdded()) {
            ArrayList<PeerInfo> peerInfos = uiBittorrentDownload.getDl().getTorrentHandle().peerInfo();
            adapter = new PeersAdapter(peerInfos);
        }
        updateComponents();
    }

    @Override
    protected int getTabTitleStringId() {
        return R.string.peers;
    }

    @Override
    public void ensureComponentsReferenced(View rootView) {
        peerNumberTextView = findView(rootView, R.id.fragment_transfer_detail_peers_number);
        recyclerView = findView(rootView, R.id.fragment_transfer_detail_peers_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    }

    @Override
    protected void updateComponents() {
        if (uiBittorrentDownload == null) {
            return;
        }
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(adapter);
        }
        if (adapter != null) {
            ArrayList<PeerInfo> peerInfos = uiBittorrentDownload.getDl().getTorrentHandle().peerInfo();
            adapter.updatePeers(peerInfos);
            peerNumberTextView.setText(getString(R.string.n_peers, peerInfos.size()));
        }
    }

    private static final class PeerItemViewHolder extends RecyclerView.ViewHolder {
        @SuppressWarnings("unused")
        private int offset; // could be used for click listeners on peers, say to remove/throttle, copy ip:port
        private final TextView addressTextView;
        private final TextView rttTextView;
        private final TextView clientTextView;
        private final TextView downSpeedTextView;
        private final TextView upSpeedTextView;
        private final TextView sourceTypeTextView;
        private TextView downloadedTextView;
        private TextView uploadedTextView;

        enum PeerSourceType {
            tracker(0x1, R.string.tracker),
            dht(0x2, R.string.dht),
            pex(0x4, R.string.pex),
            lsd(0x8, R.string.lsd);

            private final int bitFlag;
            private final int stringId;

            PeerSourceType(int bitFlag, int stringId) {
                this.bitFlag = bitFlag;
                this.stringId = stringId;
            }

            public static int getSourceStringId(int sourceFlags) {
                int peerSourceStringId = -1;
                for (PeerSourceType ps_enum : PeerSourceType.values()) {
                    if ((sourceFlags & ps_enum.bitFlag) == ps_enum.bitFlag) {
                        peerSourceStringId = ps_enum.stringId;
                        break;
                    }
                }
                return peerSourceStringId;
            }
        }

        public PeerItemViewHolder(View v) {
            super(v);
            addressTextView = v.findViewById(R.id.view_transfer_detail_peer_item_address);
            rttTextView = v.findViewById(R.id.view_transfer_detail_peer_item_rtt);
            clientTextView = v.findViewById(R.id.view_transfer_detail_peer_item_client);
            downSpeedTextView = v.findViewById(R.id.view_transfer_detail_peer_item_down_speed);
            upSpeedTextView = v.findViewById(R.id.view_transfer_detail_peer_item_up_speed);
            sourceTypeTextView = v.findViewById(R.id.view_transfer_detail_peer_source_type);
            downloadedTextView = v.findViewById(R.id.view_transfer_detail_peer_downloaded);
            uploadedTextView = v.findViewById(R.id.view_transfer_detail_peer_uploaded);
            downloadedTextView = v.findViewById(R.id.view_transfer_detail_peer_downloaded);
            uploadedTextView = v.findViewById(R.id.view_transfer_detail_peer_uploaded);
        }

        public void updateData(PeerInfo peerInfo, int offset) {
            peer_info peer = peerInfo.swig();
            this.offset = offset;
            Resources r = itemView.getResources();

            String address = peer.getIp().address().to_string(new error_code());//peer.getLocal_endpoint().address().to_string(new error_code());
            int port = peer.getLocal_endpoint().port();

            String[] connectionTypes = {"bt", "uTP", "web_seed", "http_seed"};
            addressTextView.setText(connectionTypes[peer.getConnection_type()] + "://" + address + ":" + port);

            int rtt = peer.getRtt();
            rttTextView.setText(r.getString(R.string.rtt_ms, rtt));

            String client = peer.getClient();
            if (client == null || client.isEmpty()) {
                client = r.getString(R.string.unknown);
            }
            clientTextView.setText(client);

            downSpeedTextView.setText(UIUtils.getBytesInHuman(peer.getDown_speed()) + "/s");
            upSpeedTextView.setText(UIUtils.getBytesInHuman(peer.getUp_speed()) + "/s");

            int source = peer.getSource().to_int();
            int peerSourceStringId = PeerSourceType.getSourceStringId(source);
            if (peerSourceStringId != -1) {
                sourceTypeTextView.setText(r.getString(R.string.source_type, r.getString(peerSourceStringId)));
            }

            String totalDownloadedInHumanBytes = r.getString(R.string.downloaded_n, UIUtils.getBytesInHuman(peerInfo.totalDownload()));
            downloadedTextView.setText(totalDownloadedInHumanBytes);
            String totalUploadedInHumanBytes = r.getString(R.string.uploaded_n, UIUtils.getBytesInHuman(peerInfo.totalUpload()));
            uploadedTextView.setText(totalUploadedInHumanBytes);
        }
    }

    private static final class PeersAdapter extends RecyclerView.Adapter<PeerItemViewHolder> {

        private final ArrayList<PeerInfo> peers;

        public PeersAdapter(ArrayList<PeerInfo> peerInfos) {
            this.peers = new ArrayList<>(0);
            updatePeers(peerInfos);
        }

        public void updatePeers(ArrayList<PeerInfo> peerInfos) {
            this.peers.clear();
            if (peerInfos != null) {
                this.peers.addAll(peerInfos);
            }
            notifyDataSetChanged();
        }

        @Override
        public PeerItemViewHolder onCreateViewHolder(ViewGroup parent, int i) {
            View inflatedView = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_detail_peer_item, parent, false);
            return new PeerItemViewHolder(inflatedView);
        }

        @Override
        public void onBindViewHolder(PeerItemViewHolder peerItemViewHolder, int i) {
            if (peers != null && peers.size() > 0 && i >= 0) {
                peerItemViewHolder.updateData(peers.get(i), i);
            }
        }

        @Override
        public int getItemCount() {
            return peers.size();
        }
    }
}
