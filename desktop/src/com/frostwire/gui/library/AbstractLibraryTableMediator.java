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

package com.frostwire.gui.library;

import com.frostwire.gui.bittorrent.SendFileProgressDialog;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.DataLineModel;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.io.File;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static com.limegroup.gnutella.gui.I18n.tr;

/**
 * @param <T>
 * @param <E>
 * @param <I>
 * @author gubatron
 * @author aldenml
 */
abstract class AbstractLibraryTableMediator<T extends DataLineModel<E, I>, E extends AbstractLibraryTableDataLine<I>, I> extends AbstractTableMediator<T, E, I> {
    private static final LibraryActionsRenderer ACTION_RENDERER = new LibraryActionsRenderer();
    Action SEND_TO_FRIEND_ACTION;
    private MediaType mediaType;
    private int needToScrollTo;
    private AdjustmentListener adjustmentListener;

    AbstractLibraryTableMediator(String id) {
        super(id);
        GUIMediator.addRefreshListener(this);
        mediaType = MediaType.getAnyTypeMediaType();
        needToScrollTo = -1;
    }

    @Override
    protected void setupConstants() {
    }

    @Override
    protected TableCellRenderer getAbstractActionsRenderer() {
        return ACTION_RENDERER;
    }

    List<AbstractLibraryTableDataLine<I>> getSelectedLines() {
        int[] selected = TABLE.getSelectedRows();
        List<AbstractLibraryTableDataLine<I>> lines = new ArrayList<>(selected.length);
        for (int aSelected : selected) {
            lines.add(DATA_MODEL.get(aSelected));
        }
        return lines;
    }

    int[] getSelectedIndexes() {
        if (TABLE != null) {
            return TABLE.getSelectedRows();
        } else {
            return null;
        }
    }

    private I getItemAt(int row) {
        try {
            return DATA_MODEL.get(row).getInitializeObject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * This method selects the given item and ensures that it's visible (scrolls to it)
     */
    boolean setItemSelected(I item) {
        int i = DATA_MODEL.getRow(item);
        if (i != -1) {
            TABLE.setSelectedRow(i);
            TABLE.ensureSelectionVisible();
            return true;
        }
        return false;
    }

    /**
     * Convenience method to select an item at the given row.
     */
    @SuppressWarnings("unused")
    public boolean selectItemAt(int row) {
        return setItemSelected(getItemAt(row));
    }

    @Override
    protected JComponent getScrolledTablePane() {
        JComponent comp = super.getScrolledTablePane();
        if (adjustmentListener == null) {
            adjustmentListener = this::adjustmentListener_adjustmentValueChanged;
            SCROLL_PANE.getVerticalScrollBar().addAdjustmentListener(adjustmentListener);
        }
        return comp;
    }

    protected abstract List<MediaSource> getFilesView();

    public MediaType getMediaType() {
        return mediaType;
    }

    void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    @Override
    protected void buildListeners() {
        super.buildListeners();
        SEND_TO_FRIEND_ACTION = new SendToFriendAction();
    }

    @SuppressWarnings("unused")
    private void adjustmentListener_adjustmentValueChanged(AdjustmentEvent e) {
        try {
            int value = needToScrollTo;
            if (value >= 0) {
                if (SCROLL_PANE.getVerticalScrollBar().getMaximum() >= value) {
                    SCROLL_PANE.getVerticalScrollBar().setValue(value);
                    Toolkit.getDefaultToolkit().sync();
                    needToScrollTo = -1;
                }
            }
        } catch (Exception ex) {
            needToScrollTo = -1;
        }
    }

    void scrollTo(int value) {
        needToScrollTo = value;
    }

    int getScrollbarValue() {
        if (SCROLL_PANE != null && SCROLL_PANE.getVerticalScrollBar() != null) {
            return SCROLL_PANE.getVerticalScrollBar().getValue();
        }
        return 0;
    }

    void playCurrentSelection() {
        E line = DATA_MODEL.get(TABLE.getSelectedRow());
        if (line == null) {
            return;
        }
        try {
            MediaSource mediaSource = createMediaSource(line);
            if (mediaSource != null) {
                MediaPlayer.instance().asyncLoadMedia(mediaSource, false, getFilesView());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract MediaSource createMediaSource(E line);

    @Override
    public void removeSelection() {
        super.removeSelection();
        LibraryMediator.instance().clearDirectoryHolderCaches();
    }

    static class SendToFriendAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 1329472129818371471L;

        SendToFriendAction() {
            super(tr("Send to friend"));
            putValue(LimeAction.SHORT_NAME, tr("Send"));
            putValue(Action.LONG_DESCRIPTION, tr("Send to friend"));
            //putValue(Action.SMALL_ICON, GUIMediator.getThemeImage("share"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_SEND");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File file = LibraryMediator.instance().getSelectedFile();
            if (file == null) {
                return;
            }
            DialogOption result = GUIMediator.showYesNoMessage(tr("Do you want to send this file to a friend?") + "\n\n\"" + file.getName() + "\"", tr("Send files with FrostWire"), JOptionPane.QUESTION_MESSAGE);
            if (result == DialogOption.YES) {
                new SendFileProgressDialog(GUIMediator.getAppFrame(), file).setVisible(true);
                GUIMediator.instance().setWindow(GUIMediator.Tabs.TRANSFERS);
            }
        }
    }
}
