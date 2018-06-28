package com.frostwire.gui.components.transfers;

import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.peer_info;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import java.util.Locale;


public class TransferDetailPeersDataLine extends AbstractDataLine<TransferDetailPeers.PeerItemHolder> {

    private static final int IP_COLUMN_ID = 0;
    private static final int CLIENT_COLUMN_ID = 1;
    private static final int FLAGS_COLUMN_ID = 2;
    private static final int PERCENTAGE_COLUMN_ID = 3;
    private static final int DOWN_SPEED_COLUMN_ID = 4;
    private static final int UP_SPEED_COLUMN_ID = 5;

    private static LimeTableColumn[] columns = new LimeTableColumn[]{
            new LimeTableColumn(IP_COLUMN_ID, "IP", I18n.tr("IP"), 300, true, true, false, String.class),
            new LimeTableColumn(CLIENT_COLUMN_ID, "CLIENT", I18n.tr("Client"), 300, true, true, false, String.class),
            new LimeTableColumn(FLAGS_COLUMN_ID, "FLAGS", I18n.tr("Flags"), 60, true, true, false, String.class),
            new LimeTableColumn(PERCENTAGE_COLUMN_ID, "PERCENTAGE", "%", 60, true, true, false, String.class),
            new LimeTableColumn(DOWN_SPEED_COLUMN_ID, "DOWN_SPEED", I18n.tr("Down Speed"), 100, true, true, false, String.class),
            new LimeTableColumn(UP_SPEED_COLUMN_ID, "UP_SPEED", I18n.tr("Up Speed"), 100, true, true, false, String.class),
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
        peer_info peer = holder.peerItem.swig();

        if (holder == null) {
            return null;
        }

        switch (col) {
            case IP_COLUMN_ID:
                String address = peer.getIp().address().to_string(new error_code());
                int port = peer.getLocal_endpoint().port();
                String[] connectionTypes = {"bt", "uTP", "web_seed", "http_seed"};
                return connectionTypes[peer.getConnection_type()] + "://" + address + ":" + port;
            case CLIENT_COLUMN_ID:
                String client = peer.getClient();
                if (client == null || client.isEmpty()) {
                    client = I18n.tr("Unknown");
                }
                return client;
            case FLAGS_COLUMN_ID:
                return peer.get_flags();
            case PERCENTAGE_COLUMN_ID:
                return getBytesInHuman(holder.peerItem.totalDownload());
            case DOWN_SPEED_COLUMN_ID:
                return Integer.toString(peer.getDown_speed());
            case UP_SPEED_COLUMN_ID:
                return Integer.toString(peer.getUp_speed());
        }
        return null;
    }

    // TODO: put this method on GUIUtils
    public static String getBytesInHuman(long size) {
        final String[] BYTE_UNITS = new String[]{"b", "KB", "Mb", "Gb", "Tb"};
        int i;
        float sizeFloat = (float) size;
        for (i = 0; sizeFloat > 1024; i++) {
            sizeFloat /= 1024f;
        }
        return String.format(Locale.US, "%.2f %s", sizeFloat, BYTE_UNITS[i]);
    }

    @Override
    public int getTypeAheadColumn() {
        return 0;
    }
}
