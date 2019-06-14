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

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.MultilineToolTip;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * A specialized JTable for use with special Lime functions.
 * 1) Allows the user to easily
 * set a column as visible or invisible, rather than
 * having to remove/add columns.
 * It internally will remember where the column was and
 * add/remove it as needed.
 * 2) It remembers which column is sorted and whether the sort
 * is ascending or descending.
 * For use with adding arrows to the tableHeader.
 * 3) Shows special tooltips for each row.
 *
 * @author Sam Berlin
 * @author gubatron
 */
public class LimeJTable extends JTable implements JSortTable {
    /**
     * Constant empty string array for any class to use -- immutable.
     */
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    /**
     * The last LimeTableColumn that was removed from this table.
     */
    private static LimeTableColumn _lastRemoved;
    /**
     * The array to use when the tip is for extending a clipped name.
     */
    private final String[] CLIPPED_TIP = new String[1];
    /**
     * The columns that are currently hidden.
     */
    private final Map<Object, LimeTableColumn> _hiddenColumns = new HashMap<>();
    /**
     * The index of the column that is currently pressed down.
     */
    private int pressedColumnIndex = -1;
    /**
     * The array of tooltip data to display next.
     */
    private String[] tips;
    /**
     * The preferences handler for the table columns.
     */
    private ColumnPreferenceHandler columnPreferences;
    /**
     * The settings for this table.
     */
    private TableSettings tableSettings;

    /**
     * Same as JTable(TableModel)
     */
    public LimeJTable(DataLineModel<?, ?> dm) {
        super(dm);
        setToolTipText("");
        GUIUtils.fixInputMap(this);
        addFocusListener(FocusHandler.INSTANCE);
        setupTableFont();
    }

    /**
     * Override getSelectedRow to ensure that it exists in the table.
     * This is necessary because of bug 4730055.
     * See: http://developer.java.sun.com/developer/bugParade/bugs/4730055.html
     */
    public int getSelectedRow() {
        int selected = super.getSelectedRow();
        if (selected >= dataModel.getRowCount())
            return -1;
        else
            return selected;
    }

    /**
     * Sets the given row to be the only one selected.
     */
    public void setSelectedRow(int row) {
        clearSelection();
        addRowSelectionInterval(row, row);
    }

    /**
     * Overrided getSelectedRows to ensure that all selected rows exist in the
     * table. This is necessary because of bug 4730055.
     * See: http://developer.java.sun.com/developer/bugParade/bugs/4730055.html
     * <p>
     * As a side effect, this implementation will return the rows in a sorted
     * order. (Lowest first)
     */
    public int[] getSelectedRows() {
        int[] selected = super.getSelectedRows();
        if (selected == null || selected.length == 0)
            return selected;
        Arrays.sort(selected);
        int tableSize = dataModel.getRowCount();
        for (int i = 0; i < selected.length; i++) {
            // Short-circuit when we find an invalid value.
            if (selected[i] >= tableSize) {
                int[] newData = new int[i];
                System.arraycopy(selected, 0, newData, 0, i);
                return newData;
            }
        }
        //Nothing was outside of the selection range.
        return selected;
    }

    /**
     * Ensures the selected row is visible.
     */
    public void ensureSelectionVisible() {
        ensureRowVisible(getSelectedRow());
    }

    /**
     * Ensures the given row is visible.
     */
    public void ensureRowVisible(int row) {
        if (row != -1) {
            scrollRectToVisible(getCellRect(row, 0, true));
        }
    }

    /**
     * Determines if the selected row is visible.
     */
    boolean isSelectionVisible() {
        return isRowVisible(getSelectedRow());
    }

    /**
     * Determines if the given row is visible.
     */
    public boolean isRowVisible(int row) {
        if (row == -1) {
            return false;
        }
        Rectangle cellRect = getCellRect(row, 0, false);
        return getVisibleRect().intersects(cellRect);
    }

    /**
     * Access the ColumnPreferenceHandler.
     */
    ColumnPreferenceHandler getColumnPreferenceHandler() {
        return columnPreferences;
    }

    /**
     * Set the ColumnPreferenceHandler
     */
    void setColumnPreferenceHandler(ColumnPreferenceHandler handl) {
        columnPreferences = handl;
    }

    /**
     * Access the TableSettings.
     */
    TableSettings getTableSettings() {
        return tableSettings;
    }

    /**
     * Set the TableSettings.
     */
    void setTableSettings(TableSettings settings) {
        tableSettings = settings;
    }

