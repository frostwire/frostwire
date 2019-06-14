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
                return holder;
        }
        return null;
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
