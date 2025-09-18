/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.limegroup.gnutella.gui.tables.*;

import javax.swing.*;

public class IPFilterTableMediator extends AbstractTableMediator<IPFilterTableMediator.IPFilterModel, IPFilterTableMediator.IPFilterDataLine, IPRange> {
    private static IPFilterTableMediator INSTANCE = null;

    // AbstractTableMediator instances are meant to be singleton, otherwise issues with duplicate settings are raised upon the
    // second instantiations.
    private IPFilterTableMediator() {
        super("IP_FILTER_TABLE_MEDIATOR_ID");
    }

    public static IPFilterTableMediator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new IPFilterTableMediator();
        }
        if (INSTANCE == null) {
            throw new RuntimeException("Check your logic, IPFilterTableMediator instance being nullified by some thread");
        }
        return INSTANCE;
    }

    @Override
    protected void updateSplashScreen() {
    }

    @Override
    protected void setupConstants() {
        MAIN_PANEL = new PaddedPanel();
        DATA_MODEL = new IPFilterTableMediator.IPFilterModel();
        TABLE = new LimeJTable(DATA_MODEL);
        TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    }

    @Override
    protected JPopupMenu createPopupMenu() {
        return null;
    }

    public static class IPFilterDataLine extends AbstractDataLine<IPRange> {
        private final static int DESCRIPTION_ID = 0;
        private final static int START = 1;
        private final static int END = 2;
        private final static LimeTableColumn[] columns = new LimeTableColumn[]{
                new LimeTableColumn(DESCRIPTION_ID, "DESCRIPTION_ID", I18n.tr("Description"), 180, true, true, true, String.class),
                new LimeTableColumn(START, "START", I18n.tr("Start"), 180, true, true, true, String.class),
                new LimeTableColumn(END, "END", I18n.tr("End"), 180, true, true, true, String.class)};

        IPFilterDataLine() {
        }

        @Override
        public int getColumnCount() {
            return 3;
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
            IPRange ipRangeHolder = getInitializeObject();
            switch (col) {
                case DESCRIPTION_ID:
                    return ipRangeHolder.description();
                case START:
                    return ipRangeHolder.startAddress();
                case END:
                    return ipRangeHolder.endAddress();
                default:
                    return null;
            }
        }

        @Override
        public int getTypeAheadColumn() {
            return 0;
        }
    }

    class IPFilterModel extends BasicDataLineModel<IPFilterDataLine, IPRange> {
        IPFilterModel() {
            super(IPFilterDataLine.class);
        }
    }
}
