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

import com.frostwire.bittorrent.BTDownloadItem;
import com.frostwire.gui.bittorrent.TransferDetailFilesActionsRenderer;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.jlibtorrent.Priority;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.search.GenericCellEditor;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class TransferDetailFilesTableMediator extends
    AbstractTableMediator<TransferDetailFilesModel, TransferDetailFilesDataLine, TransferDetailFiles.TransferItemHolder> {

    private Runnable onPriorityChangedCallback;

    TransferDetailFilesTableMediator() {
        super("TRANSFER_DETAIL_FILES_TABLE_MEDIATOR");
    }

    void setOnPriorityChangedCallback(Runnable callback) {
        this.onPriorityChangedCallback = callback;
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

        // Dim skipped files in the Name column so they appear visually distinct
        DefaultTableCellRenderer skippedRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected && row >= 0 && row < DATA_MODEL.getRowCount()) {
                    TransferDetailFilesDataLine line = DATA_MODEL.get(row);
                    if (line != null) {
                        TransferDetailFiles.TransferItemHolder holder = line.getInitializeObject();
                        if (holder != null && holder.skipped) {
                            c.setForeground(Color.GRAY);
                        } else {
                            c.setForeground(table.getForeground());
                        }
                    }
                }
                return c;
            }
        };
        TABLE.getColumnModel().getColumn(2).setCellRenderer(skippedRenderer); // Name column

        // Click on Priority column shows popup menu
        TABLE.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = TABLE.columnAtPoint(e.getPoint());
                if (col != 6) return; // Priority column
                int row = TABLE.rowAtPoint(e.getPoint());
                if (row < 0 || row >= DATA_MODEL.getRowCount()) return;
                TransferDetailFilesDataLine line = DATA_MODEL.get(row);
                if (line == null) return;
                TransferDetailFiles.TransferItemHolder holder = line.getInitializeObject();
                if (holder == null) return;
                showPriorityPopup(e.getComponent(), e.getX(), e.getY(), holder);
            }
        });
    }

    private void showPriorityPopup(Component invoker, int x, int y, TransferDetailFiles.TransferItemHolder holder) {
        JPopupMenu menu = new SkinPopupMenu();
        int[][] priorities = {
            {0, Priority.IGNORE.swig()},
            {1, Priority.NORMAL.swig()},
            {2, Priority.TWO.swig()},
            {3, Priority.THREE.swig()},
            {4, Priority.FOUR.swig()},
            {5, Priority.FIVE.swig()},
            {6, Priority.SIX.swig()},
            {7, Priority.SEVEN.swig()},
        };
        for (int[] pair : priorities) {
            int p = pair[0];
            String label = TransferDetailFilesDataLine.priorityToString(p);
            JMenuItem item = new SkinMenuItem(label);
            if (holder.priority == p) {
                item.setEnabled(false);
                item.setText("✓ " + label);
            }
            final int targetPriority = pair[1];
            item.addActionListener(e -> setFilePriority(holder, targetPriority));
            menu.add(item);
        }
        menu.show(invoker, x, y);
    }

    private void setFilePriority(TransferDetailFiles.TransferItemHolder holder, int priority) {
        if (!(holder.transferItem instanceof BTDownloadItem)) {
            return;
        }
        BTDownloadItem btItem = (BTDownloadItem) holder.transferItem;
        BackgroundQueuedExecutorService.schedule(() -> {
            btItem.setPriority(Priority.fromSwig(priority));
            if (onPriorityChangedCallback != null) {
                SwingUtilities.invokeLater(onPriorityChangedCallback);
            }
        });
    }

    @Override
    protected JPopupMenu createPopupMenu() {
        TransferDetailFilesModel dataModel = getDataModel();
        TransferDetailFilesDataLine transferDetailFilesDataLine = dataModel.get(TABLE.getSelectedRow());
        TransferDetailFiles.TransferItemHolder transferItemHolder = transferDetailFilesDataLine.getInitializeObject();
        JPopupMenu menu = new SkinPopupMenu();
        if (transferItemHolder.skipped) {
            menu.add(new TransferDetailFilesActionsRenderer.DownloadAction(transferItemHolder));
        } else {
            menu.add(new TransferDetailFilesActionsRenderer.OpenInFolderAction(transferItemHolder));
            if (transferItemHolder.complete) {
                menu.add(new TransferDetailFilesActionsRenderer.PlayAction(transferItemHolder));
            }
        }
        // Also add priority submenu
        menu.addSeparator();
        JMenu priorityMenu = new SkinMenu(I18n.tr("Set Priority"));
        int[][] priorities = {
            {0, Priority.IGNORE.swig()},
            {1, Priority.NORMAL.swig()},
            {2, Priority.TWO.swig()},
            {3, Priority.THREE.swig()},
            {4, Priority.FOUR.swig()},
            {5, Priority.FIVE.swig()},
            {6, Priority.SIX.swig()},
            {7, Priority.SEVEN.swig()},
        };
        for (int[] pair : priorities) {
            int p = pair[0];
            String label = TransferDetailFilesDataLine.priorityToString(p);
            JMenuItem item = new SkinMenuItem(label);
            if (transferItemHolder.priority == p) {
                item.setEnabled(false);
            }
            final int targetPriority = pair[1];
            item.addActionListener(e -> setFilePriority(transferItemHolder, targetPriority));
            priorityMenu.add(item);
        }
        menu.add(priorityMenu);
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