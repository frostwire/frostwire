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

import com.frostwire.transfers.TransferItem;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.ProgressBarHolder;
import com.limegroup.gnutella.gui.tables.SizeHolder;

public final class TransferDetailFilesDataLine extends AbstractDataLine<TransferDetailFiles.TransferItemHolder> {
    // TODO: Marcelina's design includes a "Share" column with a share button for each file.
    // This will require:
    // -> Creating a new torrent out of that file
    // -> The button only shows when the file is complete
    // -> A custom cell renderer for that column that displays a button and it's action listener
    static LimeTableColumn ACTIONS_COLUMN;
    private static final LimeTableColumn[] columns = new LimeTableColumn[]{
            // See TransferDetailFilesActionsRenderer for action's code
            ACTIONS_COLUMN = new LimeTableColumn(0, "ACTIONS", I18n.tr("Actions"), 80, true, true, true, TransferDetailFiles.TransferItemHolder.class),
            new LimeTableColumn(1, "NUMBER", "#", 40, true, true, true, String.class),
            new LimeTableColumn(2, "NAME", I18n.tr("Name"), 400, true, true, true, String.class),
            new LimeTableColumn(3, "PROGRESS", I18n.tr("Progress"), 150, true, ProgressBarHolder.class),
            new LimeTableColumn(4, "SIZE", I18n.tr("Size"), 80, true, true, true, SizeHolder.class),
            new LimeTableColumn(5, "TYPE", I18n.tr("Type"), 80, true, true, true, String.class),
    };

    public TransferDetailFilesDataLine() {
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
        final TransferDetailFiles.TransferItemHolder holder = getInitializeObject();
        if (holder == null) {
            return null;
        }
        final int ACTIONS = 0;
        final int NUMBER = 1;
        final int NAME = 2;
        final int PROGRESS = 3;
        final int SIZE = 4;
        final int TYPE = 5;
        switch (col) {
            case NUMBER:
                return holder.fileOffset + 1; // humans...
            case NAME:
                return holder.transferItem.getName();
            case PROGRESS:
                return holder.transferItem.isComplete() ? 100 : holder.transferItem.getProgress();
            case SIZE:
                return new SizeHolder(holder.transferItem.getSize());
            case TYPE:
                return holder.transferItem.getName().substring(holder.transferItem.getName().lastIndexOf(".") + 1);
            case ACTIONS:
                // See TransferDetailFilesActionsRenderer for action's code
                return holder;
        }
        return null;
    }

    public TransferItem getTransferItem() {
        final TransferDetailFiles.TransferItemHolder holder = getInitializeObject();
        if (holder == null) {
            return null;
        }
        return holder.transferItem;
    }


    @Override
    public void setValueAt(Object o, int col) {
    }

    @Override
    public int getTypeAheadColumn() {
        return 0;
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void update() {
    }

    @Override
    public String[] getToolTipArray(int col) {
        return new String[0];
    }

    @Override
    public boolean isTooltipRequired(int col) {
        return false;
    }
}
