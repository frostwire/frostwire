/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.components.transfers;

import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;

import javax.swing.*;

public class TransferDetailTrackersTableMediator extends
        AbstractTableMediator<TransferDetailTrackersModel, TransferDetailTrackersDataLine, TransferDetailTrackers.TrackerItemHolder> {
    TransferDetailTrackersTableMediator() {
        super("TRANSFER_DETAIL_TRACKERS_TABLE_MEDIATOR");
    }

    @Override
    protected void updateSplashScreen() {
    }

    @Override
    protected void setupConstants() {
        MAIN_PANEL = new PaddedPanel();
        DATA_MODEL = new TransferDetailTrackersModel();
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

