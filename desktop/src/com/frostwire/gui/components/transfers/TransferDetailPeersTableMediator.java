/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.theme.SkinPopupMenu;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class TransferDetailPeersTableMediator extends
        AbstractTableMediator<TransferDetailPeersModel, TransferDetailPeersDataLine, TransferDetailPeers.PeerItemHolder> {
    TransferDetailPeersTableMediator() {
        super("TRANSFER_DETAIL_PEERS_TABLE_MEDIATOR");
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
        SkinPopupMenu menu = new SkinPopupMenu();
        TransferDetailPeersModel dataModel = getDataModel();
        TransferDetailPeersDataLine transferDetailPeersDataLine = dataModel.get(TABLE.getSelectedRow());

        if (transferDetailPeersDataLine != null && transferDetailPeersDataLine.getInitializeObject() != null) {
            TransferDetailPeers.PeerItemHolder peerItemHolder = transferDetailPeersDataLine.getInitializeObject();
            menu.add(new CopyBittorrentAddressAction(peerItemHolder));
            menu.add(new CopyIPAction(peerItemHolder));
            menu.add(new CopyIPPortAction(peerItemHolder));
        }

        return menu;
    }

    @Override
    protected void updateSplashScreen() {
    }

    private final static class CopyBittorrentAddressAction extends AbstractAction {
        private final TransferDetailPeers.PeerItemHolder peerItemHolder;

        public CopyBittorrentAddressAction(TransferDetailPeers.PeerItemHolder itemHolder) {
            peerItemHolder = itemHolder;
            putValue(Action.NAME, I18n.tr("Copy") + " " + I18n.tr("Peer's") + " " + I18n.tr("Address"));
            putValue(LimeAction.SHORT_NAME, getValue(Action.NAME));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator.setClipboardContent(TransferDetailPeersDataLine.getBittorrentAddress(peerItemHolder.peerItem));
        }
    }

    private final static class CopyIPAction extends AbstractAction {
        private final TransferDetailPeers.PeerItemHolder peerItemHolder;

        public CopyIPAction(TransferDetailPeers.PeerItemHolder itemHolder) {
            peerItemHolder = itemHolder;
            putValue(Action.NAME, I18n.tr("Copy") + " " + I18n.tr("Peer's") + " " + I18n.tr("IP"));
            putValue(LimeAction.SHORT_NAME, getValue(Action.NAME));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String ipPort = peerItemHolder.peerItem.ip();
            GUIMediator.setClipboardContent(ipPort.substring(0, ipPort.indexOf(":")));
        }
    }

    private final static class CopyIPPortAction extends AbstractAction {
        private final TransferDetailPeers.PeerItemHolder peerItemHolder;

        public CopyIPPortAction(TransferDetailPeers.PeerItemHolder itemHolder) {
            peerItemHolder = itemHolder;
            putValue(Action.NAME, I18n.tr("Copy") + " " + I18n.tr("Peer's") + " " + I18n.tr("IP") + ":" + I18n.tr("Port"));
            putValue(LimeAction.SHORT_NAME, getValue(Action.NAME));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator.setClipboardContent(peerItemHolder.peerItem.ip());
        }
    }
}