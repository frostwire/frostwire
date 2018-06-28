package com.frostwire.gui.components.transfers;

import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;

import javax.swing.*;

public class TransferDetailPeersTableMediator extends
        AbstractTableMediator<TransferDetailPeersModel, TransferDetailPeersDataLine, TransferDetailPeers.PeerItemHolder> {

    TransferDetailPeersTableMediator() {
        super("TRANSFER_DETAIL_PEERS_TABLE_MEDIATOR");
    }


    @Override
    protected void updateSplashScreen() {

    }

    @Override
    protected void setupConstants() {
        MAIN_PANEL = new PaddedPanel();
        DATA_MODEL = new TransferDetailPeersModel();
        TABLE = new LimeJTable(DATA_MODEL);
        TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    }

    @Override
    protected JPopupMenu createPopupMenu() {
        return null;
    }

    @Override
    public void handleActionKey() {

    }

    @Override
    public void handleSelection(int row) {

    }

    @Override
    public void handleNoSelection() {

    }
}
