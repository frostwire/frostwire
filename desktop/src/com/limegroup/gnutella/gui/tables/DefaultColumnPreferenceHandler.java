/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.tables;

import com.limegroup.gnutella.settings.TablesHandlerSettings;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumnModel;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Iterator;

/**
 * Handles column preferences through a settings file.
 * <p>
 * This is the default implementation for ColumnPreferences.
 */
public class DefaultColumnPreferenceHandler implements ColumnPreferenceHandler, TableColumnModelListener, MouseListener {
    /**
     * The table that this is storing the column preferences for.
     */
    protected final LimeJTable table;
    /**
     * The current SimpleColumnListener for callbacks.
     */
    private SimpleColumnListener listener = null;
    /**
     * Indicates a margin has been changed since we last released the mouse.
     */
    private boolean marginChanged;

    /**
     * Constructs a new DefaultColumnPreferences object for this table.
     */
    public DefaultColumnPreferenceHandler(LimeJTable t) {
        table = t;
        startListening();
    }

    /**
     * Sets the SimpleColumnListener.  If one already was listening, it is
     * removed.
     */
    public void setSimpleColumnListener(SimpleColumnListener scl) {
        listener = scl;
    }

    /**
     * Triggered from a column being added.
     * The 'to' index of the TableColumnModelEvent is the index
     * in the TableColumn of the newly added column.
     */
    public void columnAdded(TableColumnModelEvent e) {
        LimeTableColumn addedColumn = getToColumn(e);
        LimeTableColumn ltc = addedColumn;
        setVisibility(ltc, true);
        TableColumnModel tcm = table.getColumnModel();
        int order = getOrder(ltc);
        int current = tcm.getColumnIndex(ltc.getId());
        int max = tcm.getColumnCount();
        // move this column to where we want it.
        if (order != current) {
            stopListening();
            // make sure we don't try to put this after the end.
            order = Math.min(order, max - 1);
            tcm.moveColumn(current, order);
            // traverse through and reset the saved order of columns.
            for (current = order + 1; current < max; current++) {
                ltc = (LimeTableColumn) tcm.getColumn(current);
                setOrder(ltc, current);
            }
            // traverse through the hidden columns and tell them to
            // move back up a notch if they're above us.
            for (Iterator<LimeTableColumn> i = table.getHiddenColumns(); i.hasNext(); ) {
                ltc = i.next();
                current = getOrder(ltc);
                if (current > order)
                    setOrder(ltc, current + 1);
            }
            startListening();
        }
        if (listener != null)
            listener.columnAdded(addedColumn, table);
        save();
    }

    /**
     * Triggered from a column's margin being changed.
     * This is triggered whenever a table is made visible, whenever
     * a scrollbar appears or disappears, and whenever the margins
     * of the columns change.
     * Currently does nothing.
     */
    public void columnMarginChanged(ChangeEvent e) {
        // see if we can avoid the system call...
        // if this is null, it means we resized the app or a scrollbar appeared
        if (table.getTableHeader().getResizingColumn() == null)
            return;
        marginChanged = true;
    }

    /**
     * Triggered from a column being moved.
     * This triggers whenever a column is touched.  The 'from' and 'to'
     * indexes may be the same -- if they are, we ignore the event.
     */
    public void columnMoved(TableColumnModelEvent e) {
        if (e.getFromIndex() == e.getToIndex())
            return;
        LimeTableColumn from = getFromColumn(e);
        LimeTableColumn to = getToColumn(e);
        setOrder(from, e.getFromIndex());
        setOrder(to, e.getToIndex());
        save();
    }

    /**
     * Triggered from a column being removed.
     * The TableColumnModelEvent is supremely dumb here,
     * not even giving us the TableColumn that was removed.
     * So, we need to ask the table what the last removed column was.
     */
    public void columnRemoved(TableColumnModelEvent e) {
        LimeTableColumn ltc;
        //save the reordered columns
        TableColumnModel tcm = table.getColumnModel();
        for (int i = 0; i < tcm.getColumnCount(); i++) {
            ltc = (LimeTableColumn) tcm.getColumn(i);
            setOrder(ltc, i);
        }
        LimeTableColumn removedColumn = table.getLastRemovedColumn();
        ltc = removedColumn;
        setVisibility(ltc, false);
        //decrease the order in hidden columns by one if they were
        //before the hidden column's order.
        int order = getOrder(ltc);
        for (Iterator<LimeTableColumn> i = table.getHiddenColumns(); i.hasNext(); ) {
            ltc = i.next();
            int current = getOrder(ltc);
            if (current > order)
                setOrder(ltc, current - 1);
        }
        if (listener != null)
            listener.columnRemoved(removedColumn, table);
        save();
    }

