package com.limegroup.gnutella.gui.tables;

import com.limegroup.gnutella.gui.GUIUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Date;

public class DateRenderer extends DefaultTableBevelledCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
        if (value != null) {
            if (value instanceof Date)
                value = GUIUtils.getFullDateTimeFormat().format((Date) value);
            else if (value instanceof Number)
                value = GUIUtils.getFullDateTimeFormat().format(new Date(((Number) value).longValue()));
        }
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
}
