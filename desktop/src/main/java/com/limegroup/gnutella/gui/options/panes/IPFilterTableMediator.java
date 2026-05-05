/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.gui.theme.SkinPopupMenu;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.options.panes.ipfilter.AddRangeManuallyDialog;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.tables.*;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class IPFilterTableMediator extends AbstractTableMediator<IPFilterTableMediator.IPFilterModel, IPFilterTableMediator.IPFilterDataLine, IPRange> {
    private static final Logger LOG = Logger.getLogger(IPFilterTableMediator.class);
    private static volatile IPFilterTableMediator INSTANCE = null;
    private IPFilterPaneItem paneItem;

    private IPFilterTableMediator() {
        super("IP_FILTER_TABLE_MEDIATOR_ID");
    }

    public static synchronized IPFilterTableMediator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new IPFilterTableMediator();
        }
        return INSTANCE;
    }

    public void setPaneItem(IPFilterPaneItem paneItem) {
        this.paneItem = paneItem;
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
        // Disable tooltips to prevent macOS Input Method freeze on EDT (STRICT-EDT violation)
        javax.swing.ToolTipManager.sharedInstance().unregisterComponent(TABLE);
    }

    @Override
    protected JPopupMenu createPopupMenu() {
        if (TABLE.getSelectionModel().isSelectionEmpty() || paneItem == null) {
            return null;
        }
        int selectedRow = TABLE.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        IPFilterDataLine dataLine = DATA_MODEL.get(selectedRow);
        if (dataLine == null) {
            return null;
        }
        IPRange selectedRange = dataLine.getInitializeObject();
        if (selectedRange == null) {
            return null;
        }
        LOG.info("createPopupMenu() - selectedRow=" + selectedRow + ", selectedRange=" + selectedRange + ", modelSize=" + DATA_MODEL.getRowCount());
        JPopupMenu menu = new JPopupMenu();
        java.awt.Color bg = javax.swing.UIManager.getColor("Panel.background");
        if (bg != null) {
            menu.setBackground(bg);
        }
        JMenuItem editItem = new JMenuItem(new AbstractAction(I18n.tr("Edit")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                new AddRangeManuallyDialog(paneItem, selectedRange).setVisible(true);
            }
        });
        if (bg != null) editItem.setBackground(bg);
        menu.add(editItem);
        JMenuItem removeItem = new JMenuItem(new AbstractAction(I18n.tr("Remove")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                paneItem.onRangeRemoved(selectedRange);
            }
        });
        if (bg != null) removeItem.setBackground(bg);
        menu.add(removeItem);
        return menu;
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
