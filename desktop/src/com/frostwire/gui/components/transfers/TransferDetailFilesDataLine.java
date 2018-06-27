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


    private static LimeTableColumn[] columns = new LimeTableColumn[]{
            new LimeTableColumn(0, "NUMBER", "#", 30, true, true, false, String.class),
            new LimeTableColumn(1, "NAME", I18n.tr("Name"), 500, true, String.class),
            new LimeTableColumn(2, "PROGRESS", I18n.tr("Progress"), 150, true, ProgressBarHolder.class),
            new LimeTableColumn(3, "SIZE", I18n.tr("Size"), 65, true, true, false, SizeHolder.class),
            new LimeTableColumn(4, "TYPE", I18n.tr("Type"), 40, true, true, false, String.class)
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
        final int NUMBER = 0;
        final int NAME = 1;
        final int PROGRESS = 2;
        final int SIZE = 3;
        final int TYPE = 4;
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
