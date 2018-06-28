package com.frostwire.gui.components.transfers;

import com.limegroup.gnutella.gui.tables.BasicDataLineModel;

public class TransferDetailPeersModel extends
        BasicDataLineModel<TransferDetailPeersDataLine, TransferDetailPeers.PeerItemHolder>{

    public TransferDetailPeersModel() {
        super(TransferDetailPeersDataLine.class);
    }
}