    /**
     * get the pressed header column
     *
     * @return the VIEW index of the pressed column.
     */
    public int getPressedColumnIndex() {
        return convertColumnIndexToView(pressedColumnIndex);
    }

    /**
     * set the pressed header column.
     *
     * @param col The MODEL index of the column
     */
    void setPressedColumnIndex(int col) {
        pressedColumnIndex = col;
    }

    /**
     * @return the VIEW index of the sorted column.
     */
    public int getSortedColumnIndex() {
        return convertColumnIndexToView(((DataLineModel<?, ?>) dataModel).getSortColumn());
    }

    /**
     * accessor function
     */
    public boolean isSortedColumnAscending() {
        return ((DataLineModel<?, ?>) dataModel).isSortAscending();
    }

    /**
     * Simple function that tucks away hidden columns for use later.
     * And it uses them later!
     */
    void setColumnVisible(Object columnId, boolean visible) throws LastColumnException {
        if (!visible) {
            TableColumnModel model = getColumnModel();
            // don't allow the last column to be removed.
            if (model.getColumnCount() == 1)
                throw new LastColumnException();
            LimeTableColumn column = (LimeTableColumn) model.getColumn(model.getColumnIndex(columnId));
            _hiddenColumns.put(columnId, column);
            _lastRemoved = column;
            removeColumn(column);
        } else {
            TableColumn column = _hiddenColumns.get(columnId);
            _hiddenColumns.remove(columnId);
            addColumn(column);
        }
    }

    /**
     * Returns an iterator of the removed columns.
     */
    Iterator<LimeTableColumn> getHiddenColumns() {
        return Collections.unmodifiableCollection(_hiddenColumns.values()).iterator();
    }

    /**
     * Returns the last removed column.
     */
    LimeTableColumn getLastRemovedColumn() {
        return _lastRemoved;
    }

    /**
     * Determines whether or not a column is visible in this table.
     */
    public boolean isColumnVisible(Object columnId) {
        return !_hiddenColumns.containsKey(columnId);
    }

    /**
     * Determines if the given point is a selected row.
     */
    @SuppressWarnings("unused")
    public boolean isPointSelected(Point p) {
        int row = rowAtPoint(p);
        int col = columnAtPoint(p);
        if (row == -1 || col == -1)
            return false;
        int[] sel = getSelectedRows();
        for (int aSel : sel)
            if (aSel == row)
                return true;
        return false;
    }

