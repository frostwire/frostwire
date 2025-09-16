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

import com.frostwire.gui.bittorrent.TransferDetailFilesActionsRenderer;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.search.GenericCellEditor;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TransferDetailFilesTableMediator extends
    AbstractTableMediator<TransferDetailFilesModel, TransferDetailFilesDataLine, TransferDetailFiles.TransferItemHolder> {
    TransferDetailFilesTableMediator() {
        super("TRANSFER_DETAIL_FILES_TABLE_MEDIATOR");
    }

    @Override
    protected void updateSplashScreen() {
    }

    List<TransferDetailFilesDataLine> getSelectedLines() {
        int[] selected = TABLE.getSelectedRows();
        List<TransferDetailFilesDataLine> lines = new ArrayList<>(selected.length);
        for (int aSelected : selected) {
            lines.add(DATA_MODEL.get(aSelected));
        }
        return lines;
    }

    @Override
    protected void setupConstants() {
        MAIN_PANEL = new PaddedPanel();
        DATA_MODEL = new TransferDetailFilesModel();
        TABLE = new LimeJTable(DATA_MODEL);
        TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        DATA_MODEL.sort(1); // by file #
    }

    @Override
    protected JPopupMenu createPopupMenu() {
        TransferDetailFilesModel dataModel = getDataModel();
        TransferDetailFilesDataLine transferDetailFilesDataLine = dataModel.get(TABLE.getSelectedRow());
        TransferDetailFiles.TransferItemHolder transferItemHolder = transferDetailFilesDataLine.getInitializeObject();
        JPopupMenu menu = new SkinPopupMenu();
        menu.add(new TransferDetailFilesActionsRenderer.OpenInFolderAction(transferItemHolder));
        if (transferItemHolder.transferItem.isComplete()) {
            menu.add(new TransferDetailFilesActionsRenderer.PlayAction(transferItemHolder));
        }
        return menu;
    }

    @Override
    protected void setDefaultEditors() {
        TransferDetailFilesDataLine.ACTIONS_COLUMN.setCellEditor(new GenericCellEditor(getTransferDetailFileActionsRenderer()));
        TransferDetailFilesDataLine.ACTIONS_COLUMN.setCellRenderer(getTransferDetailFileActionsRenderer());
    }

    @Override
    protected void setupDragAndDrop() {
        TABLE.setDragEnabled(true);
        TABLE.setTransferHandler(new TransferDetailFilesTableTransferHandler(this));
    }


}