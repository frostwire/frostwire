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

