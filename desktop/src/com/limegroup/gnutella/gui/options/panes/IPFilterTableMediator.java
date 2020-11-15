/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

    @Override
    public void handleActionKey() {
    }

    @Override
    public void handleSelection(int row) {
    }

    @Override
    public void handleNoSelection() {
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
