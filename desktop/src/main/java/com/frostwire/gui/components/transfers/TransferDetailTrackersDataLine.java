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

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.SizeHolder;

public final class TransferDetailTrackersDataLine extends AbstractDataLine<TransferDetailTrackers.TrackerItemHolder> {
    private static final int URL_COLUMN_ID = 0;
    private static final int STATUS_COLUMN_ID = 1;
    private static final int SEEDS_COLUMN_ID = 2;
    private static final int PEERS_COLUMN_ID = 3;
    private static final int DOWNLOADED_COLUMN_ID = 4;
    private static final LimeTableColumn[] columns = new LimeTableColumn[]{
            new LimeTableColumn(URL_COLUMN_ID, "URL", I18n.tr("URL"), 180, true, true, true, String.class),
            new LimeTableColumn(STATUS_COLUMN_ID, "STATUS", I18n.tr("Status"), 180, true, true, true, String.class),
            new LimeTableColumn(SEEDS_COLUMN_ID, "SEEDS", I18n.tr("Seeds"), 180, true, true, true, String.class),
            new LimeTableColumn(PEERS_COLUMN_ID, "PEERS", I18n.tr("Peers"), 180, true, true, true, String.class),
            new LimeTableColumn(DOWNLOADED_COLUMN_ID, "DOWNLOADED", I18n.tr("Downloaded"), 180, true, true, true, SizeHolder.class),
    };

    public TransferDetailTrackersDataLine() {
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
        final TransferDetailTrackers.TrackerItemHolder holder = getInitializeObject();
        if (holder == null) {
            return null;
        }
        switch (col) {
            case URL_COLUMN_ID:
                return holder.url;
            case STATUS_COLUMN_ID:
                return holder.isActive ? I18n.tr("Active") : I18n.tr("Inactive");
            case SEEDS_COLUMN_ID:
                return holder.seeds;
            case PEERS_COLUMN_ID:
                return holder.peers;
            case DOWNLOADED_COLUMN_ID:
                return new SizeHolder(holder.downloaded);
        }
        return null;
    }

    @Override
    public int getTypeAheadColumn() {
        return 0;
    }
}
