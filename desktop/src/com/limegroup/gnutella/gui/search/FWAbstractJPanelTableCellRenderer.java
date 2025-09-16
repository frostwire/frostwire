/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.theme.SkinTableUI;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;

import javax.swing.*;
import javax.swing.plaf.TableUI;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Checklist for Editable/Interactive cell renderers (which will need a corresponding {@link AbstractCellEditor} implementation)
 * <p>
 * If you are writing a renderer for a cell editor, remember to:
 * 1. Make sure the Model for your table <code>isCellEditable()</model> method returns true for that column.
 * 2. Make sure to add the proper default cell editors on your mediator's setDefaultEditors class (on that particular column).
 * 3. Make sure to add the proper default cell renderer on {@link AbstractTableMediator} <code>setDefaultRenderers()</code>
 * 4. Avoid using FlowLayout as it will wrap if your component won't fit into the column.
 * 5. If your renderer is an editor, make sure to invoke `cancelEdit()` before performing any updates on your inner components,
 * otherwise you may get blank cells.
 *
 * @author gubatron
 */
abstract public class FWAbstractJPanelTableCellRenderer extends JPanel implements TableCellRenderer {
    private JTable table;
    private boolean isSelected;

    @Override
    public Component getTableCellRendererComponent(final JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        this.table = table;
        this.isSelected = isSelected;
        updateUIData(value, table, row, column);
        setOpaque(true);
        setEnabled(table.isEnabled());
        updateRowBackgroundColor(isSelected, row);
        initializeDefaultMouseListeners();
        return this;
    }

    private void updateRowBackgroundColor(boolean isSelected, int row) {
        if (isSelected) {
                /* Ask the JTable – it already knows the correct colors for the
           current Look‑and‑Feel (FlatLaf, Nimbus, etc.)                  */
            setBackground(table.getSelectionBackground());
            setForeground(table.getSelectionForeground());
        } else {
//            /* Alternate rows: try UIManager key first, fall back to table bg */
//            Color alt = UIManager.getColor("Table.alternateRowColor");
//            if (alt == null) {
//                alt = table.getBackground().darker();
//            }
//            setBackground((row & 1) == 1 ? alt : table.getBackground());
            setBackground(table.getBackground());
            setForeground(table.getForeground());

        }
    }

    private void initializeDefaultMouseListeners() {
        if (getMouseListeners() == null || getMouseListeners().length == 0) {
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    cancelEdit();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    cancelEdit();
                }
            });
        }
    }

    protected void cancelEdit() {
        if (table != null && table.isEditing()) {
            TableCellEditor editor = table.getCellEditor();
            editor.cancelCellEditing();
        }
    }

    protected abstract void updateUIData(Object dataHolder, JTable table, int row, int column);

    protected boolean mouseIsOverRow(JTable table, int row) {
        boolean mouseOver = false;
        try {
            TableUI ui = table.getUI();
            if (ui instanceof SkinTableUI) {
                mouseOver = ((SkinTableUI) ui).getRowAtMouse() == row;
            }
        } catch (Throwable e) {
            //e.printStackTrace();
        }
        return mouseOver;
    }

    protected void syncFontSize(JTable table, JComponent c) {
        Font tableFont = table.getFont();
        if (tableFont != null && !tableFont.equals(c.getFont())) {
            Font syncedFont = c.getFont().deriveFont((float) table.getFont().getSize());
            c.setFont(syncedFont);
        }
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void revalidate() {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void repaint(long tm, int x, int y, int width, int height) {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void repaint(Rectangle r) {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     *
     * @since 1.5
     */
    public void repaint() {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        // Strings get interned...
        if (propertyName == "text"
                || propertyName == "labelFor"
                || propertyName == "displayedMnemonic"
                || ((propertyName == "font" || propertyName == "foreground")
                && oldValue != newValue
                && getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) != null)) {
            super.firePropertyChange(propertyName, oldValue, newValue);
        }
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        /*
         * This is a Java Swing lesson on how to obtain the coordinates of the current cell
         * as you hover with the mouse on a JTable.
         *
         * You cannot use the renderer component, since it seems that once the table is done
         * stamping the cells with it, the instance of the renderer is not the one under the mouse
         * as it will always yield negative coordinates, for example, our debugger showed that this
         * renderer's coordinates were always (-95,-25)...
         *
         * What we did in this case, to show labels for the specific components inside the renderer
         * was to get the mouse coordinates, and translate its coordinates to the coordinates of the
         * current Cell Rectangle.
         *
         * One interesting gotcha in the process is that you cannot alter the event coordinates and then
         * try to use event.getPoint() since event.getPoint() will always give you a new instance of Point
         * so we keep a copy of that Point and then translate that point.
         *
         * tags: java, swing, table, get current cell coordinates, get table cell coordinates under mouse
         */
        try {
            Component[] components = this.getComponents();
            Point p = event.getPoint();
            int row = table.rowAtPoint(p);
            int col = table.columnAtPoint(p);
            Rectangle currentCell = table.getCellRect(row, col, false);
            p.translate(-currentCell.x, -currentCell.y);
            for (Component c : components) {
                JComponent jc = (JComponent) c;
                if (jc.isVisible() && jc.getBounds().contains(p)) {
                    return jc.getToolTipText(event);
                }
            }
        } catch (Throwable t) {
            //don't risk painting the table over a tooltip
        }
        return super.getToolTipText(event);
    }
}