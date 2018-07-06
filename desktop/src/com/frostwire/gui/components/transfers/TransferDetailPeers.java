/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
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

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.peer_info;
import com.frostwire.jlibtorrent.swig.peer_info_vector;
import com.frostwire.jlibtorrent.swig.torrent_handle;
import com.frostwire.util.Logger;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class TransferDetailPeers extends JPanel implements TransferDetailComponent.TransferDetailPanel {

    private static final Logger LOG = Logger.getLogger(TransferDetailPeers.class);

    private final TransferDetailPeersTableMediator tableMediator;
    private BittorrentDownload btDownload;

    TransferDetailPeers() {
        super(new MigLayout("fillx, insets 0 0 0 0, gap 0 0"));
        tableMediator = new TransferDetailPeersTableMediator();
        add(tableMediator.getComponent(), "growx, growy");
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {
        if (btDownload != null && btDownload.getDl() != null) {
            if (this.btDownload != btDownload) {
                tableMediator.clearTable();
            }
            this.btDownload = btDownload;
            try {
                List<PeerInfoData> items = peerInfo(btDownload.getDl().getTorrentHandle().swig());
                if (items != null && items.size() > 0) {
                    if (tableMediator.getSize() == 0) {
                        int i = 0;
                        for (PeerInfoData item : items) {
                            tableMediator.add(new PeerItemHolder(i++, item));
                        }
                    } else {
                        int i = 0;
                        for (PeerInfoData item : items) {
                            try {
                                tableMediator.update(new PeerItemHolder(i++, item));
                            } catch (IndexOutOfBoundsException ignored) {
                                // peer might not be there anymore, reload table from scratch
                                tableMediator.clearTable();
                                updateData(btDownload);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                LOG.error("Error updating data: " + e.getMessage());
            }
        }
    }

    public class PeerItemHolder {
        final int peerOffset;
        final PeerInfoData peerItem;

        PeerItemHolder(int peerOffset, PeerInfoData peerItem) {
            this.peerOffset = peerOffset;
            this.peerItem = peerItem;
        }

        @Override
        public int hashCode() {
            return peerOffset;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PeerItemHolder && ((PeerItemHolder) obj).peerOffset == peerOffset;
        }
    }

    // TODO: fix jlibtorrent, review in android
    // see explanation in TransferDetailTrackers
    public static List<PeerInfoData> peerInfo(torrent_handle th) {
        if (!th.is_valid()) {
            return new ArrayList<>();
        }

        peer_info_vector v = new peer_info_vector();
        th.get_peer_info(v);

        int size = (int) v.size();
        ArrayList<PeerInfoData> l = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            l.add(new PeerInfoData(v.get(i)));
        }

        return l;
    }

    public static final class PeerInfoData {

        private final String ipAddress;
        private final int localEndpointPort;
        private final int connectionType;
        private final String client;
        private final int flags;
        private final byte source;
        private final long totalDownload;
        private final long totalUpload;
        private final float progress;
        private final int upSpeed;
        private final int downSpeed;

        public PeerInfoData(peer_info p) {
            error_code ec = new error_code();
            this.ipAddress = p.getIp().address().to_string(ec);
            this.localEndpointPort = p.getLocal_endpoint().port();
            this.connectionType = p.getConnection_type();
            this.client = Vectors.byte_vector2ascii(p.get_client());
            this.flags = p.get_flags();
            this.source = p.get_source();
            this.totalDownload = p.getTotal_download();
            this.totalUpload = p.getTotal_upload();
            this.progress = p.getProgress();
            this.upSpeed = p.getUp_speed();
            this.downSpeed = p.getDown_speed();
        }

        public String ipAddress() {
            return ipAddress;
        }

        public int localEndpointPort() {
            return localEndpointPort;
        }

        public int connectionType() {
            return connectionType;
        }

        public String client() {
            return client;
        }

        public int flags() {
            return flags;
        }

        public byte source() {
            return source;
        }

        public long totalDownload() {
            return totalDownload;
        }

        public long totalUpload() {
            return totalUpload;
        }

        public float progress() {
            return progress;
        }

        public int upSpeed() {
            return upSpeed;
        }

        public int downSpeed() {
            return downSpeed;
        }
    }
}
