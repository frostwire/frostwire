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

import com.frostwire.jlibtorrent.PeerInfo;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.SizeHolder;
import com.limegroup.gnutella.gui.tables.SpeedRenderer;

public final class TransferDetailPeersDataLine extends AbstractDataLine<TransferDetailPeers.PeerItemHolder> {
    private static final int IP_COLUMN_ID = 0;
    private static final int CLIENT_COLUMN_ID = 1;
    private static final int FLAGS_COLUMN_ID = 2;
    private static final int SOURCE_COLUMN_ID = 3;
    private static final int PROGRESS_COLUMN_ID = 4;
    private static final int DOWNLOADED_COLUMN_ID = 5;
    private static final int UPLOADED_COLUMN_ID = 6;
    private static final int DOWN_SPEED_COLUMN_ID = 7;
    private static final int UP_SPEED_COLUMN_ID = 8;
    // PEER SOURCE FLAGS
    private static final byte tracker = 1;
    private static final byte dht = 1 << 1;
    private static final byte pex = 1 << 2;
    private static final byte lsd = 1 << 3;
    private static final byte resume_data = 1 << 4;
    private static final byte incoming = 1 << 5;
    // PEER INFO FLAGS
    private static final int interesting = 1;
    private static final int choked = 1 << 1;
    private static final int remote_interested = 1 << 2;
    private static final int remote_choked = 1 << 3;
    //private static final int supports_extensions = 1 << 4;
    private static final int local_connection = 1 << 5;
    private static final int handshake = 1 << 6;
    //private static final int connecting = 1 << 7;
    //private static final int queued = 1 << 8;
    //private static final int on_parole = 1 << 9;
    //private static final int seed = 1 << 10;
    private static final int optimistic_unchoke = 1 << 11;
    private static final int snubbed = 1 << 12;
    //private static final int upload_only = 1 << 13;
    //private static final int endgame_mode = 1 << 14;
    //private static final int holepunched = 1 << 15;
    //private static final int i2p_socket = 1 << 16;
    private static final int utp_socket = 1 << 17;
    private static final int ssl_socket = 1 << 18;
    private static final int rc4_encrypted = 1 << 19;
    private static final int plaintext_encrypted = 1 << 20;
    private static final LimeTableColumn[] columns = new LimeTableColumn[]{
            new LimeTableColumn(IP_COLUMN_ID, "IP", I18n.tr("IP"), 180, true, true, true, String.class),
            new LimeTableColumn(CLIENT_COLUMN_ID, "CLIENT", I18n.tr("Client"), 120, true, true, true, String.class),
            new LimeTableColumn(FLAGS_COLUMN_ID, "FLAGS", I18n.tr("Flags"), 70, true, true, true, String.class),
            new LimeTableColumn(SOURCE_COLUMN_ID, "SOURCE", I18n.tr("Source"), 100, true, true, true, String.class),
            new LimeTableColumn(PROGRESS_COLUMN_ID, "PROGRESS", I18n.tr("Progress"), 100, true, true, false, String.class),
            new LimeTableColumn(DOWNLOADED_COLUMN_ID, "DOWNLOADED", I18n.tr("Downloaded"), 80, true, true, false, SizeHolder.class),
            new LimeTableColumn(UPLOADED_COLUMN_ID, "UPLOADED", I18n.tr("Uploaded"), 80, true, true, false, SizeHolder.class),
            new LimeTableColumn(DOWN_SPEED_COLUMN_ID, "DOWN_SPEED", I18n.tr("Down Speed"), 130, true, true, false, SpeedRenderer.class),
            new LimeTableColumn(UP_SPEED_COLUMN_ID, "UP_SPEED", I18n.tr("Up Speed"), 130, true, true, true, SpeedRenderer.class),
    };

