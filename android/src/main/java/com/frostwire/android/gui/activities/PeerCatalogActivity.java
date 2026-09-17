/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.android.gui.activities;

import static com.frostwire.android.util.SystemUtils.postToHandler;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils.HandlerThreadName;
import com.frostwire.bittorrent.DefaultTrackers;
import com.frostwire.search.relay.RemoteIndexFetcher.RemoteTorrentEntry;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Shows the shared-torrent catalog of a peer that opted in to being browsable
 * (Distributed search result magnet flag {@code x.hc=1}). The catalog is
 * fetched over the IceBridge mesh with the same signed request the desktop
 * relay exposes as {@code GET /catalog}; tapping an entry starts a transfer
 * whose metadata is fetched from that same holder over the mesh.
 *
 * @author gubatron
 */
public class PeerCatalogActivity extends AbstractActivity {

    public static final String EXTRA_PEER_PUB = "peer_pub";

    private static final Logger LOG = Logger.getLogger(PeerCatalogActivity.class);
    private static final int BROWSE_TIMEOUT_MS = 8_000;

    private ListView list;
    private TextView emptyView;
    private ProgressBar progress;

    public PeerCatalogActivity() {
        super(R.layout.activity_peer_catalog);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        super.initComponents(savedInstanceState);
        setTitle(R.string.peer_catalog_title);
        list = findView(R.id.peer_catalog_list);
        emptyView = findView(R.id.peer_catalog_empty);
        progress = findView(R.id.peer_catalog_progress);

        byte[] peerPub = decodePeerPub(getIntent().getStringExtra(EXTRA_PEER_PUB));
        if (peerPub == null) {
            showEmpty();
            return;
        }
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            if (item instanceof RemoteTorrentEntry) {
                startDownload((RemoteTorrentEntry) item, peerPub);
            }
        });
        loadCatalog(peerPub);
    }

    private static byte[] decodePeerPub(String pubBase64Url) {
        if (pubBase64Url == null || pubBase64Url.isEmpty()) {
            return null;
        }
        try {
            byte[] pub = Base64.getUrlDecoder().decode(pubBase64Url);
            return pub.length == 32 ? pub : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void loadCatalog(byte[] peerPub) {
        final AndroidRelayStack stack = AndroidRelayStack.live();
        Engine.instance().getThreadPool().execute(() -> {
            List<RemoteTorrentEntry> entries = (stack == null)
                    ? Collections.emptyList()
                    : stack.browseCatalog(peerPub, BROWSE_TIMEOUT_MS);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                populate(entries);
            });
        });
    }

    private void populate(List<RemoteTorrentEntry> entries) {
        progress.setVisibility(View.GONE);
        if (entries == null || entries.isEmpty()) {
            showEmpty();
            return;
        }
        emptyView.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        list.setAdapter(new PeerCatalogAdapter(entries));
    }

    private void showEmpty() {
        progress.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void startDownload(RemoteTorrentEntry entry, byte[] peerPub) {
        String infoHashHex = entry.infoHashHex();
        if (infoHashHex == null || infoHashHex.length() != 40) {
            return;
        }
        String magnet = UrlUtils.buildMagnetUrl(infoHashHex, entry.name(), DefaultTrackers.MAGNET_URL_PARAMETERS)
                + "&x.hp=" + Base64.getUrlEncoder().withoutPadding().encodeToString(peerPub);
        postToHandler(HandlerThreadName.DOWNLOADER, () -> {
            try {
                TransferManager tm = TransferManager.instance();
                BittorrentDownload transfer = tm.downloadTorrent(magnet, null, entry.name());
                runOnUiThread(() -> onDownloadStarted(transfer));
            } catch (Throwable t) {
                LOG.error("PeerCatalogActivity: could not start download " + infoHashHex, t);
            }
        });
    }

    private void onDownloadStarted(BittorrentDownload transfer) {
        if (transfer == null || isFinishing() || isDestroyed()) {
            return;
        }
        TransferManager tm = TransferManager.instance();
        if (tm.isBittorrentDownloadAndMobileDataSavingsOn(transfer)) {
            UIUtils.showLongMessage(this, R.string.torrent_transfer_enqueued_on_mobile_data);
            transfer.pause();
        } else {
            if (tm.isBittorrentDownloadAndMobileDataSavingsOff(transfer)) {
                UIUtils.showLongMessage(this, R.string.torrent_transfer_consuming_mobile_data);
            }
            UIUtils.showShortMessage(this, R.string.download_added_to_queue);
            UIUtils.showTransfersOnDownloadStart(this);
        }
    }

    private class PeerCatalogAdapter extends BaseAdapter {
        private final List<RemoteTorrentEntry> entries;

        PeerCatalogAdapter(List<RemoteTorrentEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.view_peer_catalog_item, parent, false);
            }
            RemoteTorrentEntry entry = entries.get(position);
            TextView name = view.findViewById(R.id.peer_catalog_item_name);
            TextView details = view.findViewById(R.id.peer_catalog_item_details);
            name.setText(entry.name());
            details.setText(UIUtils.getBytesInHuman(entry.sizeBytes())
                    + " · " + entry.fileCount() + " " + getString(R.string.files));
            return view;
        }
    }
}
