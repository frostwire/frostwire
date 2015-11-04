/*
=====================================================================
 
  SortHeaderRenderer.java
 
  Created by Claude Duguay
  Copyright (c) 2002
 
  Taken freely from:
   http://www.fawcette.com/javapro/2002_08/magazine/columns/visualcomponents/
   at the 'download code' link.
 
  Heavily modified to use the header's default renderer, but add
  icons.  Also modified to have a bevelled 'pressed' look.
 
=====================================================================
 */

package com.limegroup.gnutella.gui.tables;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import org.limewire.util.OSUtils;

public final class SortHeaderRenderer extends DefaultTableCellRenderer {

    public static Icon ASCENDING;
    public static Icon DESCENDING;

    static {
        setupIcons();
    }

    private boolean allowIcon = true;

    public SortHeaderRenderer() {
        setHorizontalAlignment(CENTER);
        setIconTextGap(2); // reduce from the default of 4 pixels
        setHorizontalTextPosition(LEFT);
    }

    public void setAllowIcon(boolean allow) {
        allowIcon = allow;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
        int index = -1;
        boolean ascending = true;
        boolean isPressed = false;
        if (allowIcon && table instanceof JSortTable) {
            JSortTable sortTable = (JSortTable) table;
            index = sortTable.getSortedColumnIndex();
            ascending = sortTable.isSortedColumnAscending();
            isPressed = (sortTable.getPressedColumnIndex() == col);
        }

        JLabel renderer = this;

        if (table != null) {
            JTableHeader header = table.getTableHeader();
            try {
                if (header != null) {
                    TableCellRenderer tcr = header.getDefaultRenderer();
                    Component c = tcr.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                    if (c instanceof JLabel) {
                        renderer = (JLabel) c;
                        renderer.setFont(header.getFont());
                        renderer.setBackground(header.getBackground());
                        renderer.setForeground(header.getForeground());
                    }
                }
            } catch (NullPointerException ignored) {
                // happens occasionally, ignore.
            }
        }

        if (value instanceof Icon) {
            renderer.setIcon((Icon) value);
            renderer.setText(null);
        } else {
            Icon icon = (col == index) ? (ascending ? ASCENDING : DESCENDING) : null;
            renderer.setIcon(icon);
            renderer.setText((value == null) ? null : value.toString());
        }

        if (renderer != this) {
            renderer.setHorizontalAlignment(CENTER);
            renderer.setIconTextGap(2); // reduce from the default of 4 pixels
            renderer.setHorizontalTextPosition(LEFT);
        }

        // Update the borders to simulate pressing, but only if the actual
        // renderer didn't put its own borders on.
        Border pressed, normal, active;
        pressed = UIManager.getBorder("TableHeader.cellPressedBorder");
        normal = UIManager.getBorder("TableHeader.cellBorder");
        active = renderer.getBorder();
        if (active == pressed || active == normal || active == null) {
            if (isPressed) {
                // need to explicitly check for null since some laf's
                // [osx] might not have it.  all will have cellBorder tho.
                renderer.setBorder(pressed == null ? normal : pressed);
            } else {
                renderer.setBorder(normal);
            }
        }

        return renderer;
    }

    private static void setupIcons() {
        if (OSUtils.isMacOSX()) {
            ASCENDING = AquaSortArrowIcon.getAscendingIcon();
            DESCENDING = AquaSortArrowIcon.getDescendingIcon();
        } else {
            ASCENDING = SortArrowIcon.getAscendingIcon();
            DESCENDING = SortArrowIcon.getDescendingIcon();
        }
    }
}