    /**
     * Processes the given mouse event.
     */
    public void processMouseEvent(MouseEvent e) {
        try {
            int reselectIndex = -1;
            if (OSUtils.isAnyMac() && e.isControlDown()) {
                TableModel model = getModel();
                if (model != null) {
                    int index = rowAtPoint(e.getPoint());
                    if (isRowSelected(index)) {
                        reselectIndex = index;
                    }
                }
            }
            super.processMouseEvent(e);
            if (reselectIndex != -1) {
                getSelectionModel().addSelectionInterval(reselectIndex, reselectIndex);
            }
            // deselect rows if
            if (e.getID() == MouseEvent.MOUSE_CLICKED && SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
                TableModel model = getModel();
                if (model != null) {
                    int index = rowAtPoint(e.getPoint());
                    if (index < 0 || index >= model.getRowCount()) {
                        clearSelection();
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException aioobe) {
            // A bug in Java 1.3 causes an AIOOBE from PopupMenus.
            // Normally we would ignore this, since it has nothing
            // to do with LimeWire -- but because we insert ourselves
            // into the call-chain here, we must manually ignore the error.
            String msg = aioobe.getMessage();
            if (msg != null && msg.contains("at javax.swing.MenuSelectionManager.processMouseEvent"))
                return; // ignore;
            throw aioobe;
        }
    }

    /**
     * Sets the internal tooltip text for use with the next
     * createToolTip.
     */
    public String getToolTipText(MouseEvent e) {
        Point p = e.getPoint();
        int row = rowAtPoint(p);
        int col = columnAtPoint(p);
        TableCellRenderer cellRenderer = getCellRenderer(row, col);
        if (cellRenderer instanceof FWAbstractJPanelTableCellRenderer) {
            FWAbstractJPanelTableCellRenderer renderer = (FWAbstractJPanelTableCellRenderer) cellRenderer;
            String tooltip = renderer.getToolTipText(e);
            if (tooltip != null) {
                this.tips = new String[]{tooltip};
                return tooltip;
            }
        }
        int colModel = convertColumnIndexToModel(col);
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) dataModel;
        boolean isClippable = (col > -1 && row > -1) && dlm.isClippable(colModel);
        boolean forceTooltip = (col > -1 && row > -1) && dlm.isTooltipRequired(row, col);
        // If the user doesn't want tooltips, only display
        // them if the column is too small (and data is clipped)
        if (!tableSettings.DISPLAY_TOOLTIPS.getValue() && !forceTooltip) {
            if (isClippable)
                return clippedToolTip(row, col, colModel);
            else
                return null;
        }
        if (row > -1) {
            //set the internal tips for later use with createToolTip
            tips = dlm.getToolTipArray(row, colModel);
            // NOTE: the below return triggers the tooltip manager to
            // create a tooltip.
            // If it is null, one won't be created.
            // If two different rows return the same tip, the manager
            // won't be triggered to create a 'new' tip.
            // Rather than return the actual row#, which could stay the same
            // if sorting is enabled & the DataLine moves,
            // return the string representation
            // of the dataline, so if the row moves out from under the mouse,
            // the tooltip will auto change when the mouse
            // moves around the new DataLine (same row)
            if (tips == null) {
                // if we're over a column, see if we can display a clipped tool tip.
                if (isClippable)
                    return clippedToolTip(row, col, colModel);
                else
                    return null;
            } else
                return dlm.get(row).toString() + col;
        }
        tips = EMPTY_STRING_ARRAY;
        return null;
    }

    /**
     * Displays a tooltip for clipped data, if possible.
     *
     * @param row      the row of the data
     * @param col      the VIEW index of the column
     * @param colModel the MODEL index of the column
     */
    private String clippedToolTip(int row, int col, int colModel) {
        TableColumn tc = getColumnModel().getColumn(col);
        int columnWidth = tc.getWidth();
        int dataWidth = getDataWidth(row, colModel);
        if (columnWidth < dataWidth) {
            tips = CLIPPED_TIP;
            stripHTMLFromTips();
            return ((DataLineModel<?, ?>) dataModel).get(row).toString() + col;
        } else {
            tips = EMPTY_STRING_ARRAY;
            return null;
        }
    }

    private void stripHTMLFromTips() {
        if (tips == null || tips.length == 0) {
            return;
        }
        int i = 0;
        for (String s : tips) {
            tips[i++] = stripHTML(s);
        }
    }

    private String stripHTML(String html) {
        @SuppressWarnings("RegExpRedundantEscape") String clean = html.replaceAll("\\<.*?>", "");
        clean = clean.replaceAll("&nbsp;", "");
        clean = clean.replaceAll("&amp;", "&");
        return clean;
    }

    /**
     * Gets the width of the data in the specified row/column.
     *
     * @param row the row of the data
     * @param col the MODEL index of the column
     */
    private int getDataWidth(int row, int col) {
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) dataModel;
        DataLine<?> dl = dlm.get(row);
        Object data = dl.getValueAt(col);
        String info;
        if (data != null && (info = data.toString()) != null) {
            if (data instanceof Icon && info.startsWith("file:"))
                return -1;
            CLIPPED_TIP[0] = info;
            TableCellRenderer tcr = getDefaultRenderer(dlm.getColumnClass(col));
            JComponent renderer = (JComponent) tcr.getTableCellRendererComponent(this, data, false, false, row, col);
            try {
                FontMetrics fm = renderer.getFontMetrics(renderer.getFont());
                return fm.stringWidth(info) + 3;
            } catch (NullPointerException npe) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    /**
     * @return The JToolTip returned is actually a JMultilineToolTip
     */
    public JToolTip createToolTip() {
        MultilineToolTip ret = new MultilineToolTip();
        if (tips != null && tips.length > 0) {
            ret.setTipArray(tips);
        }
        tips = EMPTY_STRING_ARRAY;
        return ret;
    }

    /**
     * Overrides JTable's default implementation in order to add
     * LimeTableColumn columns.
     */
    public void createDefaultColumnsFromModel() {
        DataLineModel<?, ?> dlm = (DataLineModel<?, ?>) dataModel;
        if (dlm != null) {
            // Remove any current columns
            TableColumnModel cm = getColumnModel();
            while (cm.getColumnCount() > 0) {
                cm.removeColumn(cm.getColumn(0));
            }
            // Create new columns from the data model info
            for (int i = 0; i < dlm.getColumnCount(); i++) {
                TableColumn newColumn = dlm.getTableColumn(i);
                if (newColumn != null) {
                    addColumn(newColumn);
                }
            }
        }
    }

    /**
     * Returns the next list element that starts with
     * a prefix.
     *
     * @param prefix     the string to test for a match
     * @param startIndex the index for starting the search
     * @param bias       the search direction, either
     *                   Position.Bias.Forward or Position.Bias.Backward.
     * @return the index of the next list element that
     * starts with the prefix; otherwise -1
     * @throws IllegalArgumentException if prefix is null
     *                                  or startIndex is out of bounds
     */
    @SuppressWarnings("SameParameterValue")
    int getNextMatch(String prefix, int startIndex, Position.Bias bias) {
        DataLineModel<?, ?> model = (DataLineModel<?, ?>) dataModel;
        int max = model.getRowCount();
        if (prefix == null)
            throw new IllegalArgumentException();
        if (startIndex < 0 || startIndex >= max)
            throw new IllegalArgumentException();
        prefix = prefix.toUpperCase();
        // start search from the next element after the selected element
        int increment = (bias == Position.Bias.Forward) ? 1 : -1;
        int index = startIndex;
        int typeAheadColumn = model.getTypeAheadColumn();
        if (typeAheadColumn >= 0 && typeAheadColumn < model.getColumnCount()) {
            do {
                Object o = model.getValueAt(index, typeAheadColumn);
                if (o != null) {
                    String string;
                    if (o instanceof String)
                        string = ((String) o).toUpperCase();
                    else {
                        string = o.toString();
                        if (string != null)
                            string = string.toUpperCase();
                    }
                    if (string != null && string.startsWith(prefix))
                        return index;
                }
                index = (index + increment + max) % max;
            } while (index != startIndex);
        }
        return -1;
    }

    /*
     * Stretch JTable to JViewport height so that the space
     * underneath the rows fires mouse events as well
     */
    public boolean getScrollableTracksViewportHeight() {
        Component parent = getParent();
        if (parent instanceof javax.swing.JViewport)
            return parent.getHeight() > getPreferredSize().height;
        return super.getScrollableTracksViewportHeight();
    }

    /**
     * Paints the table & a focused row border.
     */
    public void paint(Graphics g) {
        try {
            super.paint(g);
        } catch (Exception e) {
            //ignore
        }
        int focusedRow = getFocusedRow(true);
        if (focusedRow != -1 && focusedRow < getRowCount()) {
            Border rowBorder = UIManager.getBorder("Table.focusRowHighlightBorder");
            if (rowBorder != null) {
                Rectangle rect = getCellRect(focusedRow, 0, true);
                rect.width = getWidth();
                rowBorder.paintBorder(this, g, rect.x, rect.y, rect.width, rect.height);
            }
        }
    }

    @SuppressWarnings("unused")
    protected boolean isOverrideRowColor(int row) {
        return false;
    }

    /**
     * Repaints the focused row if one was focused.
     */
    private void repaintFocusedRow() {
        int focusedRow = getFocusedRow(false);
        if (focusedRow != -1 && focusedRow < getRowCount()) {
            Rectangle rect = getCellRect(focusedRow, 0, true);
            rect.width = getWidth();
            repaint(rect);
        }
    }

    /**
     * Gets the focused row.
     */
    private int getFocusedRow(boolean requireFocus) {
        if (!requireFocus || hasFocus())
            return selectionModel.getAnchorSelectionIndex();
        else
            return -1;
    }

    private void setupTableFont() {
        Font f = ThemeMediator.getCurrentTableFont();
        UIDefaults nimbusOverrides = new UIDefaults();
        nimbusOverrides.put("Table.font", new FontUIResource(f));
        putClientProperty("Nimbus.Overrides", nimbusOverrides);
        FontMetrics fm = getFontMetrics(f);
        int h = fm.getHeight() + 8;
        setRowHeight(h);
    }

    /**
     * Handler for repainting focus for all tables.
     */
    private static class FocusHandler implements FocusListener {
        private static final FocusListener INSTANCE = new FocusHandler();

        public void focusGained(FocusEvent e) {
            LimeJTable t = (LimeJTable) e.getSource();
            t.repaintFocusedRow();
        }

        public void focusLost(FocusEvent e) {
            LimeJTable t = (LimeJTable) e.getSource();
            t.repaintFocusedRow();
        }
    }
}
