/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
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