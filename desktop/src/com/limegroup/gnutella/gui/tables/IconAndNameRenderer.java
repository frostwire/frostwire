package com.limegroup.gnutella.gui.tables;

import com.frostwire.gui.theme.ThemeMediator;

import javax.swing.*;
import java.awt.*;

/**
 * Renders an icon along with a label.
 */
public final class IconAndNameRenderer extends DefaultTableBevelledCellRenderer {
    /**
     * Constructs a new IconAndNameRenderer with the Icon aligned to the left
     * of the text, with a text gap of 5 between the icon and text.
     */
    public IconAndNameRenderer() {
        super();
        setHorizontalAlignment(LEFT);
        setIconTextGap(5);
        setHorizontalTextPosition(RIGHT);
    }

    /**
     * Returns the <tt>Component</tt> that displays the icons & names
     * based on the <tt>IconAndNameHolder</tt> object.
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        IconAndNameHolder in = (IconAndNameHolder) value;
        Icon icon = null;
        String name = null;
        if (in != null) {
            icon = in.getIcon();
            name = in.getName();
            if (name != null) {
                String strValue = name;
                strValue = strValue.replace("<html>", "<html><div width=\"1000000px\">");
                strValue = strValue.replace("</html>", "</div></html>");
                name = strValue;
            }
        }
        setIcon(icon);
        Component comp = super.getTableCellRendererComponent(table, name, isSelected, hasFocus, row, column);
        ThemeMediator.fixLabelFont((JLabel) comp);
        return comp;
    }
}