    /**
     * From a column's selection changing.
     * Does nothing.
     */
    public void columnSelectionChanged(ListSelectionEvent e) {
    }

    /**
     * The mouse was clicked on the table header.
     */
    public void mouseClicked(MouseEvent e) {
    }

    /**
     * The mouse entered the table header.
     */
    public void mouseEntered(MouseEvent e) {
    }

    /**
     * The mouse exited the table header.
     */
    public void mouseExited(MouseEvent e) {
    }

    /**
     * The mouse pressed the table header.
     */
    public void mousePressed(MouseEvent e) {
    }

    /**
     * The mouse released from the table header.
     */
    public void mouseReleased(MouseEvent e) {
        // if the margins haven't changed, exit.
        if (!marginChanged)
            return;
        // iterate through and save the widths we wanted.
        TableColumnModel tcm = table.getColumnModel();
        for (int i = 0; i < tcm.getColumnCount(); i++) {
            LimeTableColumn ltc = (LimeTableColumn) tcm.getColumn(i);
            setWidth(ltc, ltc.getWidth());
        }
        marginChanged = false;
        save();
    }

    /**
     * Reverts this table's header preferences to their default
     * values.
     */
    public void revertToDefault() {
        stopListening();
        // Traverse & change settings, and make everything visible
        // so we can traverse back through & set the order and width.
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) table.getModel();
        for (int i = 0; i < dlm.getColumnCount(); i++) {
            LimeTableColumn ltc = dlm.getTableColumn(i);
            setVisibility(ltc, ltc.getDefaultVisibility());
            setOrder(ltc, ltc.getDefaultOrder());
            setWidth(ltc, ltc.getDefaultWidth());
            try {
                if (!table.isColumnVisible(ltc.getId())) {
                    table.setColumnVisible(ltc.getId(), true);
                    if (listener != null)
                        listener.columnAdded(ltc, table);
                }
            } catch (LastColumnException ignored) {
            }
        }
        // traverse to set the order ...
        TableColumnModel tcm = table.getColumnModel();
        for (int i = 0; i < dlm.getColumnCount(); i++) {
            LimeTableColumn ltc = dlm.getTableColumn(i);
            int order = getOrder(ltc);
            int current = tcm.getColumnIndex(ltc.getId());
            if (current != order)
                tcm.moveColumn(current, order);
            ltc.setPreferredWidth(ltc.getDefaultWidth());
        }
        // traverse to set the visibility ...
        for (int i = 0; i < dlm.getColumnCount(); i++) {
            LimeTableColumn ltc = dlm.getTableColumn(i);
            boolean wantVis = getVisibility(ltc);
            try {
                if (!wantVis) {
                    table.setColumnVisible(ltc.getId(), false);
                    if (listener != null)
                        listener.columnRemoved(ltc, table);
                }
            } catch (LastColumnException ignored) {
            }
        }
        startListening();
        save();
    }

    /**
     * Determines whether the columns are already the default values.
     */
    public boolean isDefault() {
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) table.getModel();
        for (int i = 0; i < dlm.getColumnCount(); i++) {
            LimeTableColumn ltc = dlm.getTableColumn(i);
            if (!isDefaultWidth(ltc))
                return false;
            if (!isDefaultOrder(ltc))
                return false;
            if (!isDefaultVisibility(ltc))
                return false;
        }
        return true;
    }

    /**
     * Sets the headers to the correct widths, depending on
     * the user's preferences for this table.  This will not set
     * the table to exactly the user's widths, because the only
     * way to set the width is to suggest it via setPreferredWidth.
     */
    public void setWidths() {
        stopListening();
        //traverse through each possible column and set its preferred
        //width.  this MUST use the DataLineModel to traverse, to ensure
        //that we set the future preferred width for any added columns.
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) table.getModel();
        for (int i = 0; i < dlm.getColumnCount(); i++) {
            LimeTableColumn ltc = dlm.getTableColumn(i);
            if (ltc != null) {
                int width = getWidth(ltc);
                if (width != -1) {
                    ltc.setPreferredWidth(width);
                }
            }
        }
        startListening();
    }

    /**
     * Sets the headers to the correct order, depending on the
     * user's preferences for this table.
     */
    public void setOrder() {
        stopListening();
        boolean changed = false;
        //traverse through each possible column, and if it's visible,
        //put it in the correct place.  this MUST use the DataLineModel
        //to traverse, so reordering doesn't confuse what we're looking at.
        TableColumnModel tcm = table.getColumnModel();
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) table.getModel();
        int max = dlm.getColumnCount();
        for (int i = 0; i < max; i++) {
            LimeTableColumn ltc = dlm.getTableColumn(i);
            if (ltc != null) {
                int order = getOrder(ltc);
                if (table.isColumnVisible(ltc.getId())) {
                    int current = tcm.getColumnIndex(ltc.getId());
                    // can't go beyond boundary
                    if (order >= max) {
                        order = max - 1;
                        setOrder(ltc, order);
                        changed = true;
                    }
                    if (current != order)
                        tcm.moveColumn(current, order);
                }
            }
        }
        if (changed)
            TablesHandlerSettings.instance().save();
        startListening();
    }

    /**
     * Sets the headers so that some may be invisible, depending
     * on the user's preferences for this table.
     */
    public void setVisibility() {
        stopListening();
        //traverse through each possible column, and set its
        //visibility appropriately
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) table.getModel();
        for (int i = 0; i < dlm.getColumnCount(); i++) {
            LimeTableColumn ltc = dlm.getTableColumn(i);
            boolean wantVis = getVisibility(ltc);
            // if we want to see it and we don't currently see it, show it.
            // if we don't want to see it and we currently see it, hide it.
            boolean isVis = table.isColumnVisible(ltc.getId());
            try {
                if (wantVis && !isVis) {
                    table.setColumnVisible(ltc.getId(), true);
                    if (listener != null)
                        listener.columnAdded(ltc, table);
                } else if (!wantVis && isVis) {
                    table.setColumnVisible(ltc.getId(), false);
                    if (listener != null)
                        listener.columnRemoved(ltc, table);
                }
            } catch (LastColumnException ee) {
                // ignore it -- we can't show an error while starting up.
            }
        }
        startListening();
    }

    protected void save() {
        TablesHandlerSettings.instance().save();
    }

    private void startListening() {
        table.getTableHeader().addMouseListener(this);
        table.getColumnModel().addColumnModelListener(this);
    }

    private void stopListening() {
        table.getTableHeader().removeMouseListener(this);
        table.getColumnModel().removeColumnModelListener(this);
    }

    private LimeTableColumn getToColumn(TableColumnModelEvent e) {
        return (LimeTableColumn) table.getColumnModel().getColumn(e.getToIndex());
    }

    private LimeTableColumn getFromColumn(TableColumnModelEvent e) {
        return (LimeTableColumn) table.getColumnModel().getColumn(e.getFromIndex());
    }

    protected void setVisibility(LimeTableColumn col, boolean vis) {
        TablesHandlerSettings.getVisibility(col.getId(), col.getDefaultVisibility()).setValue(vis);
    }

    protected void setOrder(LimeTableColumn col, int order) {
        TablesHandlerSettings.getOrder(col.getId(), col.getDefaultOrder()).setValue(order);
    }

    protected void setWidth(LimeTableColumn col, int width) {
        TablesHandlerSettings.getWidth(col.getId(), col.getDefaultWidth()).setValue(width);
    }

    protected boolean getVisibility(LimeTableColumn col) {
        return TablesHandlerSettings.getVisibility(col.getId(), col.getDefaultVisibility()).getValue();
    }

    protected int getOrder(LimeTableColumn col) {
        return TablesHandlerSettings.getOrder(col.getId(), col.getDefaultOrder()).getValue();
    }

    protected int getWidth(LimeTableColumn col) {
        return TablesHandlerSettings.getWidth(col.getId(), col.getDefaultWidth()).getValue();
    }

    protected boolean isDefaultVisibility(LimeTableColumn col) {
        return TablesHandlerSettings.getVisibility(col.getId(), col.getDefaultVisibility()).isDefault();
    }

    protected boolean isDefaultOrder(LimeTableColumn col) {
        return TablesHandlerSettings.getOrder(col.getId(), col.getDefaultOrder()).isDefault();
    }

    protected boolean isDefaultWidth(LimeTableColumn col) {
        return TablesHandlerSettings.getWidth(col.getId(), col.getDefaultWidth()).isDefault();
    }
}