    public TransferDetailPeersDataLine() {
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public LimeTableColumn getColumn(int col) {
        return columns[col];
    }

    @Override
    public boolean isDynamic(int col) {
        return false;
    }

    @Override
    public boolean isClippable(int col) {
        return false;
    }

    @Override
    public Object getValueAt(int col) {
        final TransferDetailPeers.PeerItemHolder holder = getInitializeObject();
        if (holder == null) {
            return null;
        }
        PeerInfo peer = holder.peerItem;
        switch (col) {
            case IP_COLUMN_ID:
                String address = peer.ip();
                return connectionTypeAsString(peer.connectionType(), peer.flags()) + "://" + address;
            case CLIENT_COLUMN_ID:
                String client = holder.peerItem.client();
                if (client == null || client.isEmpty()) {
                    client = I18n.tr("Unknown");
                }
                return client;
            case FLAGS_COLUMN_ID:
                return getFlagsAsString(peer.flags(), peer.source());
            case SOURCE_COLUMN_ID:
                return getSourceAsString(peer.source());
            case DOWNLOADED_COLUMN_ID:
                return new SizeHolder(holder.peerItem.totalDownload());
            case PROGRESS_COLUMN_ID:
                return 100 * peer.progress() + "%";
            case UPLOADED_COLUMN_ID:
                return holder.peerItem.totalUpload();
            case DOWN_SPEED_COLUMN_ID:
                return (double) peer.downSpeed();
            case UP_SPEED_COLUMN_ID:
                return (double) peer.upSpeed();
        }
        return null;
    }

    /**
     * tracker = 0_bit
     * dht = 1_bit
     * pex = 2_bit
     * lsd = 3_bit
     * resume_data = 4_bit
     * incoming = 5_bit
     */
    private String getSourceAsString(byte source) {
        StringBuilder sb = new StringBuilder();
        if ((source & tracker) == tracker) {
            sb.append("Tracker "); // purposefully not-translatable
        }
        if ((source & dht) == dht) {
            sb.append("DHT ");
        }
        if ((source & pex) == pex) {
            sb.append("PEX ");
        }
        if ((source & lsd) == lsd) {
            sb.append("LSD ");
        }
        if ((source & resume_data) == resume_data) {
            sb.append(I18n.tr("Resumed")).append(" ");
        }
        if ((source & incoming) == incoming) {
            sb.append(I18n.tr("Incoming")).append(" ");
        }
        return sb.toString();
    }

    /**
     * According to uTorrent's FAQ this is what their flag column means
     * D = Currently downloading (interested and not choked)
     * d = Your client wants to download, but peer doesn't want to send (interested and choked)
     * U = Currently uploading (interested and not choked)
     * u = Peer wants your client to upload, but your client doesn't want to (interested and choked)
     * O = Optimistic unchoke
     * S = Peer is snubbed
     * I = Peer is an incoming connection
     * K = Peer is unchoking your client, but your client is not interested
     * ? = Your client unchoked the peer but the peer is not interested
     * X = Peer was included in peerlists obtained through Peer Exchange (PEX) or an IPv6 peer told you its IPv4 address.
     * H = Peer was obtained through DHT.
     * E = Peer is using Protocol Encryption (all traffic)
     * e = Peer is using Protocol Encryption (handshake)
     * P = Peer is using uTorrent uTP
     * L = Peer is local (discovered through network broadcast, or in reserved local IP ranges)
     * <p>
     * And these are the flags we have in libtorrent (peer_info.hpp)
     * https://github.com/arvidn/libtorrent/blob/master/include/libtorrent/peer_info.hpp#L94
     * <p>
     * interesting = 0_bit (we are interested)
     * choked = 1_bit (we choke them)
     * remote_interested = 2_bit (they are interested)
     * remote_choked = 3_bit (they choked us)
     * supports_extensions = 4_bit
     * local_connection = 5_bit
     * handshake = 6_bit
     * connecting = 7_bit
     * queued = 8_bit
     * on_parole = 9_bit
     * seed = 10_bit
     * optimistic_unchoke = 11_bit
     * snubbed = 12_bit
     * upload_only = 13_bit
     * endgame_mode = 14_bit
     * holepunched = 15_bit
     * i2p_socket = 16_bit
     * utp_socket = 17_bit
     * ssl_socket = 18_bit
     * rc4_encrypted = 19_bit
     * plaintext_encrypted = 20_bit
     */
    private String getFlagsAsString(int flags, int sourceFlags) {
        StringBuilder sb = new StringBuilder();
        //D = Currently downloading (interested and not choked)
        if ((flags & interesting) == interesting && (flags & remote_choked) == 0) {
            sb.append("D");
        }
        //d = Your client wants to download, but peer doesn't want to send (interested and choked)
        if ((flags & interesting) == interesting && (flags & remote_choked) == remote_choked) {
            sb.append("d");
        }
        //U = Currently uploading (interested and not choked)
        if ((flags & remote_interested) == remote_interested && (flags & choked) == 0) {
            sb.append("U");
        }
        //u = Peer wants your client to upload, but your client doesn't want to (interested and choked)
        if ((flags & remote_interested) == remote_interested && (flags & choked) == choked) {
            sb.append("u");
        }
        //O = Optimistic unchoke
        if ((flags & optimistic_unchoke) == optimistic_unchoke) {
            sb.append("O");
        }
        //S = Peer is snubbed
        if ((flags & snubbed) == snubbed) {
            sb.append("S");
        }
        //I = Peer is an incoming connection
        //local_connection = If this flag is not set, this peer connection was opened by this peer connecting to us.
        if ((flags & local_connection) == 0) {
            sb.append("I");
        }
        //K = Peer is unchoking your client, but your client is not interested
        if ((flags & interesting) == 0 && (flags & remote_choked) == 0) {
            sb.append("K");
        }
        //? = Your client unchoked the peer but the peer is not interested
        if ((flags & remote_interested) == 0 && (flags & choked) == 0) {
            sb.append("?");
        }
        //X = Peer was included in peerlists obtained through Peer Exchange (PEX) or an IPv6 peer told you its IPv4 address.
        if ((sourceFlags & pex) == pex) {
            sb.append("X");
        }
        //H = Peer was obtained through DHT.
        if ((sourceFlags & dht) == dht) {
            sb.append("H");
        }
        //E = Peer is using Protocol Encryption (all traffic)
        if ((flags & plaintext_encrypted) == plaintext_encrypted ||
                (flags & rc4_encrypted) == rc4_encrypted ||
                (flags & ssl_socket) == ssl_socket) {
            sb.append("E");
        }
        //e = Peer is using Protocol Encryption (handshake)
        if ((flags & handshake) == handshake &&
                ((flags & plaintext_encrypted) == plaintext_encrypted ||
                        (flags & rc4_encrypted) == rc4_encrypted ||
                        (flags & ssl_socket) == ssl_socket)) {
            sb.append("e");
        }
        //P = Peer is using uTorrent uTP
        if ((flags & utp_socket) == utp_socket) {
            sb.append("P");
        }
        //L = Peer is local (discovered through network broadcast, or in reserved local IP ranges)
        // BUGGY FLAG: if ((flags & local_connection) == local_connection) {
        if ((sourceFlags & lsd) == lsd) {
            sb.append("L");
        }
        return sb.toString();
    }

    private String connectionTypeAsString(PeerInfo.ConnectionType t, int flags) {
        switch (t) {
            case WEB_SEED:
                return "web_seed";
            case HTTP_SEED:
                return "http_seed";
            default:
                return (flags & utp_socket) == utp_socket ? "uTP" : "bt";
        }
    }

    @Override
    public int getTypeAheadColumn() {
        return 0;
    }
}
